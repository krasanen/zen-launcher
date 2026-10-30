package fr.neamar.kiss.loader;

import android.content.Context;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import fr.neamar.kiss.R;
import fr.neamar.kiss.db.ShortcutRecord;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static fi.zmengames.zen.ZEvent.State.ACTION_SET_DEFAULT_LAUNCHER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class LoadShortcutsPojosTest {
    @Test
    public void launcherSwitchExistsAfterShortcutCleanup() {
        Context context = getInstrumentation().getTargetContext();
        List<ShortcutRecord> records = LoadShortcutsPojos.includeDefaultLauncher(context, Collections.emptyList());
        assertEquals(1, records.size());
        assertEquals(context.getString(R.string.default_launcher_title), records.get(0).name);
        assertEquals("zen", records.get(0).packageName);
        assertEquals(ACTION_SET_DEFAULT_LAUNCHER.toString(), records.get(0).intentUri);
    }

    @Test
    public void legacyCopiesAreReplacedWithoutRemovingUserShortcuts() {
        Context context = getInstrumentation().getTargetContext();
        ShortcutRecord legacy = new ShortcutRecord();
        legacy.name = "Old translated launcher switch";
        legacy.packageName = "zen";
        legacy.intentUri = ACTION_SET_DEFAULT_LAUNCHER.toString();
        ShortcutRecord userShortcut = new ShortcutRecord();
        userShortcut.name = "My shortcut";
        userShortcut.intentUri = "https://example.com";

        List<ShortcutRecord> records = LoadShortcutsPojos.includeDefaultLauncher(context,
                Arrays.asList(legacy, userShortcut, legacy));
        assertEquals(2, records.size());
        assertSame(userShortcut, records.get(0));
        assertEquals(context.getString(R.string.default_launcher_title), records.get(1).name);
        assertEquals(ACTION_SET_DEFAULT_LAUNCHER.toString(), records.get(1).intentUri);

        // Reloading an already-populated list must still produce just one switch.
        assertEquals(2, LoadShortcutsPojos.includeDefaultLauncher(context, records).size());
    }
}
