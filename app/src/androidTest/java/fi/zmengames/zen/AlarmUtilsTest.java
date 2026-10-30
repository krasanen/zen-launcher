package fi.zmengames.zen;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;

import androidx.test.filters.SdkSuppress;

import org.junit.Test;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

public class AlarmUtilsTest {
    @Test
    public void setupRequiresExactAccessThenModesAccess() {
        assertEquals(AlarmUtils.RequiredAccess.EXACT_ALARMS, AlarmUtils.requiredAccess(false, false));
        assertEquals(AlarmUtils.RequiredAccess.EXACT_ALARMS, AlarmUtils.requiredAccess(false, true));
        assertEquals(AlarmUtils.RequiredAccess.MODES, AlarmUtils.requiredAccess(true, false));
        assertEquals(AlarmUtils.RequiredAccess.READY, AlarmUtils.requiredAccess(true, true));
    }

    @Test
    public void oneMinuteTimerStartsWithFullMinuteAfterSetup() {
        Intent request = new Intent();
        request.putExtra(AlarmUtils.ALARM_DELAY_MILLIS, 60000L);
        long nowAfterSetup = 1780000112345L;
        assertEquals(nowAfterSetup + 60000L, AlarmUtils.triggerTime(request, 1780000000000L, nowAfterSetup));
    }

    @Test
    public void absoluteAlarmKeepsSelectedTimeAfterSetup() {
        assertEquals(1780000000000L, AlarmUtils.triggerTime(new Intent(), 1780000000000L, 1780000112345L));
    }

    @Test
    public void missingPermissionDoesNotAttemptScheduling() {
        boolean scheduled = AlarmUtils.tryScheduleExactAlarm(() -> false,
                () -> fail("Must not schedule without exact alarm access"));
        assertFalse(scheduled);
    }

    @Test
    public void grantedPermissionSchedulesExactlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        assertTrue(AlarmUtils.tryScheduleExactAlarm(() -> true, calls::incrementAndGet));
        assertEquals(1, calls.get());
    }

    @Test
    public void serviceDenialAfterSuccessfulCheckDoesNotCrash() {
        assertFalse(AlarmUtils.tryScheduleExactAlarm(() -> true, () -> {
            throw new SecurityException("Exact alarms denied by service");
        }));
    }

    @Test
    @SdkSuppress(minSdkVersion = 31)
    public void deniedAlarmIsNotSavedOnDevice() {
        Context context = getInstrumentation().getTargetContext();
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        assumeFalse("This check requires a device without exact alarm access", manager.canScheduleExactAlarms());
        List<Long> before = AlarmUtils.getAlarmIds(context);
        Calendar target = Calendar.getInstance();
        target.add(Calendar.DAY_OF_YEAR, 1);
        Intent intent = new Intent(context, AlarmReceiver.class);

        try {
            assertFalse(AlarmUtils.addAlarm(context, intent, target.getTimeInMillis(), target));
            assertEquals("Denied alarms must not appear as scheduled", before, AlarmUtils.getAlarmIds(context));
        } finally {
            AlarmUtils.cancelAlarm(context, target.getTimeInMillis());
        }
    }
}
