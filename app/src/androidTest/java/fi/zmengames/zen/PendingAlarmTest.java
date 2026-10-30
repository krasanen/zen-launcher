package fi.zmengames.zen;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import androidx.test.filters.SdkSuppress;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

public class PendingAlarmTest {
    private Context context;

    @Before
    public void setUp() {
        String prefix = "alarm-test-" + UUID.randomUUID() + "-";
        context = new ContextWrapper(getInstrumentation().getTargetContext()) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(prefix + name, mode);
            }
        };
    }

    @Test
    @SdkSuppress(minSdkVersion = 31)
    public void readyTimerActuallyDeliversItsBroadcast() throws Exception {
        assumeTrue("This check requires exact alarm and Modes access",
                AlarmUtils.requiredAccess(context) == AlarmUtils.RequiredAccess.READY);
        String action = context.getPackageName() + ".TEST_ALARM." + UUID.randomUUID();
        CountDownLatch delivered = new CountDownLatch(1);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                delivered.countDown();
            }
        };
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, new IntentFilter(action), Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, new IntentFilter(action));
        }
        long id = -104;
        Intent request = new Intent(action).setPackage(context.getPackageName());
        request.putExtra(AlarmUtils.ALARM_DELAY_MILLIS, 3000L);
        Calendar target = Calendar.getInstance();
        long started = SystemClock.elapsedRealtime();
        try {
            assertTrue(AlarmUtils.addAlarm(context, request, id, target));
            assertTrue("Android must deliver the scheduled timer", delivered.await(15, TimeUnit.SECONDS));
            assertTrue("Timer must not fire before its requested delay",
                    SystemClock.elapsedRealtime() - started >= 2900L);
        } finally {
            PendingIntent operation = PendingIntent.getBroadcast(context, (int) id & 0xfffffff,
                    request, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
            if (operation != null) {
                ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).cancel(operation);
                operation.cancel();
            }
            AlarmUtils.cancelAlarm(context, id);
            context.unregisterReceiver(receiver);
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 31)
    public void asyncRetrySchedulesWithRealAlarmManagerOnceAccessIsAvailable() throws Exception {
        assumeTrue("This check requires exact alarm and Modes access",
                AlarmUtils.requiredAccess(context) == AlarmUtils.RequiredAccess.READY);
        Calendar target = Calendar.getInstance();
        target.add(Calendar.DAY_OF_YEAR, 1);
        long id = -103;
        Intent request = new Intent(context, AlarmReceiver.class);
        request.putExtra(AlarmActivity.ALARM_ID, id);
        request.putExtra(AlarmActivity.ALARM_EXTRA, "Automated scheduling check");
        try {
            AlarmUtils.savePendingAlarm(context, request, id, target);
            CountDownLatch completed = new CountDownLatch(1);
            AlarmUtils.retryPendingAlarmsAsync(context, completed::countDown);
            assertTrue("Async retry must finish", completed.await(5, TimeUnit.SECONDS));
            assertTrue("The real alarm must be recorded as scheduled", AlarmUtils.getAlarmIds(context).contains(id));
            assertEquals(0, AlarmUtils.retryPendingAlarms(context, (ctx, intent, alarmId, time) -> {
                fail("Successfully scheduled alarm must not remain pending");
                return true;
            }));
        } finally {
            AlarmUtils.cancelAlarm(context, id);
        }
    }

    @After
    public void cleanUp() {
        context.getSharedPreferences("pending_exact_alarms", Context.MODE_PRIVATE).edit().clear().commit();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
    }

    @Test
    public void deniedRequestSurvivesUntilPermissionIsGrantedAndRetriesOnlyOnce() {
        Calendar target = Calendar.getInstance();
        target.add(Calendar.MINUTE, 10);
        long id = target.getTimeInMillis();
        Intent request = new Intent(context, AlarmReceiver.class);
        request.putExtra(AlarmActivity.ALARM_EXTRA, "First alarm after fresh install");
        request.putExtra(AlarmActivity.ALARM_ID, id);
        request.putExtra(AlarmActivity.ALARM_TIME, target.getTime().toString());
        AlarmUtils.savePendingAlarm(context, request, id, target);

        assertEquals(0, AlarmUtils.retryPendingAlarms(context, (ctx, intent, alarmId, time) -> false));
        AtomicInteger scheduled = new AtomicInteger();
        assertEquals(1, AlarmUtils.retryPendingAlarms(context, (ctx, intent, alarmId, time) -> {
            assertEquals(id, alarmId);
            assertEquals(target.getTimeInMillis(), time.getTimeInMillis());
            assertEquals(request.getComponent(), intent.getComponent());
            assertEquals(request.getStringExtra(AlarmActivity.ALARM_EXTRA), intent.getStringExtra(AlarmActivity.ALARM_EXTRA));
            assertEquals(id, intent.getLongExtra(AlarmActivity.ALARM_ID, 0));
            assertEquals(request.getStringExtra(AlarmActivity.ALARM_TIME), intent.getStringExtra(AlarmActivity.ALARM_TIME));
            scheduled.incrementAndGet();
            return true;
        }));
        assertEquals(0, AlarmUtils.retryPendingAlarms(context, (ctx, intent, alarmId, time) -> {
            fail("A broadcast followed by onResume must not schedule twice");
            return true;
        }));
        assertEquals(1, scheduled.get());
    }

    @Test
    public void alarmThatBecomesDueInSettingsIsStillScheduledWithOriginalTime() {
        Calendar target = Calendar.getInstance();
        target.add(Calendar.MINUTE, -1);
        AlarmUtils.savePendingAlarm(context, new Intent(context, AlarmReceiver.class), -101, target);
        assertEquals(1, AlarmUtils.retryPendingAlarms(context, (ctx, intent, id, time) -> {
            assertEquals(target.getTimeInMillis(), time.getTimeInMillis());
            return true;
        }));
    }

    @Test
    public void cancelledPendingAlarmIsNotResurrectedAfterGrant() {
        Calendar target = Calendar.getInstance();
        AlarmUtils.savePendingAlarm(context, new Intent(context, AlarmReceiver.class), -101, target);
        AlarmUtils.cancelAlarm(context, -101);
        assertEquals(0, AlarmUtils.retryPendingAlarms(context, (ctx, intent, id, time) -> {
            fail("Cancelled request must not be scheduled");
            return true;
        }));
    }

    @Test
    public void cancelAllAlsoClearsRequestsWaitingForPermission() {
        Calendar target = Calendar.getInstance();
        AlarmUtils.savePendingAlarm(context, new Intent(context, AlarmReceiver.class), -101, target);
        AlarmUtils.savePendingAlarm(context, new Intent(context, AlarmReceiver.class), -102, target);
        AlarmUtils.cancelAllAlarms(context);
        assertEquals(0, AlarmUtils.retryPendingAlarms(context, (ctx, intent, id, time) -> true));
    }
}
