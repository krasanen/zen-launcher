package fi.zmengames.zen;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

import androidx.core.view.WindowCompat;
import androidx.fragment.app.FragmentActivity;

import fr.neamar.kiss.BuildConfig;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.R;
import fr.neamar.kiss.UIColors;

public class AppGridActivity extends FragmentActivity {
    private static final String TAG = AppGridActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Setting the theme needs to be done before setContentView()
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String theme = prefs.getString("theme", "light");
        applySystemUi(isPreferenceHideNavBar(prefs), false);

        switch (theme) {
            case "dark":
                this.setTheme(R.style.AppThemeDark);
                break;
            case "transparent":
                this.setTheme(R.style.AppThemeTransparent);
                break;
            case "semi-transparent":
                this.setTheme(R.style.AppThemeSemiTransparent);
                break;
            case "semi-transparent-dark":
                this.setTheme(R.style.AppThemeSemiTransparentDark);
                break;
            case "transparent-dark":
                this.setTheme(R.style.AppThemeTransparentDark);
                break;
            case "amoled-dark":
                this.setTheme(R.style.AppThemeAmoledDark);
                break;
        }

        UIColors.updateThemePrimaryColor(this);
        this.getTheme().applyStyle(prefs.getBoolean("small-results", false) ? R.style.OverlayResultSizeSmall : R.style.OverlayResultSizeStandard, true);
        
        // Handle status bar color with Android 15+ compatibility
        Window window = getWindow();
        int backgroundColor = getColorBasedOnTheme(this, R.attr.listBackgroundColor);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // On Android 15+, setStatusBarColor is deprecated and no-op
            // Use WindowCompat to control appearance instead
            WindowCompat.setDecorFitsSystemWindows(window, true);
            boolean isLightColor = isColorLight(backgroundColor);
            WindowCompat.getInsetsController(window, window.getDecorView())
                    .setAppearanceLightStatusBars(isLightColor);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(backgroundColor);
        }
        setContentView(R.layout.appgripscreen);

    }

    private int getColorBasedOnTheme(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    /**
     * Determines if a color is light (for choosing status bar icon color)
     */
    private boolean isColorLight(int color) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        double darkness = 1 - (0.299 * red + 0.587 * green + 0.114 * blue) / 255;
        return darkness < 0.5;
    }

    float startY;
    float touchSlop = 400.0f; // amount scroll needed to close app grid
    public static boolean onTop = false;
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (BuildConfig.DEBUG) Log.i(TAG, "dispatchTouchEvent: " + ev.getAction());
        super.dispatchTouchEvent(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startY = ev.getY(); // Save the initial Y coordinate
                MainActivity.dismissPopup();
                break;
            case MotionEvent.ACTION_MOVE:
                float deltaY = ev.getY() - startY;
                if (onTop && deltaY > touchSlop) {
                    onBackPressed();
                }
                break;
        }

        return true;
    }
    @Override
    public void onBackPressed() {
        if (BuildConfig.DEBUG) Log.i(TAG, "onBackPressed");
        if (MainActivity.isShowingPopup()){
            MainActivity.dismissPopup();
        } else {
            super.onBackPressed();
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_settings, menu);
        return true;
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

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(visibility);

    }
    private boolean isPreferenceHideNavBar(SharedPreferences prefs) {
        return prefs.getBoolean("pref-hide-navbar", false);
    }

    private boolean isPreferenceHideStatusBar(SharedPreferences prefs) {
        return prefs.getBoolean("pref-hide-statusbar", false);
    }
}
