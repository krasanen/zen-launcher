package fr.neamar.kiss.forwarder;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageView;

import org.greenrobot.eventbus.EventBus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

import fi.zmengames.zen.AccessibilityDisclosureHelper;
import fi.zmengames.zen.ZEvent;
import fr.neamar.kiss.BuildConfig;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.R;
import fr.neamar.kiss.searcher.HistorySearcher;
import fr.neamar.kiss.searcher.NullSearcher;

import static android.text.InputType.TYPE_CLASS_PHONE;

import static fi.zmengames.zen.ZEvent.State.BARCODE_READER;
import static fr.neamar.kiss.MainActivity.mDebugJson;


// Deals with any settings in the "User Experience" setting sub-screen
public class ExperienceTweaks extends Forwarder {
    private static final String TAG = ExperienceTweaks.class.getSimpleName();

    /**
     * InputType that behaves as if the consuming IME is a standard-obeying
     * soft-keyboard
     * <p>
     * *Auto Complete* means "we're handling auto-completion ourselves". Then
     * we ignore whatever the IME thinks we should display.
     */
    private final static int INPUT_TYPE_STANDARD = InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    /**
     * InputType that behaves as if the consuming IME is SwiftKey
     * <p>
     * *Visible Password* fields will break many non-Latin IMEs and may show
     * unexpected behaviour in numerous ways. (#454, #517)
     */
    private final static int INPUT_TYPE_WORKAROUND = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;

    private final Runnable displayKeyboardRunnable = new Runnable() {
        @Override
        public void run() {
            mainActivity.showKeyboard();
        }
    };

    private View mainEmptyView;
    private final GestureDetector gd;
    private final ScaleGestureDetector sgd;
    private boolean scaling;
    int width, height;
    public static boolean mNumericInputTypeForced = false;
    private boolean swipeDetected;

    // divider that affects amount of pixels to be used in gesture detection
    // screen width / SWIPE_DIVIDER
    // screen height / SWIPE_DIVIDER
    private final float SWIPE_DIVIDER = 8f;

    ExperienceTweaks(final MainActivity mainActivity) {
        super(mainActivity);

        Display display = mainActivity.getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        width = size.x;
        height = size.y;
        if(BuildConfig.DEBUG) Log.i(TAG, "ExperienceTweaks create");
        // Lock launcher into portrait mode
        // Do it here (before initializing the view in onCreate) to make the transition as smooth as possible
        if (prefs.getBoolean("force-portrait", true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mainActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
            } else {
                mainActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        } else {
            mainActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
        }


        sgd = new ScaleGestureDetector(mainActivity, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (mDebugJson) {
                    if(BuildConfig.DEBUG) Log.i(TAG, "onScale");
                }
                scaling = true;
                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                if (mDebugJson) {
                    if(BuildConfig.DEBUG) Log.i(TAG, "onScaleBegin");
                }
                scaling = true;
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                if (mDebugJson) {
                    if(BuildConfig.DEBUG) Log.i(TAG, "onScaleEnd");
                }
                if (prefs.getBoolean("pinch-open", false)) {
                    mainActivity.launcherButton.performClick();
                }
                scaling = false;
            }
        });

