package fi.zmengames.zen;


import android.accessibilityservice.AccessibilityService;
import android.animation.Animator;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
                    if (BuildConfig.DEBUG) Log.d(TAG, "lockScreenStartTimer: " + run + ", running=" + running);
                    if (run) {
                        if (!running) {
                            running = true;
                            handler.postDelayed(lockProximityRunnable, 2000);
                        }
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

    // Screen off using accessibility service (Android P+) with fallback to device admin
    public void turnOffScreenGesture(){
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
        }
    }


    public void lockScreenProximity() {
        turnOffScreenGesture();
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

    // Required for AccessibilityService
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not required for this use case
    }

    // Required for AccessibilityService
    @Override
    public void onInterrupt() {
        // Not required for this use case
    }

    @Override
    public void onDestroy() {
        if(BuildConfig.DEBUG) Log.v(TAG, "in onDestroy");
        super.onDestroy();
        if (isProximityLockEnabled(this)) {
            stopListeningProximitySensor();
        }
        stopNightMode();
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
                else if (intent.getAction().equals(SCREEN_ON.toString())) screenOn();
                else if (intent.getAction().equals(SCREEN_OFF.toString())) screenOff();
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
        Date alarm = new Date(System.currentTimeMillis()+minutes*60*1000);
        alarm.setSeconds(0);
        calAlarm.setTimeInMillis(alarm.getTime());
        setAlarm(calAlarm, query);
    }


    private void checkBatteryOptimizations(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm!=null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                handleShowToast(getResources().getString(R.string.alarm_battery_optimization));
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setData(Uri.parse("package:" + packageName));
                try {
                    startActivity(intent);
                } catch (RuntimeException e){
                    handleShowToast(getResources().getString(R.string.alarm_battery_optimization));
                }
            }
        }
    }


    private void checkDnd() {
        // Get the notification manager instance
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        checkBatteryOptimizations();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // If notification policy access granted for this package
            if (mNotificationManager != null && mNotificationManager.isNotificationPolicyAccessGranted()) {
            } else {
                handleShowToast(getResources().getString(R.string.notification_access));
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(intent);
                } catch (RuntimeException e){
                    handleShowToast(getResources().getString(R.string.notification_access));
                }
            }
        }
    }

    private void setAlarm(Calendar targetCal, String query) {
        checkDnd();
        handleShowToast(getResources().getString(R.string.alarm_is_set_at)
                + " " + targetCal.getTime());
        if (BuildConfig.DEBUG) Log.d(TAG,"targetCal.getTimeInMillis():"+targetCal.getTimeInMillis());
        Intent intent = new Intent(getApplicationContext(), AlarmReceiver.class);
        long notificationId = targetCal.getTimeInMillis();
        intent.putExtra(ALARM_ID, notificationId);
        intent.putExtra(ALARM_EXTRA, query);
        intent.putExtra(ALARM_TIME, targetCal.getTime().toString());
        AlarmUtils.addAlarm(getApplicationContext(), intent, notificationId,targetCal);
    }

    private void screenOn() {
        if (BuildConfig.DEBUG) Log.d(TAG, "screenOn");
        if (isProximityLockEnabled(this)) {
            startListeningProximitySensor();
        }
    }

    private void screenOff() {
        if (BuildConfig.DEBUG) Log.d(TAG, "screenOff");
        if (isProximityLockEnabled(this)) {
            stopListeningProximitySensor();
        }
    }


    private void launchIntent(Intent intent) {
        if(BuildConfig.DEBUG) Log.i(TAG, "launchIntent");
        Intent toLaunch = intent.getParcelableExtra(Intent.EXTRA_INTENT);
        if (toLaunch != null) {
            toLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            startActivity(toLaunch);
        }
        catch(SecurityException e){
            handleShowToast(e.getMessage());
        }
        catch(Exception e) {
            e.printStackTrace();
            handleShowToast(e.getMessage());
        }
    }


    private void handleGoogleSignIn(Intent intent) {
        if(BuildConfig.DEBUG) Log.i(TAG, "handleGoogleSignIn");
        sendMessageSticky2(new ZEvent(ZEvent.State.GOOGLE_SIGNIN));
    }


    private void handleGoogleSignOut(Intent intent) {
        if(BuildConfig.DEBUG) Log.i(TAG, "handleGoogleSignOut");
        sendMessageSticky2(new ZEvent(ZEvent.State.GOOGLE_SIGNOUT));
    }

    private void handleShowToast(String text) {
        if(BuildConfig.DEBUG) Log.i(TAG, "handleShowToast");
        sendMessage(new ZEvent(SHOW_TOAST, text));
    }


    private void startNightMode() {
        if(BuildConfig.DEBUG) Log.i(TAG, "startNightMode");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                if (mLayout == null) {
                    mLayout = new View(getApplicationContext());
                    mLayout.setLayoutParams(
                            new ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                            )
                    );
                    mLayout.setBackgroundColor(Color.BLACK);
                    mLayout.setAlpha(0.1f);
                }
                updateLayoutParams();
                try {
                    mWindowManager.addView(mLayout, mLayoutParams);
                    handleShowToast(getResources().getString(R.string.nightmodeOn));
                } catch (Exception e) {
                    e.printStackTrace();
                    if(BuildConfig.DEBUG) Log.i(TAG, "startNightMode CANNOT_START");
                    Intent broadcastIntent = new Intent();
                    broadcastIntent.setAction(MainActivity.class.getCanonicalName());
                    broadcastIntent.putExtra(Constants.Extra.EVENT_ID, Constants.Event.CANNOT_START);
                    sendBroadcast(broadcastIntent);
                }
            }
        });
    }


    private static float constrain(float paramFloat1, float paramFloat2, float paramFloat3) {
        if (paramFloat1 < paramFloat2) {
            return paramFloat2;
        }
        if (paramFloat1 > paramFloat3) {
            return paramFloat3;
        }
        return paramFloat1;
    }


    public int getWindowType() {
        int sdkInt = Build.VERSION.SDK_INT;
        if (sdkInt >= Build.VERSION_CODES.O) {
            // Only TYPE_APPLICATION_OVERLAY is available in O.
            return TYPE_APPLICATION_OVERLAY;
        }
        // Default window type.
        int result = TYPE_SYSTEM_OVERLAY;
        if (mAdvancedMode == Constants.AdvancedMode.NO_PERMISSION
                && sdkInt < Build.VERSION_CODES.N) {
            // Toast Mode cannot work normally after N. Window will be set 10~ secs max timeout.
            result = TYPE_TOAST;
        } else if (mAdvancedMode == Constants.AdvancedMode.OVERLAY_ALL) {
            // It seems that this mode should use TYPE_SYSTEM_ERROR as window type.
            result = TYPE_SYSTEM_ERROR;
        }
        return result;
    }


    private void updateLayoutParams() {
        if (mLayoutParams == null) {
            mLayoutParams = new WindowManager.LayoutParams();
        }

        // Apply layout params type & gravity
        mLayoutParams.type = getWindowType();
        mLayoutParams.gravity = Gravity.CENTER;

        // Apply layout params attributes
        mLayoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        mLayoutParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        mLayoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        mLayoutParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        mLayoutParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        mLayoutParams.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        mLayoutParams.format = PixelFormat.TRANSLUCENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            InputManager im = (InputManager) getSystemService(Context.INPUT_SERVICE);
            float a = im.getMaximumObscuringOpacityForTouch();
            if (a<0.8f) {
                mLayoutParams.alpha = a;
            } else {
                mLayoutParams.alpha = 0.8f;
            }
        } else {
            mLayoutParams.alpha = 0.8f;
        }
        if (mLayout != null) {
            int color = Color.YELLOW;
            if (mYellowFilterAlpha > 0) {
                Log.i(TAG, "Alpha: " + mYellowFilterAlpha);
                float ratio = ((float) mYellowFilterAlpha) / 100F;
                color = ColorUtil.blendColors(Color.YELLOW, Color.TRANSPARENT, ratio);
            }
            mLayout.setBackgroundColor(color);
        }
    }


    private void cancelNotification() {
        try {
            mNotificationManager.cancel(NOTIFICATION_NO);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void stopNightMode() {
        if(BuildConfig.DEBUG) Log.i(TAG, "stopNightMode");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                cancelNotification();
                if (mLayout != null) {
                    handleShowToast(getResources().getString(R.string.nightmodeOff));
                    mLayout.animate()
                            .alpha(0f)
                            .setDuration(ANIMATE_DURATION_MILES)
                            .setListener(new Animator.AnimatorListener() {
                                private final View readyToRemoveView = mLayout;

                                @Override
                                public void onAnimationStart(Animator animator) {
                                }

                                @Override
                                public void onAnimationCancel(Animator animator) {
                                }

                                @Override
                                public void onAnimationRepeat(Animator animator) {
                                }

                                @Override
                                public void onAnimationEnd(Animator animator) {
                                    try {
                                        mWindowManager.removeViewImmediate(readyToRemoveView);
                                    } catch (Exception e) {
                                        // Just do nothing
                                    }
                                }
                            }).start();
                    mLayout = null;
                }
            }
        });
    }
}
