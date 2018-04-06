package fi.zmengames.zlauncher.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.PowerManager;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.util.Log;

public class ProximitySwitch extends SwitchPreference {
    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;

    public ProximitySwitch(Context context) {
        this(context, null);
    }

    public ProximitySwitch(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.switchPreferenceStyle);
        mPowerManager = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
    }

    public ProximitySwitch(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onClick() {
        if (!isChecked()) {
            turnOffScreen();
        } else {
            turnOnScreen();
        }
        super.onClick();
    }

    public void turnOnScreen(){
        // turn on screen
        Log.v("ProximityActivity", "ON!");
        if (mWakeLock!=null) {
            mWakeLock.release();
        }
    }


    public void turnOffScreen(){
        // turn off screen
        Log.v("ProximityActivity", "OFF!");
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "tag");
        mWakeLock.acquire();
    }
}