package fi.zmengames.zen;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fr.neamar.kiss.BuildConfig;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.R;
import fr.neamar.kiss.broadcast.BadgeCountHandler;

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_TOAST;
import static fi.zmengames.zen.AlarmActivity.ALARM_EXTRA;
import static fi.zmengames.zen.AlarmActivity.ALARM_ID;
import static fi.zmengames.zen.AlarmActivity.ALARM_TIME;
import static fi.zmengames.zen.ZEvent.State.ALARM_DATE_PICKER_MILLIS;
import static fi.zmengames.zen.ZEvent.State.ALARM_ENTERED_TEXT;
import static fi.zmengames.zen.ZEvent.State.ALARM_IN_ACTION;
import static fi.zmengames.zen.ZEvent.State.ALARM_PICKER;
import static fi.zmengames.zen.ZEvent.State.DEV_ADMIN_LOCK_AFTER;
import static fi.zmengames.zen.ZEvent.State.DISABLE_PROXIMITY;
import static fi.zmengames.zen.ZEvent.State.ENABLE_PROXIMITY;
import static fi.zmengames.zen.ZEvent.State.GOOGLE_SIGN_IN;
import static fi.zmengames.zen.ZEvent.State.GOOGLE_SIGN_OUT;
import static fi.zmengames.zen.ZEvent.State.LAUNCH_INTENT;
import static fi.zmengames.zen.ZEvent.State.NIGHTMODE_OFF;
import static fi.zmengames.zen.ZEvent.State.NIGHTMODE_ON;
import static fi.zmengames.zen.ZEvent.State.SCREEN_OFF;
import static fi.zmengames.zen.ZEvent.State.SCREEN_OFF_GESTURE;
import static fi.zmengames.zen.ZEvent.State.SCREEN_ON;
import static fi.zmengames.zen.ZEvent.State.SHOW_TOAST;

// CHANGED: Extends AccessibilityService instead of Service
public class LauncherService extends AccessibilityService {
    private static final String TAG = LauncherService.class.getSimpleName();

    private final ExecutorService serviceExecutor = Executors.newCachedThreadPool();


    // System Services
    private WindowManager mWindowManager;
    private NotificationManager mNotificationManager;
    private AccessibilityManager mAccessibilityManager;

    // Floating Window
    private View mLayout;
    private WindowManager.LayoutParams mLayoutParams;

    // Options
    private final int mBrightness = 100;
    private final int mAdvancedMode = Constants.AdvancedMode.NONE;
    private final int mYellowFilterAlpha = 100;

