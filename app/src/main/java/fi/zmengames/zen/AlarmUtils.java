package fi.zmengames.zen;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

import fr.neamar.kiss.BuildConfig;
import fr.neamar.kiss.R;

public class AlarmUtils {

    private static final String sTagAlarms = ":zenalarms";
    private static final String TAG = AlarmUtils.class.getSimpleName();
    private static final String PENDING_ALARMS = "pending_exact_alarms";
    private static final ExecutorService alarmExecutor = Executors.newSingleThreadExecutor();
    public static final String ALARM_DELAY_MILLIS = "alarm_delay_millis";

    enum RequiredAccess { EXACT_ALARMS, MODES, READY }

    static RequiredAccess requiredAccess(boolean exactAlarms, boolean modes) {
        if (!exactAlarms) return RequiredAccess.EXACT_ALARMS;
        if (!modes) return RequiredAccess.MODES;
        return RequiredAccess.READY;
    }

    static RequiredAccess requiredAccess(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        NotificationManager notifications = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return requiredAccess(Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms(),
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M || notifications.isNotificationPolicyAccessGranted());
    }

    static long triggerTime(Intent intent, long requestedTime, long now) {
        long delay = intent.getLongExtra(ALARM_DELAY_MILLIS, 0);
        return delay > 0 ? now + delay : requestedTime;
    }

