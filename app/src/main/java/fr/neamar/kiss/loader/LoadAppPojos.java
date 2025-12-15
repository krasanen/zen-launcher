package fr.neamar.kiss.loader;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import fr.neamar.kiss.BadgeHandler;
import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.TagsHandler;
import fr.neamar.kiss.cache.MemoryCacheHelper;
import fr.neamar.kiss.db.AppRecord;
import fr.neamar.kiss.db.DBHelper;
import fr.neamar.kiss.pojo.AppPojo;
import fr.neamar.kiss.utils.UserHandle;

public class LoadAppPojos extends LoadPojos<AppPojo> {

    private final TagsHandler tagsHandler;
    private static final String TAG = LoadAppPojos.class.getSimpleName();
    public LoadAppPojos(Context context) {
        super(context, "app://");
        tagsHandler = KissApplication.getApplication(context).getDataHandler().getTagsHandler();
    }

    public AppPojo loadApp(String packageName, UserHandle user, String className, Context ctx){

        Set<String> excludedAppList = KissApplication.getApplication(ctx).getDataHandler().getExcluded();
        Set<String> excludedFromHistoryAppList = KissApplication.getApplication(ctx).getDataHandler().getExcludedFromHistory();
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            UserManager manager = (UserManager) ctx.getSystemService(Context.USER_SERVICE);
            LauncherApps launcher = (LauncherApps) ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            for (android.os.UserHandle profile : manager.getUserProfiles()) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(packageName, className));
                LauncherActivityInfo activityInfo = launcher.resolveActivity(intent, profile);
                return appAppLollipop(activityInfo, user, excludedAppList, excludedFromHistoryAppList, excludedAppList);
            }
        } else {
            PackageManager manager = ctx.getPackageManager();
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(packageName, className));
            ResolveInfo resolveInfo = manager.resolveActivity(intent, 0);
            appAppPreLollipop(resolveInfo, manager, excludedAppList,excludedFromHistoryAppList);
        }
        return null;

    }

    @Override
    protected ArrayList<AppPojo> doInBackground(Void... params) {
        long start = System.nanoTime();

        ArrayList<AppPojo> apps = new ArrayList<>();

        Context ctx = context.get();
        if (ctx == null) {
            return apps;
        }

        Set<String> excludedAppList = KissApplication.getApplication(ctx).getDataHandler().getExcluded();
        Set<String> excludedAppListFavorites = KissApplication.getApplication(ctx).getDataHandler().getExcludedFavorites();
        Set<String> excludedFromHistoryAppList = KissApplication.getApplication(ctx).getDataHandler().getExcludedFromHistory();

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            UserManager manager = (UserManager) ctx.getSystemService(Context.USER_SERVICE);
            LauncherApps launcher = (LauncherApps) ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);

            // Handle multi-profile support introduced in Android 5 (#542)
            for (android.os.UserHandle profile : manager.getUserProfiles()) {
                UserHandle user = new UserHandle(manager.getSerialNumberForUser(profile), profile);
                List<LauncherActivityInfo> activityList = launcher.getActivityList(null, profile);
                for (LauncherActivityInfo activityInfo : activityList) {
                    ApplicationInfo appInfo = activityInfo.getApplicationInfo();

                    String id = user.addUserSuffixToString(pojoScheme + appInfo.packageName + "/" + activityInfo.getName(), '/');

                    boolean isExcluded = excludedAppList.contains(AppPojo.getComponentName(appInfo.packageName, activityInfo.getName(), user));
                    isExcluded |= excludedAppListFavorites.contains(id);
                    boolean isExcludedFromHistory = excludedFromHistoryAppList.contains(id);

                    AppPojo app = new AppPojo(id, appInfo.packageName, activityInfo.getName(), user,
                            isExcluded, isExcludedFromHistory);

                    // Use try-catch to handle resource loading failures gracefully
                    // This prevents ANR from ResourcesManager lock contention
                    String appLabel;
                    try {
                        CharSequence labelCs = activityInfo.getLabel();
                        appLabel = labelCs != null ? labelCs.toString() : activityInfo.getName();
                    } catch (Exception e) {
                        // Fallback to activity name if label loading fails
                        appLabel = activityInfo.getName();
                    }
                    app.setName(appLabel);

                    app.setTags(tagsHandler.getTags(app.id));

                    apps.add(app);

                    MemoryCacheHelper.cacheAppIconDrawable(ctx, new ComponentName(app.packageName, app.activityName), app.userHandle);
                }
            }
        } else {
            PackageManager manager = ctx.getPackageManager();

            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            for (ResolveInfo info : manager.queryIntentActivities(mainIntent, 0)) {
                ApplicationInfo appInfo = info.activityInfo.applicationInfo;
                String id = pojoScheme + appInfo.packageName + "/" + info.activityInfo.name;
                boolean isExcluded = excludedAppList.contains(
                        AppPojo.getComponentName(appInfo.packageName, info.activityInfo.name, new UserHandle())
                );
                isExcluded |= excludedAppListFavorites.contains(id);
                boolean isExcludedFromHistory = excludedFromHistoryAppList.contains(id);

                AppPojo app = new AppPojo(id, appInfo.packageName, info.activityInfo.name, new UserHandle(),
                        isExcluded, isExcludedFromHistory);

                // Use try-catch to handle resource loading failures gracefully
                String appLabel;
                try {
                    CharSequence labelCs = info.loadLabel(manager);
                    appLabel = labelCs != null ? labelCs.toString() : info.activityInfo.name;
                } catch (Exception e) {
                    appLabel = info.activityInfo.name;
                }
                app.setName(appLabel);

                app.setTags(tagsHandler.getTags(app.id));

                apps.add(app);

                MemoryCacheHelper.cacheAppIconDrawable(ctx, new ComponentName(app.packageName, app.activityName), app.userHandle);
            }
        }

        HashMap<String, AppRecord> customApps = DBHelper.getCustomAppData(ctx);
        for (AppPojo app : apps) {
            AppRecord customApp = customApps.get(app.getComponentName());
            if (customApp == null)
                continue;
            if (customApp.hasCustomName())
                app.setName(customApp.name);
            if (customApp.hasCustomIcon())
                app.setCustomIconId(customApp.dbId);
        }

        long end = System.nanoTime();
        Log.i("time", (end - start) / 1000000 + " milliseconds to list apps");

        return apps;
    }

    private AppPojo appAppPreLollipop(ResolveInfo info, PackageManager manager, Set<String> excludedAppList, Set<String> excludedFromHistoryAppList) {
        ApplicationInfo appInfo = info.activityInfo.applicationInfo;
        String id = pojoScheme + appInfo.packageName + "/" + info.activityInfo.name;
        boolean isExcluded = excludedAppList.contains(
                AppPojo.getComponentName(appInfo.packageName, info.activityInfo.name, new UserHandle())
        );
        boolean isExcludedFromHistory = excludedFromHistoryAppList.contains(id);

        AppPojo app = new AppPojo(id, appInfo.packageName, info.activityInfo.name, new UserHandle(),
                isExcluded, isExcludedFromHistory);

        // Use try-catch to handle resource loading failures gracefully
        String appLabel;
        try {
            CharSequence labelCs = info.loadLabel(manager);
            appLabel = labelCs != null ? labelCs.toString() : info.activityInfo.name;
        } catch (Exception e) {
            appLabel = info.activityInfo.name;
        }
        app.setName(appLabel);

        app.setTags(tagsHandler.getTags(app.id));

        return app;
    }

    private AppPojo appAppLollipop(LauncherActivityInfo activityInfo, UserHandle user, Set<String> appList, Set<String> excludedAppList, Set<String> excludedFromHistoryAppList) {
        ApplicationInfo appInfo = activityInfo.getApplicationInfo();

        String id = user.addUserSuffixToString(pojoScheme + appInfo.packageName + "/" + activityInfo.getName(), '/');

        boolean isExcluded = excludedAppList.contains(AppPojo.getComponentName(appInfo.packageName, activityInfo.getName(), user));
        boolean isExcludedFromHistory = excludedFromHistoryAppList.contains(id);

        AppPojo app = new AppPojo(id, appInfo.packageName, activityInfo.getName(), user,
                isExcluded, isExcludedFromHistory);

        // Use try-catch to handle resource loading failures gracefully
        String appLabelLollipop;
        try {
            CharSequence labelCs = activityInfo.getLabel();
            appLabelLollipop = labelCs != null ? labelCs.toString() : activityInfo.getName();
        } catch (Exception e) {
            appLabelLollipop = activityInfo.getName();
        }
        app.setName(appLabelLollipop);

        app.setTags(tagsHandler.getTags(app.id));
        return app;
    }
}