        gd = new GestureDetector(mainActivity, new GestureDetector.SimpleOnGestureListener() {


            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return super.onSingleTapConfirmed(e);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if(BuildConfig.DEBUG) Log.i(TAG, "onFling:" +SWIPE_DIVIDER);
                if (e1!=null&&e2!=null) {
                    float directionY = e2.getY() - e1.getY();
                    float directionX = e2.getX() - e1.getX();
                    if(BuildConfig.DEBUG) Log.i(TAG, "directionX:" + directionX);
                    if(BuildConfig.DEBUG) Log.i(TAG, "directionY:" + directionY);

                    if (!mainActivity.isViewingAllApps() && !scaling) {

                        if (Math.abs(directionY) > height / SWIPE_DIVIDER) {
                            swipeDetected = true;
                            if (directionY < 0) {
                                if(BuildConfig.DEBUG) Log.i(TAG, "swipeUp");
                                onSwipeUp();
                            } else {
                                if(BuildConfig.DEBUG) Log.i(TAG, "swipeDown");
                                onSwipeDown();
                            }
                            return true;
                        }
                        if (Math.abs(directionX) > width / SWIPE_DIVIDER) {
                            swipeDetected = true;
                            if (directionX > 0) {
                                if(BuildConfig.DEBUG) Log.i(TAG, "swipeRight");
                                onSwipeRight();

                            } else {
                                if(BuildConfig.DEBUG) Log.i(TAG, "swipeLeft");
                                onSwipeLeft();

                            }
                            return true;
                        }

                    }
                }
                return true;
            }
        });
    }

    void onCreate() {
        adjustInputType(null);
        mainEmptyView = mainActivity.findViewById(R.id.main_empty);

    }

    void onResume() {
        if (mNumericInputTypeForced){
            mNumericInputTypeForced = false;
        }
        // Activity manifest specifies stateAlwaysHidden as windowSoftInputMode
        // so the keyboard will be hidden by default
        // we may want to display it if the setting is set
        if (isKeyboardOnStartEnabled()) {
            // Display keyboard
            mainActivity.showKeyboard();

            new Handler().postDelayed(displayKeyboardRunnable, 10);
            // For some weird reasons, keyboard may be hidden by the system
            // So we have to run this multiple time at different time
            // See https://github.com/Neamar/KISS/issues/119
            new Handler().postDelayed(displayKeyboardRunnable, 100);
            new Handler().postDelayed(displayKeyboardRunnable, 500);
        } else {
            // Not used (thanks windowSoftInputMode)
            // unless coming back from Zen Launcher settings
            if(mainActivity.isKeyboardVisible()) {
                mainActivity.hideKeyboard();
            }
        }

        if (isMinimalisticModeEnabled()) {
            mainEmptyView.setVisibility(View.GONE);

            mainActivity.list.setVerticalScrollBarEnabled(false);
            mainActivity.searchEditText.setHint("");
        }
        if (prefs.getBoolean("pref-hide-circle", false)) {
            ((ImageView) mainActivity.launcherButton).setImageBitmap(null);
            ((ImageView) mainActivity.menuButton).setImageBitmap(null);
        }
    }
    Handler handler = new Handler();
    int numberOfTaps = 0;
    long lastTapTimeMs = 0;
    long touchDownMs = 0;
    boolean onTouch(View view, MotionEvent event) {
        // Forward touch events to the gesture detector
        gd.onTouchEvent(event);
        sgd.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDownMs = System.currentTimeMillis();

                break;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacksAndMessages(null);

                if ((System.currentTimeMillis() - touchDownMs) > ViewConfiguration.getLongPressTimeout()) {
                    //it was not a tap

                    numberOfTaps = 0;
                    lastTapTimeMs = 0;
                    break;
                }

                if (numberOfTaps >= 0
                        && (System.currentTimeMillis() - lastTapTimeMs) < ViewConfiguration.getDoubleTapTimeout()) {
                    numberOfTaps += 1;
                    if(BuildConfig.DEBUG) Log.i(TAG,"numberOfTaps += 1");
                } else {
                    numberOfTaps = 1;
                    if(BuildConfig.DEBUG) Log.i(TAG,"onetap");
                    Runnable onetap = new Runnable() {
                        @Override
                        public void run() {
                            if (numberOfTaps == 1 )
                                numberOfTaps = 0;
                            if (swipeDetected) {
                                swipeDetected = false;
                            } else {
                                onSingleTap();
                            }
                        }
                    };
                    handler.postDelayed(onetap, ViewConfiguration.getDoubleTapTimeout());
                }

                lastTapTimeMs = System.currentTimeMillis();

                if (numberOfTaps == 3) {
                    if(BuildConfig.DEBUG) Log.i(TAG,"tripletap");
                    onTripleTap();
                    numberOfTaps = 0;
                    //handle triple tap
                } else if (numberOfTaps == 2) {
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if(BuildConfig.DEBUG) Log.i(TAG,"doubletap");
                            if (numberOfTaps==2) {
                                onDoubleTap();
                            }
                        }
                    }, ViewConfiguration.getDoubleTapTimeout());
                }
                break;
            default:
                return false;
        }
        return false;
    }

    public void toggleScreenOnOff() {
        // turn off/on screen backlight
        WindowManager.LayoutParams params = mainActivity.getWindow().getAttributes();
        if (params.screenBrightness == -1) {
            params.screenBrightness = 0;
        } else {
            params.screenBrightness = -1;
        }
        params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        mainActivity.getWindow().setAttributes(params);
    }


    void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && isKeyboardOnStartEnabled()) {
            mainActivity.showKeyboard();
        }
    }

    void onDisplayKissBar(Boolean display) {
        if (isMinimalisticModeEnabledForFavorites() && !display) {
            mainActivity.favoritesBar.setVisibility(View.GONE);
        }

        if (!display && isKeyboardOnStartEnabled()) {
            // Display keyboard
            mainActivity.showKeyboard();
        }
    }

    void updateSearchRecords(String query) {
        if (!mNumericInputTypeForced) {
            adjustInputType(query);
        }

        if (query.isEmpty()) {
            if (isMinimalisticModeEnabled()) {
                mainActivity.runTask(new NullSearcher(mainActivity));
                // By default, help text is displayed -- not in minimalistic mode.
                mainEmptyView.setVisibility(View.GONE);

                if (isMinimalisticModeEnabledForFavorites()) {
                    mainActivity.favoritesBar.setVisibility(View.GONE);
                }
            } else {
                mainActivity.runTask(new HistorySearcher(mainActivity));
            }
        }
    }

    // Ensure the keyboard uses the right input method
    public void adjustInputType(String currentText) {
        int currentInputType = mainActivity.searchEditText.getInputType();
        int requiredInputType;

        if (isSuggestionsEnabled()) {
            requiredInputType = InputType.TYPE_CLASS_TEXT;
        } else if (currentText != null && Pattern.matches("[+]\\d+", currentText)) {
            requiredInputType = TYPE_CLASS_PHONE;
        } else if (isNonCompliantKeyboard()) {
            requiredInputType = INPUT_TYPE_WORKAROUND;
        } else {
            requiredInputType = INPUT_TYPE_STANDARD;
        }
        if (currentInputType != requiredInputType) {
            mainActivity.searchEditText.setInputType(requiredInputType);
        }
    }
    // Ensure the keyboard uses the right input method
    public void switchInputType() {
        int currentInputType = mainActivity.searchEditText.getInputType();
        if (currentInputType!=  TYPE_CLASS_PHONE) {
            mNumericInputTypeForced = true;
            mainActivity.searchEditText.setInputType(TYPE_CLASS_PHONE);
        } else {
            int requiredInputType;
            if (isSuggestionsEnabled()) {
                requiredInputType = InputType.TYPE_CLASS_TEXT;
            } else if (isNonCompliantKeyboard()) {
                requiredInputType = INPUT_TYPE_WORKAROUND;
            } else {
                requiredInputType = INPUT_TYPE_STANDARD;
            }
            mNumericInputTypeForced = false;
            mainActivity.searchEditText.setInputType(requiredInputType);

        }
    }
    // Super hacky code to display notification drawer
    // Can (and will) break in any Android release.
    @SuppressLint("PrivateApi")
    private void displayNotificationDrawer() {
        @SuppressLint("WrongConstant") Object sbservice = mainActivity.getSystemService("statusbar");
        Class<?> statusbarManager = null;
        try {
            statusbarManager = Class.forName("android.app.StatusBarManager");
            Method showStatusBar;
            if (Build.VERSION.SDK_INT >= 17) {
                showStatusBar = statusbarManager.getMethod("expandNotificationsPanel");
            }
            else {
                showStatusBar = statusbarManager.getMethod("expand");
            }
            showStatusBar.invoke( sbservice );
        } catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        } catch (NoSuchMethodException e1) {
            e1.printStackTrace();
        } catch (IllegalAccessException e1) {
            e1.printStackTrace();
        } catch (InvocationTargetException e1) {
            e1.printStackTrace();
        }
    }

    private boolean isMinimalisticModeEnabled() {
        return prefs.getBoolean("history-hide", false);
    }

    private boolean isMinimalisticModeEnabledForFavorites() {
        return prefs.getBoolean("history-hide", false) && prefs.getBoolean("favorites-hide", false) && prefs.getBoolean("enable-favorites-bar", true);
    }

    /**
     * Should we force the keyboard not to display suggestions?
     * (swiftkey is broken, see https://github.com/Neamar/KISS/issues/44)
     */
    private boolean isNonCompliantKeyboard() {
        String currentKeyboard = Settings.Secure.getString(mainActivity.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return currentKeyboard.contains("swiftkey");
    }

    /**
     * Should the keyboard be displayed by default?
     */
    private boolean isKeyboardOnStartEnabled() {
        return prefs.getBoolean("display-keyboard", false);
    }

    public void hideKeyboard() {
        if(BuildConfig.DEBUG) Log.i(TAG,"hideKeyboard");
        if(mNumericInputTypeForced){
            mNumericInputTypeForced = false;
            adjustInputType(null);
        }
    }

    /**
     * Should the keyboard autocomplete and suggest options
     */
    private boolean isSuggestionsEnabled() {
        return prefs.getBoolean("enable-suggestions-keyboard", false);
    }

    private void onSwipeDown() {
        doAction(prefs.getString("gesture-swipe-down", "display-notifications"));
    }

    private void onSwipeUp() {
        doAction(prefs.getString("gesture-swipe-up", "open-keyboard"));
    }

    private void onSwipeLeft() {
        doAction(prefs.getString("gesture-swipe-left", "display-contacts"));
    }

    private void onSwipeRight() {
        doAction(prefs.getString("gesture-swipe-right", "display-contacts"));
    }

    public void onSingleTap() {
        if (prefs.getBoolean("history-onclick", false)) {
            doAction("display-history");
        }
        else if(isMinimalisticModeEnabledForFavorites()) {
            doAction("display-favorites");
        } else {
            doAction(prefs.getString("gesture-single-tap", "do-nothing"));
        }
    }

    public void onDoubleTap() {
        doAction(prefs.getString("gesture-double-tap", "display-apps-notif"));
    }

    private void onTripleTap() {
        doAction(prefs.getString("gesture-triple-tap", "start-flashlight"));
    }

    private void doAction(String action) {
        if(BuildConfig.DEBUG) Log.i(TAG,"doAction: "+ action);
        if (!isGesturesEnabled()) {
            return;
        }
        switch (action) {
            case "display-notifications":
                displayNotificationDrawer();
                break;
            case "display-keyboard":
                mainActivity.showKeyboard();
                break;
            case "display-contacts":
                mainActivity.displayContacts(true);
                break;
            case "display-apps":
                mainActivity.displayKissBar(true);
                break;
            case "display-all-apps":
                mainActivity.startAppGridActivity();
                break;
            case "display-apps-notif":
                mainActivity.displayAppsWithNotif(true);
                break;
            case "lock-screen":
                mainActivity.lockScreen();
                break;
            case "turn-off-screen":
                // Check if accessibility service is enabled, show disclosure if needed
                if (AccessibilityDisclosureHelper.isAccessibilityServiceEnabled(mainActivity)) {
                    Intent intentScreenOff = new Intent(mainActivity, fi.zmengames.zen.LauncherService.class);
                    intentScreenOff.setAction(ZEvent.State.SCREEN_OFF.toString());
                    mainActivity.startService(intentScreenOff);
                } else {
                    // Show prominent disclosure and request permission
                    AccessibilityDisclosureHelper.requestAccessibilityPermission(mainActivity, null);
                }
                break;
            case "display-history":
                // if minimalistic mode is enabled,
                if (isMinimalisticModeEnabled()) {
                    // and we're currently in minimalistic mode with no results,
                    // and we're not looking at the app list
                    if (mainActivity.isViewingSearchResults() && TextUtils.isEmpty(mainActivity.searchEditText.getText())) {
                        if (mainActivity.list.getAdapter() == null || mainActivity.list.getAdapter().isEmpty()) {
                            mainActivity.showHistory();
                        }
                    }
                }

                if (isMinimalisticModeEnabledForFavorites()) {
                    mainActivity.favoritesBar.setVisibility(View.VISIBLE);
                }
                break;
            case "display-favorites":
                // Not provided as an option for the gestures, but useful if you only want to display favorites on tap,
                // not history.
                mainActivity.favoritesBar.setVisibility(View.VISIBLE);
            case "hide-keyboard":
                if (mainActivity.isKeyboardVisible()) {
                    mainActivity.hideKeyboard();
                }
                break;
            case "start-flashlight":
                mainActivity.toggleFlashLight();
                break;
            case "start-barcode-reader":
                EventBus.getDefault().post(new ZEvent(BARCODE_READER));
                break;
        }
    }
    private boolean isGesturesEnabled() {
        return prefs.getBoolean("enable-gestures", true);
    }
}
