package fr.neamar.kiss.result;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;

import org.junit.Test;

import fr.neamar.kiss.pojo.ShortcutPojo;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static fi.zmengames.zen.ZEvent.State.ACTION_SET_DEFAULT_LAUNCHER;
import static org.junit.Assert.assertNotNull;

public class ShortcutsResultTest {
    @Test
    public void internalLauncherActionDoesNotLookUpSyntheticZenPackage() {
        Context context = new ContextWrapper(getInstrumentation().getTargetContext()) {
            @Override
            public PackageManager getPackageManager() {
                throw new AssertionError("Internal shortcut must use the launcher's own icon");
            }
        };
        ShortcutPojo shortcut = new ShortcutPojo("shortcut://change default launcher", "zen",
                ACTION_SET_DEFAULT_LAUNCHER.toString());
        assertNotNull(new ShortcutsResult(shortcut).loadAppDrawable(context));
    }

    @Test
    public void removedShortcutPackageUsesFallbackIcon() {
        Context context = getInstrumentation().getTargetContext();
        String missingPackage = context.getPackageName() + ".missing.test.package";
        Intent intent = new Intent(Intent.ACTION_MAIN).setPackage(missingPackage);
        ShortcutPojo shortcut = new ShortcutPojo("shortcut://missing", missingPackage,
                intent.toUri(Intent.URI_INTENT_SCHEME));
        assertNotNull(new ShortcutsResult(shortcut).loadAppDrawable(context));
    }
}
