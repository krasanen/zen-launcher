package fr.neamar.kiss.forwarder;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Build;
import android.util.Log;
import android.view.*;

import fi.zmengames.zlauncher.ParcelableUtil;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.R;
import fr.neamar.kiss.ui.WidgetLayout;
import fr.neamar.kiss.ui.WidgetMenu;
import fr.neamar.kiss.ui.WidgetPreferences;

import java.util.Map;

import static fr.neamar.kiss.MainActivity.REQUEST_BIND_APPWIDGET;

public class Widget extends Forwarder implements WidgetMenu.OnClickListener {
    public static final int REQUEST_REFRESH_APPWIDGET = 10;
    private static final int REQUEST_PICK_APPWIDGET = 9;
    private static final int REQUEST_CREATE_APPWIDGET = 5;
    private static final String TAG = Widget.class.getSimpleName();
    private static final int APPWIDGET_HOST_ID = 442;
    public static final String WIDGET_PREFERENCE_ID = "widgetprefs";

    private SharedPreferences widgetPrefs;

    /**
     * Widget fields
     */
    private AppWidgetManager mAppWidgetManager;
    private AppWidgetHost mAppWidgetHost;

    /**
     * View widgets are added to
     */
    private WidgetLayout widgetArea;

    Widget(MainActivity mainActivity) {
        super(mainActivity);
    }

    void onCreate() {
        // Initialize widget manager and host, restore widgets
        widgetPrefs = mainActivity.getSharedPreferences(WIDGET_PREFERENCE_ID, Context.MODE_PRIVATE);

        mAppWidgetManager = AppWidgetManager.getInstance(mainActivity);
        mAppWidgetHost = new AppWidgetHost(mainActivity, APPWIDGET_HOST_ID);
        widgetArea = mainActivity.findViewById(R.id.widgetLayout);

        //set the size of the Widget Area
        Point size = new Point();
        mainActivity.getWindowManager().getDefaultDisplay().getSize(size);
        ViewGroup.LayoutParams params = widgetArea.getLayoutParams();
        //TODO: Fix this! We assume the widget area size is 3x screen size
        params.width = size.x * 3;
        widgetArea.setLayoutParams(params);
        widgetArea.scrollWidgets(.5f);

        restoreWidgets();
    }

    void onStart() {
        // Start listening for widget update
        mAppWidgetHost.startListening();
    }

    void onStop() {
        // Stop listening for widget update
        mAppWidgetHost.stopListening();
    }

