package fr.neamar.kiss.forwarder;

import android.content.Intent;
import android.content.pm.LauncherApps;
import android.util.Log;

import fr.neamar.kiss.BuildConfig;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.preference.DefaultLauncherPreference;
import fr.neamar.kiss.utils.ShortcutUtil;


public class OreoShortcuts extends Forwarder {
    private static final String TAG = OreoShortcuts.class.getSimpleName();
    OreoShortcuts(MainActivity mainActivity) {
        super(mainActivity);
    }

    void onCreate() {
        // Shortcuts in Android O
        // The built-in launcher switch is supplied by LoadShortcutsPojos rather
        // than stored here, where shortcut cleanup could immediately delete it.
        if (ShortcutUtil.areShortcutsEnabled(mainActivity)) {

            // On first run save all shortcuts
            if (prefs.getBoolean("first-run-shortcuts", true)) {

                DefaultLauncherPreference.AsyncTaskCompleteListener callback = (result, activity) -> {
                    // Handle the result here
                    if (result) {
                        ShortcutUtil.addAllShortcuts(mainActivity);
                        // Set flag to false
                        prefs.edit().putBoolean("first-run-shortcuts", false).apply();
                    } else {
                        if (BuildConfig.DEBUG) Log.d(TAG,"not a default launcher");
                    }
                };

                DefaultLauncherPreference.ResolveDefaultLauncherTask myAsyncTask = new DefaultLauncherPreference.ResolveDefaultLauncherTask(callback,mainActivity);
                myAsyncTask.execute();

            }

            Intent intent = mainActivity.getIntent();
            if (intent != null) {
                final String action = intent.getAction();
                if (LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT.equals(action)) {
                    // Save single shortcut via a pin request
                    ShortcutUtil.addShortcut(mainActivity, intent);
                }
            }
        }

    }

}
