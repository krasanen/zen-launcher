package fi.zmengames.zen;


/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

import fr.neamar.kiss.BuildConfig;

/**
 * Specific {@link AppWidgetHost} that creates our {@link LauncherAppWidgetHostView}
 * which correctly captures all long-press events. This ensures that users can
 * always pick up and move widgets.
 */
public class LauncherAppWidgetHost extends AppWidgetHost {
    private static final String TAG = LauncherAppWidgetHost.class.getSimpleName();
    private final Set<Integer> hostedWidgetIds = new HashSet<>();
    private boolean activityStarted;
    private boolean listening;
    public LauncherAppWidgetHost(Context context, int hostId) {
        super(context, hostId);
    }

    @Override
    protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
                                             AppWidgetProviderInfo appWidget) {
        LauncherAppWidgetHostView view = new LauncherAppWidgetHostView(context);
        hostedWidgetIds.add(appWidgetId);
        // A widget can be added after onStart(), when we skipped listening
        // because the launcher had no widgets yet.
        startListeningIfNeeded();
        return view;
    }

    @Override
    public void startListening() {
        activityStarted = true;
        startListeningIfNeeded();
    }

    private void startListeningIfNeeded() {
        // startListening performs a synchronous Binder call. Avoid it entirely
        // on the common startup path where there are no widget views to update.
        if (!activityStarted || hostedWidgetIds.isEmpty() || listening) {
            return;
        }
        // If applying a pending update throws after registration, onStop must
        // still unregister the host from the service.
        listening = true;
        startListeningToService();
    }

    protected void startListeningToService() {
        super.startListening();
    }

    @Override
    public void stopListening() {
        activityStarted = false;
        stopListeningIfNeeded();
    }

    private void stopListeningIfNeeded() {
        if (!listening) {
            return;
        }
        stopListeningToService();
        listening = false;
    }

    protected void stopListeningToService() {
        super.stopListening();
    }

    @Override
    public void deleteAppWidgetId(int appWidgetId) {
        super.deleteAppWidgetId(appWidgetId);
        hostedWidgetIds.remove(appWidgetId);
        if (hostedWidgetIds.isEmpty()) {
            stopListeningIfNeeded();
        }
    }
    @Override
    protected void onProviderChanged(int appWidgetId, AppWidgetProviderInfo appWidget) {
        super.onProviderChanged(appWidgetId, appWidget);
        if(BuildConfig.DEBUG) Log.i(TAG,"onProviderChanged:"+appWidgetId);

    }
}