    void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CREATE_APPWIDGET:
                    addAppWidget(data);
                    break;
                case REQUEST_PICK_APPWIDGET:
                    configureAppWidget(data);
                    break;
                case REQUEST_REFRESH_APPWIDGET:
                    refreshAppWidget(data);
                    break;
            }
        } else if (resultCode == Activity.RESULT_CANCELED && data != null) {
            //if widget was not selected, delete id
            int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (appWidgetId != -1) {
                mAppWidgetHost.deleteAppWidgetId(appWidgetId);
            }
        }
    }

    boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() != R.id.widget) {
            return false;
        }

        if (getWidgetHostViewCount() == 0) {
            if (canAddWidget()) {
                // request widget picker, a selection will lead to a call of onActivityResult
                int appWidgetId = mAppWidgetHost.allocateAppWidgetId();
                Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
                pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                mainActivity.startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET);
            } else {
                // if we already have a widget we remove it
                removeAllWidgets();
            }
        } else {
            WidgetMenu menu = new WidgetMenu(this);
            if (canAddWidget())
                menu.add(mainActivity, R.string.menu_widget_add);
            for (int i = 0; i < getWidgetHostViewCount(); i += 1) {
                AppWidgetHostView hostView = getWidgetHostView(i);
                if (hostView == null)
                    continue;
                AppWidgetProviderInfo info = hostView.getAppWidgetInfo();
                String label;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    label = info.label;
                } else {
                    label = info.loadLabel(mainActivity.getPackageManager());
                }
                menu.add(hostView.getAppWidgetId(), label);
            }
            mainActivity.registerPopup(menu.show(widgetArea));
        }
        return true;
  }

    void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (prefs.getBoolean("history-hide", true)) {
            if (getWidgetHostViewCount() == 0) {
                if (canAddWidget())
                    menu.findItem(R.id.widget).setTitle(R.string.menu_widget_add);
                else
                    menu.findItem(R.id.widget).setTitle(R.string.menu_widget_remove);
            }
        } else {
            menu.findItem(R.id.widget).setVisible(false);
        }
    }

    void onDataSetChanged() {
        if ((getWidgetHostViewCount() > 0) && mainActivity.adapter.isEmpty()) {
            // when a widget is displayed the empty list would prevent touches on the widget
            mainActivity.emptyListView.setVisibility(View.GONE);
        }
    }

    /**
     * Restores all previously added widgets
     */
    private void restoreWidgets() {
        Log.d("Widget", "restoreWidgets");
        Map<String, ?> widgetIds = widgetPrefs.getAll();
        for (String appWidgetId : widgetIds.keySet()) {
            Log.d("Widget", "appWidgetId"+appWidgetId);
            addWidgetToLauncher(Integer.parseInt(appWidgetId));
        }
    }

    /**
     * Adds a widget to the widget area on the MainActivity
     *
     * @param appWidgetId id of widget to add
     */
    private WidgetPreferences addWidgetToLauncher(int appWidgetId) {
        Log.d("Widget", "addWidgetToLauncher"+appWidgetId);
        // only add widgets if in minimal mode (may need launcher restart when turned on)
        if (prefs.getBoolean("history-hide", true)) {
            // remove empty list view when using widgets, this would block touches on the widget
            mainActivity.emptyListView.setVisibility(View.GONE);
            //add widget to view
            AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
            if (appWidgetInfo == null) {
                Log.d(TAG, "appWidgetInfo null, recreate widget");
                String data = widgetPrefs.getString(String.valueOf(appWidgetId), null);
                WidgetPreferences wp = WidgetPreferences.unserialize(data);


                AppWidgetProviderInfo a = ParcelableUtil.unmarshall(wp.appWidgetProviderInfo,  AppWidgetProviderInfo.CREATOR);
                mAppWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, a.provider);
                AppWidgetHostView hostView = mAppWidgetHost.createView(mainActivity, appWidgetId, a);
                hostView.setAppWidget(appWidgetId, a);
                addWidgetHostView(hostView);
                SharedPreferences.Editor widgetPrefsEditor = widgetPrefs.edit();
                widgetPrefsEditor.remove(String.valueOf(appWidgetId));
                removeAppWidget(appWidgetId);
                widgetPrefsEditor.putString(String.valueOf(appWidgetId), WidgetPreferences.serialize(wp));
                widgetPrefsEditor.apply();
                mainActivity.refreshWidget(appWidgetId);
                return wp;
            }
            AppWidgetHostView hostView = mAppWidgetHost.createView(mainActivity, appWidgetId, appWidgetInfo);
            hostView.setMinimumHeight(appWidgetInfo.minHeight);
            hostView.setAppWidget(appWidgetId, appWidgetInfo);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                hostView.updateAppWidgetSize(null, appWidgetInfo.minWidth, appWidgetInfo.minHeight, appWidgetInfo.minWidth, appWidgetInfo.minHeight);
            }
            addWidgetHostView(hostView);

            WidgetPreferences wp = new WidgetPreferences();
            wp.load( hostView, ParcelableUtil.marshall(appWidgetInfo) );
            return wp;
        }
        return null;
    }

    private void addWidgetHostView(final AppWidgetHostView hostView) {
        String data = widgetPrefs.getString(String.valueOf(hostView.getAppWidgetId()), null);
        WidgetPreferences wp = WidgetPreferences.unserialize(data);

        int w = ViewGroup.LayoutParams.WRAP_CONTENT;
        int h = ViewGroup.LayoutParams.WRAP_CONTENT;
        if (wp != null) {
            w = wp.width;
            h = wp.height;
        }

        WidgetLayout.LayoutParams layoutParams = new WidgetLayout.LayoutParams(w, h);
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        if (wp != null)
            wp.apply(layoutParams, wp.appWidgetProviderInfo);

        //hostView.setBackgroundColor(0x3F7f0000);
        hostView.setLayoutParams(layoutParams);

        widgetArea.post(new Runnable() {
            @Override
            public void run() {
                widgetArea.addView(hostView);
            }
        });
    }

    private void removeWidgetHostView(AppWidgetHostView hostView) {
        int childCount = widgetArea.getChildCount();
        for (int i = 0; i < childCount; i += 1) {
            if (widgetArea.getChildAt(i) == hostView) {
                widgetArea.removeViewAt(i);
                return;
            }
        }
    }

    private AppWidgetHostView getWidgetHostView(int index) {
        return (AppWidgetHostView) widgetArea.getChildAt(index);
    }

    private AppWidgetHostView getWidgetHostView(View view) {
        return (AppWidgetHostView) view;
    }

    private int getWidgetHostViewCount() {
        return widgetArea.getChildCount();
    }

    /**
     * Removes all widgets from the launcher
     */
    private void removeAllWidgets() {
        while (getWidgetHostViewCount() > 0) {
            AppWidgetHostView widget = getWidgetHostView(0);
            removeAppWidget(widget);
        }
    }

    /**
     * Removes a single widget and deletes it from persistent prefs
     *
     * @param hostView instance of a displayed widget
     */
    private void removeAppWidget(AppWidgetHostView hostView) {
        // remove widget from view
        int appWidgetId = hostView.getAppWidgetId();
        removeAppWidget(appWidgetId);
        removeWidgetHostView(hostView);
    }

    /**
     * Removes a single widget and deletes it from persistent prefs
     *
     * @param appWidgetId id of widget that should get removed
     */
    private void removeAppWidget(int appWidgetId) {
        // remove widget from view
        mAppWidgetHost.deleteAppWidgetId(appWidgetId);
        // remove widget id from persistent prefs
        SharedPreferences.Editor widgetPrefsEditor = widgetPrefs.edit();
        widgetPrefsEditor.remove(String.valueOf(appWidgetId));
        widgetPrefsEditor.apply();
    }

    private boolean canAddWidget() {
        return true;
    }

    /**
     * Adds widget to Activity and persists it in prefs to be able to restore it
     *
     * @param data Intent holding widget id to add
     */
    private void addAppWidget(Intent data) {
        int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        //add widget
        WidgetPreferences wp = addWidgetToLauncher(appWidgetId);

        // Save widget in preferences
        SharedPreferences.Editor widgetPrefsEditor = widgetPrefs.edit();
        widgetPrefsEditor.putString(String.valueOf(appWidgetId), WidgetPreferences.serialize(wp));
        widgetPrefsEditor.apply();
    }

    /**
     * Check if widget needs configuration and display configuration view if necessary,
     * otherwise just add the widget
     *
     * @param data Intent holding widget id to configure
     */
    private void configureAppWidget(Intent data) {
        int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);

        AppWidgetProviderInfo appWidget =
                mAppWidgetManager.getAppWidgetInfo(appWidgetId);

        if (appWidget.configure != null) {
            // Launch over to configure widget, if needed.
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(appWidget.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            mainActivity.startActivityForResult(intent, REQUEST_CREATE_APPWIDGET);
        } else {
            // Otherwise, finish adding the widget.
            addAppWidget(data);
        }
    }

    private void refreshAppWidget(Intent intent) {
        int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        String data = widgetPrefs.getString(String.valueOf(appWidgetId), null);
        WidgetPreferences wp = WidgetPreferences.unserialize(data);
        if (wp == null)
            return;
        for (int i = 0; i < getWidgetHostViewCount(); i += 1) {
            AppWidgetHostView hostView = getWidgetHostView(i);
            if (hostView.getAppWidgetId() == appWidgetId) {
                WidgetLayout.LayoutParams layoutParams = (WidgetLayout.LayoutParams) hostView.getLayoutParams();
                wp.apply(layoutParams, wp.appWidgetProviderInfo);
                hostView.setLayoutParams(layoutParams);
                break;
            }
        }
    }

    public void onWallpaperScroll(float fCurrent) {
        widgetArea.scrollWidgets(fCurrent);
    }

    @Override
    public void onWidgetAdd() {
        // request widget picker, a selection will lead to a call of onActivityResult
        int appWidgetId = mAppWidgetHost.allocateAppWidgetId();
        Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        mainActivity.startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET);
    }

    @Override
    public void onWidgetEdit(int appWidgetId) {
        for (int i = 0; i < getWidgetHostViewCount(); i += 1) {
            AppWidgetHostView hostView = getWidgetHostView(i);
            if (hostView.getAppWidgetId() == appWidgetId) {
                String data = widgetPrefs.getString(String.valueOf(appWidgetId), null);
                WidgetPreferences wp = WidgetPreferences.unserialize(data);
                if (wp == null)
                    wp = new WidgetPreferences();
                wp.showEditMenu(mainActivity, widgetPrefs, hostView);
                break;
            }
        }
    }

    @Override
    public void onWidgetRemove(int appWidgetId) {
        for (int i = 0; i < getWidgetHostViewCount(); i += 1) {
            AppWidgetHostView hostView = getWidgetHostView(i);
            if (hostView.getAppWidgetId() == appWidgetId) {
                removeAppWidget(hostView);
                break;
            }
        }
    }
}
