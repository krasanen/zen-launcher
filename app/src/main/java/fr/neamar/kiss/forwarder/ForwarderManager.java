package fr.neamar.kiss.forwarder;

import android.content.Context;
import android.content.Intent;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.R;
import fr.neamar.kiss.UIColors;

public class ForwarderManager extends Forwarder {
    private final Widget widgetForwarder;
    private final LiveWallpaper liveWallpaperForwarder;
    private final InterfaceTweaks interfaceTweaks;
    private final ExperienceTweaks experienceTweaks;
    private final Favorites favoritesForwarder;
    private final Permission permissionForwarder;
    private final OreoShortcuts shortcutsForwarder;
    private final TagsMenu tagsMenu;
    private final Notification notificationForwarder;


    public ForwarderManager(MainActivity mainActivity) {
        super(mainActivity);

        this.widgetForwarder = new Widget(mainActivity);
        this.interfaceTweaks = new InterfaceTweaks(mainActivity);
        this.liveWallpaperForwarder = new LiveWallpaper(mainActivity);
        this.experienceTweaks = new ExperienceTweaks(mainActivity);
        this.favoritesForwarder = new Favorites(mainActivity);
        this.permissionForwarder = new Permission(mainActivity);
        this.shortcutsForwarder = new OreoShortcuts(mainActivity);
        this.notificationForwarder = new Notification(mainActivity);
        this.tagsMenu = new TagsMenu(mainActivity);
        // Setting the theme needs to be done before setContentView()
        String theme = prefs.getString("theme", "transparent");
        switch (theme) {
            case "dark":
                mainActivity.setTheme(R.style.AppThemeDark);
                break;
            case "transparent":
                mainActivity.setTheme(R.style.AppThemeTransparent);
                break;
            case "semi-transparent":
                mainActivity.setTheme(R.style.AppThemeSemiTransparent);
                break;
            case "semi-transparent-dark":
                mainActivity.setTheme(R.style.AppThemeSemiTransparentDark);
                break;
            case "transparent-dark":
                mainActivity.setTheme(R.style.AppThemeTransparentDark);
                break;
            case "amoled-dark":
                mainActivity.setTheme(R.style.AppThemeAmoledDark);
                break;
        }

        UIColors.applyOverlay(mainActivity, prefs);

        mainActivity.getTheme().applyStyle(prefs.getBoolean("small-results", false) ? R.style.OverlayResultSizeSmall : R.style.OverlayResultSizeStandard, true);
    }

    public void onCreate() {
        favoritesForwarder.onCreate();
        widgetForwarder.onCreate();
        interfaceTweaks.onCreate();
        experienceTweaks.onCreate();
        shortcutsForwarder.onCreate();
        tagsMenu.onCreate();
    }

    public void onResume() {
        interfaceTweaks.onResume();
        experienceTweaks.onResume();
        notificationForwarder.onResume();
        tagsMenu.onResume();
    }

    public void onPause() {
        notificationForwarder.onPause();
    }

    public void onStart() {
        widgetForwarder.onStart();
    }

    public void onStop() {
        widgetForwarder.onStop();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        widgetForwarder.onActivityResult(requestCode, resultCode, data);
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionForwarder.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        return widgetForwarder.onOptionsItemSelected(item);
    }

    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        widgetForwarder.onCreateContextMenu(menu);
    }

    public boolean onTouch(View view, MotionEvent event) {
        if (experienceTweaks.onTouch(view, event))
            return true;
        else
            return liveWallpaperForwarder.onTouch(view, event);
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        experienceTweaks.onWindowFocusChanged(hasFocus);
    }

    public void onDataSetChanged() {
        widgetForwarder.onDataSetChanged();
        favoritesForwarder.onDataSetChanged();
    }

    public void updateSearchRecords(String query) {
        favoritesForwarder.updateSearchRecords(query);
        experienceTweaks.updateSearchRecords(query);
    }

    public void onFavoriteChange() {
        favoritesForwarder.onFavoriteChange();
    }

    public void onDisplayKissBar(Boolean display) {
        experienceTweaks.onDisplayKissBar(display);
    }

    public boolean onMenuButtonClicked(View menuButton) {
        if (tagsMenu.isTagMenuEnabled()) {
            mainActivity.registerPopup(tagsMenu.showMenu(menuButton));
            return true;
        }
        return false;
    }

    public void onWallpaperScroll(float fCurrent) {
        widgetForwarder.onWallpaperScroll(fCurrent);
    }


    public void hideKeyboard() {
        experienceTweaks.hideKeyboard();
    }

    public void switchInputType() {
        experienceTweaks.switchInputType();
    }

    public void removeWidgets() {
        widgetForwarder.removeAllWidgets();
    }

    public void restoreWidgets() {
        widgetForwarder.restoreWidgets();
    }

    public void addWidget() {
        widgetForwarder.onWidgetAdd();
    }

    public void updateWidgets(Context context) {
        widgetForwarder.updateWidgets(context);
    }

    public void showWidgetSettings() {
        widgetForwarder.onShowWidgetSettings();
    }

    /**
     * Handle back press, returns true if consumed
     */
    public boolean onBackPressed() {
        if (widgetForwarder.isInResizeMode()) {
            widgetForwarder.exitResizeMode();
            return true;
        }
        return false;
    }
}