    public static synchronized boolean addAlarm(Context context, Intent intent, long notificationId, Calendar calendar) {
        if (BuildConfig.DEBUG) Log.d(TAG,"addAlarm: "+notificationId);
        // Persist before opening permission settings, which can outlive this process.
        savePendingAlarm(context, intent, notificationId, calendar);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        boolean scheduled = tryScheduleExactAlarm(
                () -> requiredAccess(context) == RequiredAccess.READY,
                () -> {
                    long trigger = triggerTime(intent, calendar.getTimeInMillis(), System.currentTimeMillis());
                    Calendar actualTime = Calendar.getInstance();
                    actualTime.setTimeInMillis(trigger);
                    intent.putExtra(AlarmActivity.ALARM_TIME, actualTime.getTime().toString());
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, (int) notificationId & 0xfffffff, intent,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent);
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, trigger, pendingIntent);
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, trigger, pendingIntent);
                    }
                });
        if (scheduled) {
            saveAlarmId(context, notificationId, intent);
            pendingAlarms(context).edit().remove(Long.toString(notificationId)).apply();
        }
        return scheduled;
    }

    private static SharedPreferences pendingAlarms(Context context) {
        return context.getSharedPreferences(PENDING_ALARMS, Context.MODE_PRIVATE);
    }

    static synchronized void savePendingAlarm(Context context, Intent intent, long id, Calendar target) {
        try {
            JSONObject record = new JSONObject();
            record.put("triggerAtMillis", target.getTimeInMillis());
            record.put("intent", intent.toUri(Intent.URI_INTENT_SCHEME));
            pendingAlarms(context).edit().putString(Long.toString(id), record.toString()).apply();
        } catch (org.json.JSONException e) {
            throw new IllegalArgumentException("Could not retain alarm request", e);
        }
    }

    public static void retryPendingAlarmsAsync(Context context) {
        retryPendingAlarmsAsync(context, () -> {});
    }

    static void retryPendingAlarmsAsync(Context context, Runnable completion) {
        Context appContext = context.getApplicationContext();
        alarmExecutor.execute(() -> {
            try {
                int scheduled = retryPendingAlarms(appContext, AlarmUtils::addAlarm);
                if (scheduled > 0) {
                    EventBus.getDefault().post(new ZEvent(ZEvent.State.SHOW_TOAST,
                            appContext.getString(R.string.alarm_pending_scheduled)));
                }
            } finally {
                completion.run();
            }
        });
    }

    interface AlarmScheduler {
        boolean schedule(Context context, Intent intent, long id, Calendar target);
    }

    static synchronized int retryPendingAlarms(Context context, AlarmScheduler scheduler) {
        int scheduled = 0;
        for (Map.Entry<String, ?> entry : pendingAlarms(context).getAll().entrySet()) {
            long id;
            Intent intent;
            Calendar target = Calendar.getInstance();
            try {
                id = Long.parseLong(entry.getKey());
                JSONObject record = new JSONObject((String) entry.getValue());
                target.setTimeInMillis(record.getLong("triggerAtMillis"));
                intent = Intent.parseUri(record.getString("intent"), Intent.URI_INTENT_SCHEME);
            } catch (Exception e) {
                Log.w(TAG, "Discarding unreadable pending alarm", e);
                pendingAlarms(context).edit().remove(entry.getKey()).apply();
                continue;
            }
            // A past trigger is intentionally retained: Android delivers it as
            // soon as possible if permission was granted after the requested time.
            if (scheduler.schedule(context, intent, id, target)) {
                pendingAlarms(context).edit().remove(entry.getKey()).apply();
                scheduled++;
            }
        }
        return scheduled;
    }

    static boolean tryScheduleExactAlarm(BooleanSupplier canSchedule, Runnable schedule) {
        try {
            if (!canSchedule.getAsBoolean()) {
                return false;
            }
            schedule.run();
            return true;
        } catch (SecurityException e) {
            // Defend against a denial from the service even after the preflight
            // check. Keep the request pending without marking it as scheduled.
            Log.w(TAG, "Exact alarm scheduling was denied", e);
            return false;
        }
    }

    public static synchronized void cancelAlarm(Context context, long notificationId) {
        if (BuildConfig.DEBUG) Log.d(TAG,"cancelAlarm: "+notificationId);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent myIntent = new Intent(context.getApplicationContext(), AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, (int) notificationId & 0xfffffff, myIntent, PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
        pendingAlarms(context).edit().remove(Long.toString(notificationId)).apply();
        removeAlarmId(context, notificationId);

        /*
        try {
            executeBadge(context, context.getPackageName(), "AlarmUtils", getAlarmIds(context).size());
        } catch (ShortcutBadgeException e) {
            e.printStackTrace();
        }*/
    }

    public static synchronized void cancelAllAlarms(Context context) {
        pendingAlarms(context).edit().clear().apply();
        for (long idAlarm : getAlarmIds(context)) {
            cancelAlarm(context, idAlarm);
        }
        EventBus.getDefault().post(new ZEvent(ZEvent.State.SHOW_TOAST, "All Zen Alarms cancelled!"));
    }

    public static boolean hasAlarm(Context context, Intent intent, int notificationId) {
        return PendingIntent.getBroadcast(context, notificationId, intent, PendingIntent.FLAG_NO_CREATE) != null;
    }

    private static void saveAlarmId(Context context, long id, Intent intent) {
        List<Long> idsAlarms = getAlarmIds(context);

        if (idsAlarms.contains(id)) {
            return;
        }

        idsAlarms.add(id);

        saveIdsInPreferences(context, idsAlarms);
    }

    private static void removeAlarmId(Context context, long id) {
        List<Long> idsAlarms = getAlarmIds(context);

        for (int i = 0; i < idsAlarms.size(); i++) {
            if (idsAlarms.get(i) == id)
                idsAlarms.remove(i);
        }

        saveIdsInPreferences(context, idsAlarms);
    }

    public static List<Long> getAlarmIds(Context context) {
        List<Long> ids = new ArrayList<>();
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            JSONArray jsonArray2 = new JSONArray(prefs.getString(context.getPackageName() + sTagAlarms, "[]"));

            for (int i = 0; i < jsonArray2.length(); i++) {
                ids.add(jsonArray2.getLong(i));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return ids;
    }

    private static void saveIdsInPreferences(Context context, List<Long> lstIds) {
        JSONArray jsonArray = new JSONArray();
        for (Long idAlarm : lstIds) {
            jsonArray.put(idAlarm);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(context.getPackageName() + sTagAlarms, jsonArray.toString());

        editor.apply();
    }
}
