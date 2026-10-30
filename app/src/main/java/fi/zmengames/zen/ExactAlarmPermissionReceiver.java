package fi.zmengames.zen;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Resumes saved alarm requests even if the launcher was killed in Settings. */
public class ExactAlarmPermissionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(intent.getAction())) {
            PendingResult result = goAsync();
            AlarmUtils.retryPendingAlarmsAsync(context, result::finish);
        }
    }
}
