package fr.neamar.kiss.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import fr.neamar.kiss.BuildConfig;
import fr.neamar.kiss.DataHandler;
import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.utils.PackageManagerUtils;

public class BadgeCountHandler extends BroadcastReceiver {
    private static final String TAG = BadgeCountHandler.class.getSimpleName();
    public static final String BADGECOUNT = "BADGE_COUNT";
    public static final String PACKAGENAME = "PACKAGE_NAME";
    @Override
    public void onReceive(Context context, Intent intent) {
        DataHandler dataHandler = KissApplication.getApplication(context).getDataHandler();
        String packageName = null;
        int badgeCount = 0;
        if (BuildConfig.DEBUG) Log.i(TAG,"onReceive");
        switch (intent.getAction()) {
            case "com.htc.launcher.action.UPDATE_SHORTCUT":
                badgeCount = intent.getIntExtra("count", 0);
                packageName = intent.getStringExtra("packagename");
                break;
            case "android.intent.action.BADGE_COUNT_UPDATE":
                badgeCount = intent.getIntExtra("badge_count", 0);
                packageName = intent.getStringExtra("badge_count_package_name");
                break;
            case "com.sonyericsson.home.action.UPDATE_BADGE":
                boolean showMessage = intent.getBooleanExtra("com.sonyericsson.home.intent.extra.badge.SHOW_MESSAGE", true);
                if (showMessage) {
                    String message = intent.getStringExtra("com.sonyericsson.home.intent.extra.badge.MESSAGE");
                    try {
                        badgeCount = Integer.parseInt(message);
                    } catch (Exception ex) {
                        badgeCount = 0;
                    }
                }
                packageName = intent.getStringExtra("com.sonyericsson.home.intent.extra.badge.PACKAGE_NAME");
                break;
        }

        if (packageName != null) {
            if (BuildConfig.DEBUG) Log.i(TAG,"onReceive, package:"+packageName+ " count:"+badgeCount);
            if (dataHandler.getBadgeHandler().getBadgeCount(packageName)!=badgeCount) {
                dataHandler.getBadgeHandler().setBadgeCount(packageName, badgeCount);
            }
        }

    }

    public static void setEnabled(Context context, boolean enabled) {
        PackageManagerUtils.enableComponent(context, BadgeCountHandler.class, enabled);
    }
}