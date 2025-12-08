package fi.zmengames.zen;

import androidx.annotation.NonNull;

public class ZEvent {
    private final @NonNull  State       state;
    private final String text;
    private final int intExtra;

    public ZEvent(@NonNull State state, String stringExtra, int intExtra) {
        this.state                = state;
        this.text = stringExtra;
        this.intExtra = intExtra;
    }

    public enum State {
        // Normal states
        GOOGLE_SIGNIN,
        GOOGLE_SIGNOUT,
        LOAD_OVER,
        FULL_LOAD_OVER,
        SHOW_TOAST,
        BADGE_COUNT,
        NOTIFICATION_COUNT,
        HANDLE_PENDING_EVENTS,
        RELOAD_APPS,
        WIFI_ON,
        WIFI_OFF,
        FLASHLIGHT_ON,
        FLASHLIGHT_OFF,
        UPDATE_WALLPAPER,
        ALARM_IN_ACTION,
        ALARM_AT,
        ALARM_PICKER,
        DEV_ADMIN_LOCK_AFTER,
        DEV_ADMIN_LOCK_PROXIMITY,
        DATE_TIME_PICKER,
        REFRESH_UI,
        ACTION_SET_DEFAULT_LAUNCHER,
        BARCODE_READER,
        REQUEST_REMOVE_DEVICE_ADMIN_AND_UNINSTALL,
        SHOW_HISTORYBUTTON,
        HIDE_HISTORYBUTTON,
        NIGHTMODE_ON,
        NIGHTMODE_OFF,
        LAUNCH_INTENT,
        SET_BADGE_COUNT,
        ENABLE_PROXIMITY,
        DISABLE_PROXIMITY,
        GOOGLE_SIGN_IN,
        GOOGLE_SIGN_OUT,
        SCREEN_ON,
        SCREEN_OFF,
        SCREEN_OFF_GESTURE,
        ALARM_ENTERED_TEXT,
        ALARM_DATE_PICKER_MILLIS
    }

    public ZEvent(@NonNull State state)
    {
        this.state                = state;
        text = null;
        this.intExtra = 0;
    }
    public ZEvent(@NonNull State state, String text)
    {
        this.state                = state;
        this.text = text;
        this.intExtra = 0;
    }
    public @NonNull State getState() {
        return state;
    }
    public String getText() {
        return text;
    }
    public int getIntExtra() {return intExtra;}
}
