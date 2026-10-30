package fi.zmengames.zen;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import fr.neamar.kiss.R;

/** Requests alarm prerequisites one at a time, retaining requests in AlarmUtils. */
public class AlarmSetupActivity extends Activity {
    private String requestedAccess;
    private boolean scheduling;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) requestedAccess = state.getString("requestedAccess");
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("requestedAccess", requestedAccess);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scheduling) return;
        AlarmUtils.RequiredAccess access = AlarmUtils.requiredAccess(this);
        if (access == AlarmUtils.RequiredAccess.READY) {
            scheduling = true;
            AlarmUtils.retryPendingAlarmsAsync(this, () -> runOnUiThread(this::finish));
            return;
        }
        // Returning without granting access must not reopen the same screen.
        if (access.name().equals(requestedAccess)) {
            Toast.makeText(this, R.string.alarm_setup_pending, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        requestedAccess = access.name();
        Intent settings;
        if (access == AlarmUtils.RequiredAccess.EXACT_ALARMS) {
            Toast.makeText(this, R.string.alarm_exact_permission_required, Toast.LENGTH_LONG).show();
            settings = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
        } else {
            Toast.makeText(this, R.string.alarm_modes_permission_required, Toast.LENGTH_LONG).show();
            settings = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        }
        try {
            startActivity(settings);
        } catch (RuntimeException e) {
            Log.w("AlarmSetupActivity", "Could not open alarm access settings", e);
            Toast.makeText(this, R.string.alarm_setup_pending, Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