    // Constants
    private static final int ANIMATE_DURATION_MILES = 250;
    private static final int NOTIFICATION_NO = 1024;
    private static SensorManager mSensorManager;
    private static Sensor mProximity;
    private static SensorEventListener sensorEventListener;
    public static boolean isProximityLockEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("proximity-switch-lock", false);
    }

    public void stopListeningProximitySensor(){
        if (mSensorManager!=null){
            handler.removeCallbacks(lockProximityRunnable);
            if (sensorEventListener!=null) {
                mSensorManager.unregisterListener(sensorEventListener);
            }
            lastValue = -1f;
        }
    }
    public static boolean running = true;
    public static float lastValue=-1f;
    final Runnable lockAfterRunnable = new Runnable() {
        public void run() {
            running = false;
            if (sensorEventListener!=null) {
                mSensorManager.unregisterListener(sensorEventListener);
            }
            lockScreen();
        }
    };

    final Runnable lockProximityRunnable = new Runnable() {
        public void run() {
            running = false;
            if (sensorEventListener!=null) {
                mSensorManager.unregisterListener(sensorEventListener);
            }
            lockScreenProximity();
        }
    };
    Handler handler = new Handler(Looper.getMainLooper());

    private void startListeningProximitySensor() {
        if (BuildConfig.DEBUG) Log.d(TAG, "startListeningProximitySensor");
        if (lastValue == -1f) {
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            sensorEventListener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent sensorEvent) {
                    float thisValue = sensorEvent.values[0];
                    if (thisValue != lastValue && sensorEvent.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "sensorEvent.values[0]:" + thisValue);
                        lastValue = thisValue;
                        //far
                        //near
                        lockScreenStartTimer(sensorEvent.values[0] < sensorEvent.sensor.getMaximumRange());
                    }
                }

                private void lockScreenStartTimer(boolean run) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "lockScreenStartTimer: " + run);
                    if (run) {
                        if (!running)
                            handler.postDelayed(lockProximityRunnable, 2000);
                    } else {
                        handler.removeCallbacks(lockProximityRunnable);
                        running = false;
                    }
                }


                @Override
                public void onAccuracyChanged(Sensor sensor, int i) {

                }
            };

            mSensorManager.registerListener(sensorEventListener, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            if (BuildConfig.DEBUG) Log.d(TAG, "startListeningProximitySensor (was already running)");
        }

    }

    private void turnOffScreen(){
        if (BuildConfig.DEBUG) Log.d(TAG, "turnOffScreen");
    }

    private void turnOnScreen() {
        if (BuildConfig.DEBUG) Log.d(TAG, "turnOnScreen");
    }

    private void turnOffScreenGesture(){
        if (BuildConfig.DEBUG) Log.d(TAG, "turnOffScreenGesture");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            boolean success = performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
            if (!success) {
                if (BuildConfig.DEBUG) Log.w(TAG, "performGlobalAction failed. Accessibility Service Enabled: " + isAccessibilityServiceEnabled());
                
                if (!isAccessibilityServiceEnabled()) {
                    handleShowToast("Please enable Zen Launcher in Accessibility Settings to use this feature.");
                    Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Could not open accessibility settings", e);
                    }
                }
                
                // If accessibility fails or isn't enabled, fallback to Admin method (will require PIN)
                lockScreen();
            }
        } else {
            lockScreen();
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        
        // FEEDBACK_ALL_MASK covers all types
        java.util.List<android.accessibilityservice.AccessibilityServiceInfo> enabledServices = 
            am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            
        for (android.accessibilityservice.AccessibilityServiceInfo service : enabledServices) {
            if (service.getId() != null && service.getId().contains(getPackageName())) {
                return true;
            }
        }
        return false;
    }

    public void lockScreen() {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        if (isDeviceAdminActive()) {
            devicePolicyManager.lockNow();
        } else {
            handleShowToast(getResources().getString(R.string.cannot_lock));
            try {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(this, ZenAdmin.class));
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.deviceadmin_switch_text));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Could not request device admin", e);
            }
        }
    }

    public void lockScreenProximity() {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        if (isDeviceAdminActive()) {
            devicePolicyManager.lockNow();
        } else {
            handleShowToast(getResources().getString(R.string.cannot_lock));
        }
    }

    private boolean isDeviceAdminActive() {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName compName = new ComponentName(this, ZenAdmin.class);
        return devicePolicyManager.isAdminActive(compName);
    }

    @Override
    public void onCreate() {
        if(BuildConfig.DEBUG) Log.i(TAG, "onCreate...");
        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mAccessibilityManager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);

        super.onCreate();
    }

    @Override
    public void onServiceConnected() {
        if(BuildConfig.DEBUG) Log.i(TAG, "onServiceConnected");
        super.onServiceConnected();
        if (isProximityLockEnabled(this)) {
            startListeningProximitySensor();
        }
    }

    // ADDED: Required for AccessibilityService
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not required for this use case
    }

    // ADDED: Required for AccessibilityService
    @Override
    public void onInterrupt() {
        // Not required for this use case
    }

    private void sendMessage(ZEvent event) {
        EventBus.getDefault().post(event);
    }

    private void sendMessageSticky2(ZEvent event) {
        EventBus.getDefault().postSticky(event);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if(BuildConfig.DEBUG) Log.i(TAG, "onStartCommand..." + intent.getAction());
        if (intent == null || intent.getAction() == null) return START_NOT_STICKY;

        serviceExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (intent.getAction().equals(GOOGLE_SIGN_IN.toString())) handleGoogleSignIn(intent);
                else if (intent.getAction().equals(GOOGLE_SIGN_OUT.toString())) handleGoogleSignOut(intent);

                else if (intent.getAction().equals(NIGHTMODE_ON.toString())) startNightMode();
                else if (intent.getAction().equals(NIGHTMODE_OFF.toString())) stopNightMode();
                else if (intent.getAction().equals(LAUNCH_INTENT.toString())) launchIntent(intent);
                else if (intent.getAction().equals(ENABLE_PROXIMITY.toString())) startListeningProximitySensor();
                else if (intent.getAction().equals(DISABLE_PROXIMITY.toString())) stopListeningProximitySensor();
                else if (intent.getAction().equals(SCREEN_ON.toString())) turnOnScreen();
                else if (intent.getAction().equals(SCREEN_OFF.toString())) turnOffScreen();
                else if (intent.getAction().equals(SCREEN_OFF_GESTURE.toString())) turnOffScreenGesture();
                else if (intent.getAction().equals(DEV_ADMIN_LOCK_AFTER.toString())) lockScreenAfter(intent);
                else if (intent.getAction().equals(ALARM_IN_ACTION.toString())) alarmIn(intent);
                else if (intent.getAction().equals(ALARM_PICKER.toString())) alarmAtPicker(intent);
            }
        });

        return START_NOT_STICKY;
    }

    private void alarmAtPicker(Intent intent) {
        long millis = intent.getLongExtra(ALARM_DATE_PICKER_MILLIS.toString(),0);
        String enteredText = intent.getStringExtra(ALARM_ENTERED_TEXT.toString());

        if (BuildConfig.DEBUG)  Log.d(TAG,"alarmAtPicker, millis:"+ millis);
        Calendar calAlarm = Calendar.getInstance();
        calAlarm.setTimeZone(TimeZone.getTimeZone("GMT"));
        calAlarm.setTimeInMillis(millis);
        setAlarm(calAlarm, enteredText);
    }

    private void lockScreenAfter(Intent intent) {
        int minutes = intent.getIntExtra(ZenProvider.mMinutes, 0);
        if (BuildConfig.DEBUG) Log.d(TAG, "lockScreenTimer: " + minutes);
        handler.postDelayed(lockAfterRunnable, minutes * 60000);
        if (isDeviceAdminActive()) {
            String toast = getString(R.string.lockIn) +" "+ getString(R.string.after)+ " " + minutes + " "+ getString(R.string.minutes);
            handleShowToast(toast );
        } else {
            handleShowToast(getString(R.string.deviceadmin_switch_text));
            sendMessage(new ZEvent(DEV_ADMIN_LOCK_AFTER));
        }
    }

    private void alarmIn(Intent intent){
        String query = intent.getStringExtra(ALARM_ENTERED_TEXT.toString());

        long minutes = intent.getLongExtra(ZenProvider.mMinutes, 0);
        if (BuildConfig.DEBUG)  Log.d(TAG,"alarmIn, minutes:"+ minutes);
        Calendar calAlarm = Calendar.getInstance();
        calAlarm.setTimeZone(TimeZone.getTimeZone("GMT"));
        calAlarm.setTime(new Date(System.currentTimeMillis()));
        // Note: truncated functionality from previous snippet handled implicitly or missing in source provided.
    }

    // Placeholder methods to prevent compilation errors if these methods exist outside the provided snippet
    private void handleGoogleSignIn(Intent intent) {}
    private void handleGoogleSignOut(Intent intent) {}
    private void startNightMode() {}
    private void stopNightMode() {}
    private void launchIntent(Intent intent) {}
    private void screenOn() {}

    private void handleShowToast(String s) {}
    private void setAlarm(Calendar c, String s) {}
}
