package fr.neamar.kiss.androidTest;

import android.content.Context;
import android.os.StrictMode;
import android.os.SystemClock;

import org.junit.Test;

import java.util.Map;
import java.util.UUID;

import fr.neamar.kiss.db.DBHelper;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class BadgePersistenceTest {
    @Test
    public void badgeWritesAvoidMainThreadIoAndPreserveOrder() {
        Context context = getInstrumentation().getTargetContext();
        String packageName = "badge-test-" + UUID.randomUUID();
        String marker = packageName + "-marker";
        try {
            getInstrumentation().runOnMainSync(() -> {
                StrictMode.ThreadPolicy previous = StrictMode.getThreadPolicy();
                StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                        .detectDiskReads().detectDiskWrites().penaltyDeath().build());
                try {
                    DBHelper.setBadgeCount(context, packageName, 1);
                    DBHelper.setBadgeCount(context, packageName, 0);
                    DBHelper.setBadgeCount(context, packageName, 7);
                    DBHelper.setBadgeCount(context, marker, 1);
                } finally {
                    StrictMode.setThreadPolicy(previous);
                }
            });
            // The marker must persist after every preceding update, including a clear.
            awaitCount(context, marker, 1);
            assertEquals(Integer.valueOf(7), DBHelper.loadBadges(context).get(packageName));

            DBHelper.setBadgeCount(context, packageName, 0);
            DBHelper.setBadgeCount(context, marker, 2);
            awaitCount(context, marker, 2);
            assertFalse(DBHelper.loadBadges(context).containsKey(packageName));
        } finally {
            DBHelper.setBadgeCount(context, packageName, 0);
            DBHelper.setBadgeCount(context, marker, 0);
        }
    }

    private static void awaitCount(Context context, String packageName, int count) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            Map<String, Integer> badges = DBHelper.loadBadges(context);
            if (Integer.valueOf(count).equals(badges.get(packageName))) {
                return;
            }
            SystemClock.sleep(20);
        }
        fail("Badge write did not complete: " + packageName);
    }
}
