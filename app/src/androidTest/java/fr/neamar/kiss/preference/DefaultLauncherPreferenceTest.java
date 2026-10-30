package fr.neamar.kiss.preference;

import android.content.ActivityNotFoundException;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultLauncherPreferenceTest {
    @Test
    public void opensHomeSettingsInsteadOfExistingLauncher() {
        RecordingContext context = new RecordingContext(0);
        DefaultLauncherPreference.selectLauncher(context);
        assertEquals(1, context.intents.size());
        Intent intent = context.intents.get(0);
        assertEquals(Settings.ACTION_HOME_SETTINGS, intent.getAction());
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void unavailableHomeSettingsFallsBackToDefaultAppSettings() {
        RecordingContext context = new RecordingContext(1);
        DefaultLauncherPreference.selectLauncher(context);
        assertEquals(2, context.intents.size());
        assertEquals(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, context.intents.get(1).getAction());
    }

    @Test
    public void unavailableDefaultAppSettingsFallsBackToGeneralSettings() {
        RecordingContext context = new RecordingContext(2);
        DefaultLauncherPreference.selectLauncher(context);
        assertEquals(3, context.intents.size());
        assertEquals(Settings.ACTION_SETTINGS, context.intents.get(2).getAction());
    }

    private static class RecordingContext extends ContextWrapper {
        final List<Intent> intents = new ArrayList<>();
        private final int failures;

        RecordingContext(int failures) {
            super(getInstrumentation().getTargetContext());
            this.failures = failures;
        }

        @Override
        public PackageManager getPackageManager() {
            throw new AssertionError("Selection must not resolve HOME or toggle dummy components");
        }

        @Override
        public void startActivity(Intent intent) {
            intents.add(intent);
            if (intents.size() <= failures) throw new ActivityNotFoundException("Unavailable settings screen");
        }
    }
}
