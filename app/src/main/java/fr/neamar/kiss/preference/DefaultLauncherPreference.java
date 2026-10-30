package fr.neamar.kiss.preference;

import android.app.Activity;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.preference.DialogPreference;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import fr.neamar.kiss.R;

/**
 * A Dialog Preference that allows the User to change the default launcher
 */
public class DefaultLauncherPreference extends DialogPreference {

    public interface AsyncTaskCompleteListener {
        void onTaskComplete(boolean result, Activity activity);
    }

    public static class ResolveDefaultLauncherTask extends AsyncTask<Activity, Void, Boolean> {
        private Activity activity;
        private AsyncTaskCompleteListener listener;

        public ResolveDefaultLauncherTask(AsyncTaskCompleteListener listener, Activity activity) {
            this.listener = listener;
            this.activity = activity;
        }

        @Override
        protected Boolean doInBackground(Activity... params) {
            if (params.length > 0) {
                activity = params[0];
            }
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            final ResolveInfo res = activity.getPackageManager().resolveActivity(intent, 0);
            return res != null && res.activityInfo != null && activity.getPackageName()
                    .equals(res.activityInfo.packageName);
        }

        @Override
        protected void onPostExecute(Boolean isDefault) {
            if (listener != null) {
                listener.onTaskComplete(isDefault, activity);
            }
        }
    }

    public DefaultLauncherPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    private static final String TAG = DefaultLauncherPreference.class.getSimpleName();

    public static void selectLauncher(Context context){
        // ACTION_MAIN/CATEGORY_HOME opens the existing default launcher. Use
        // Settings to let the user choose a home app on Pixels and other devices.
        for (String action : new String[]{Settings.ACTION_HOME_SETTINGS,
                Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_SETTINGS}) {
            Intent intent = new Intent(action);
            if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.w(TAG, "Could not open " + action, e);
            }
        }
        Toast.makeText(context, R.string.error_setting_default, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        super.onClick(dialog, which);

        if (which == DialogInterface.BUTTON_POSITIVE) {
            selectLauncher(getContext());
        }
    }
}
