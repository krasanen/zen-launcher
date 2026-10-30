package fi.zmengames.zen;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.os.Looper;

import org.junit.Test;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;

public class LauncherAppWidgetHostTest {
    @Test
    public void emptyLauncherDoesNotCallWidgetServiceOnStartOrStop() {
        getInstrumentation().runOnMainSync(() -> {
            RecordingHost host = new RecordingHost();
            host.startListening();
            host.stopListening();
            host.startListening();
            host.stopListening();
            assertEquals(0, host.starts);
            assertEquals(0, host.stops);
        });
    }

    @Test
    public void restoredWidgetListensOncePerVisibleSession() {
        getInstrumentation().runOnMainSync(() -> {
            RecordingHost host = new RecordingHost();
            host.createWidget(1);
            assertEquals(0, host.starts);
            host.startListening();
            host.startListening();
            assertEquals(1, host.starts);
            host.stopListening();
            host.stopListening();
            assertEquals(1, host.stops);
            host.startListening();
            assertEquals(2, host.starts);
            host.stopListening();
        });
    }

    @Test
    public void firstWidgetAddedWhileVisibleStartsListening() {
        getInstrumentation().runOnMainSync(() -> {
            RecordingHost host = new RecordingHost();
            host.startListening();
            assertEquals(0, host.starts);
            host.createWidget(1);
            assertEquals(1, host.starts);
            host.createWidget(2);
            assertEquals(1, host.starts);
            host.stopListening();
            host.createWidget(3);
            assertEquals(1, host.starts);
            host.startListening();
            assertEquals(2, host.starts);
            host.stopListening();
        });
    }

    @Test
    public void deletingLastWidgetStopsListeningUntilAnotherIsAdded() {
        getInstrumentation().runOnMainSync(() -> {
            RecordingHost host = new RecordingHost();
            // Allocate real IDs under an isolated test host for the deletion calls.
            int first = host.allocateAppWidgetId();
            int second = host.allocateAppWidgetId();
            try {
                host.createWidget(first);
                host.createWidget(second);
                host.startListening();
                host.deleteAppWidgetId(first);
                assertEquals(0, host.stops);
                host.deleteAppWidgetId(second);
                assertEquals(1, host.stops);
                host.stopListening();
                host.startListening();
                assertEquals(1, host.starts);
                host.createWidget(3);
                assertEquals(2, host.starts);
                host.stopListening();
            } finally {
                host.deleteHost();
            }
        });
    }

    private static class RecordingHost extends LauncherAppWidgetHost {
        private final Context context;
        int starts;
        int stops;

        RecordingHost() {
            super(getInstrumentation().getTargetContext(), 1442);
            context = getInstrumentation().getTargetContext();
        }

        void createWidget(int id) {
            onCreateView(context, id, new AppWidgetProviderInfo());
        }

        @Override
        protected void startListeningToService() {
            assertEquals(Looper.getMainLooper(), Looper.myLooper());
            starts++;
        }

        @Override
        protected void stopListeningToService() {
            assertEquals(Looper.getMainLooper(), Looper.myLooper());
            stops++;
        }
    }
}
