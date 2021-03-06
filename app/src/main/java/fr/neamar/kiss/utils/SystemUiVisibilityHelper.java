package fr.neamar.kiss.utils;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import fr.neamar.kiss.BuildConfig;
import fr.neamar.kiss.MainActivity;

import static fr.neamar.kiss.forwarder.ExperienceTweaks.mNumericInputTypeForced;

public class SystemUiVisibilityHelper implements View.OnSystemUiVisibilityChangeListener {
    private final MainActivity mMainActivity;
    private final Handler mHandler;
    private final SharedPreferences prefs;
    private boolean mKeyboardVisible;
    private boolean mIsScrolling;
    private int mPopupCount;
    private static final String TAG = SystemUiVisibilityHelper.class.getSimpleName();
    // This is used to emulate SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    private final Runnable autoApplySystemUiRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mKeyboardVisible && !mIsScrolling && mPopupCount == 0)
                applySystemUi();
        }
    };

    public SystemUiVisibilityHelper(MainActivity activity) {
        mMainActivity = activity;
        mHandler = new Handler(Looper.getMainLooper());
        prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        View decorView = mMainActivity.getWindow()
                .getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(this);
        mKeyboardVisible = false;
        mIsScrolling = false;
        mPopupCount = 0;
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            if (mIsScrolling)
                applyScrollSystemUi();
            else
                applySystemUi();
        }
    }

    public boolean isKeyboardVisible(){
        return mKeyboardVisible;
    }


    public void onKeyboardVisibilityChanged(boolean isVisible) {
        if (mKeyboardVisible==isVisible) return;
        mKeyboardVisible = isVisible;
        if (BuildConfig.DEBUG) Log.d(TAG,"onKeyboardVisibilityChanged:"+ isVisible);
        if (isVisible) {
            mHandler.removeCallbacks(autoApplySystemUiRunnable);
            applySystemUi(false, false);
            mMainActivity.historyButton.setVisibility(View.GONE);
            mMainActivity.searchEditText.setCursorVisible(true);
            if (mNumericInputTypeForced) {
                mMainActivity.numericButton.setVisibility(View.GONE);
                mMainActivity.keyboardButton.setVisibility(View.VISIBLE);
            } else {
                mMainActivity.numericButton.setVisibility(View.VISIBLE);
                mMainActivity.keyboardButton.setVisibility(View.GONE);
            }
        } else {
            autoApplySystemUiRunnable.run();
            mMainActivity.numericButton.setVisibility(View.GONE);
            mMainActivity.keyboardButton.setVisibility(View.GONE);
            if (prefs.getBoolean("enable-historybutton", true)) {
                mMainActivity.historyButton.setVisibility(View.VISIBLE);
            } else {
                mMainActivity.historyButton.setVisibility(View.GONE);
            }
            if (mNumericInputTypeForced){
                mMainActivity.switchInputType();
            }
            mMainActivity.searchEditText.setCursorVisible(false);
        }
    }

    private void applySystemUi() {
        applySystemUi(isPreferenceHideNavBar(), isPreferenceHideStatusBar());
    }

    private void applySystemUi(boolean hideNavBar, boolean hideStatusBar) {
        int visibility = 0;
        if (hideNavBar) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                visibility = visibility
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION; // hide nav bar
            } else {
                visibility = visibility
                        | View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION; // hide nav bar
            }
        }
        if (hideStatusBar) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                visibility = visibility
                        | View.SYSTEM_UI_FLAG_FULLSCREEN; // hide status bar
            }
        }
        if (hideNavBar || hideStatusBar) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                visibility = visibility
                        | View.SYSTEM_UI_FLAG_IMMERSIVE;
            }
        }
        View decorView = mMainActivity.getWindow()
                .getDecorView();
        decorView.setSystemUiVisibility(visibility);
    }

    public void applyScrollSystemUi() {
        mIsScrolling = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            applySystemUi();
        }
    }

    public void resetScroll() {
        mIsScrolling = false;
        if (!mKeyboardVisible)
            mHandler.post(autoApplySystemUiRunnable);
    }

    private boolean isPreferenceHideNavBar() {
        return prefs.getBoolean("pref-hide-navbar", false);
    }

    private boolean isPreferenceHideStatusBar() {
        return prefs.getBoolean("pref-hide-statusbar", false);
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("onSystemUiVisibilityChange %x", visibility));

        if ((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0)
            sb.append("\n SYSTEM_UI_FLAG_HIDE_NAVIGATION");

        if ((visibility & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) != 0)
            sb.append("\n SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN");
        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0)
            sb.append("\n SYSTEM_UI_FLAG_FULLSCREEN");

        if ((visibility & View.SYSTEM_UI_FLAG_IMMERSIVE) != 0)
            sb.append("\n SYSTEM_UI_FLAG_IMMERSIVE");
        if ((visibility & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0)
            sb.append("\n SYSTEM_UI_FLAG_IMMERSIVE_STICKY");

        if(BuildConfig.DEBUG) Log.w("TBog", sb.toString());

        if ((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0) {
            applySystemUi();
        }

        if (visibility == 0) {
            mHandler.postDelayed(autoApplySystemUiRunnable, 1500);
        }
    }

    public void copyVisibility(View contentView) {
        View decorView = mMainActivity.getWindow()
                .getDecorView();
        int visibility = decorView.getSystemUiVisibility();
        contentView.setSystemUiVisibility(visibility);
    }

    public void addPopup() {
        mPopupCount += 1;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            applySystemUi(false, false);
        }
    }

    public void popPopup() {
        mPopupCount -= 1;
        if (mPopupCount < 0) {
            Log.e("TBog", "popup count negative!");
            mPopupCount = 0;
        }
    }
}
