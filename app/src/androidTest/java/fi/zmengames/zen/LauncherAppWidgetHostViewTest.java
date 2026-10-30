package fi.zmengames.zen;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import androidx.test.filters.SdkSuppress;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import fr.neamar.kiss.R;
import fr.neamar.kiss.ZenWidget;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@SdkSuppress(minSdkVersion = 26)
public class LauncherAppWidgetHostViewTest {
    @Test
    public void widgetInflationRunsOffMainThreadAndAppliesOnMainThread() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        AtomicReference<GatedHostView> reference = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> reference.set(new GatedHostView(context)));
        GatedHostView host = reference.get();
        try {
            AppWidgetProviderInfo provider = null;
            for (AppWidgetProviderInfo info : AppWidgetManager.getInstance(context).getInstalledProviders()) {
                if (info.provider.getPackageName().equals(context.getPackageName())
                        && info.provider.getClassName().equals(ZenWidget.class.getName())) {
                    provider = info;
                    break;
                }
            }
            assertNotNull("The debug app's widget provider must be installed", provider);
            AppWidgetProviderInfo widgetInfo = provider;
            getInstrumentation().runOnMainSync(() -> {
                host.setAppWidget(1, widgetInfo);
                host.updateAppWidget(widget(context, "First update"));
            });

            assertTrue("Inflation must use the configured executor",
                    host.workerStarted.await(5, TimeUnit.SECONDS));
            assertFalse("Inflation must not run on the main thread", host.workerWasMain.get());
            // The main thread can process another task while inflation is blocked.
            getInstrumentation().runOnMainSync(() -> assertFalse(host.applied.get()));
            host.releaseWorker.countDown();
            awaitDescription(host, "First update");
            assertTrue("The widget view must be attached on the main thread", host.appliedOnMain.get());

            getInstrumentation().runOnMainSync(() -> host.updateAppWidget(widget(context, "Second update")));
            awaitDescription(host, "Second update");
        } finally {
            host.releaseWorker.countDown();
        }
    }

    private static RemoteViews widget(Context context, String description) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.zen_widget);
        views.setContentDescription(R.id.zenwidgetbutton, description);
        return views;
    }

    private static void awaitDescription(GatedHostView host, String description) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        AtomicBoolean matched = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline) {
            getInstrumentation().runOnMainSync(() -> {
                View button = host.findViewById(R.id.zenwidgetbutton);
                matched.set(button != null && description.contentEquals(button.getContentDescription()));
            });
            if (matched.get()) {
                return;
            }
            SystemClock.sleep(20);
        }
        fail("Widget update did not render: " + description);
    }

    private static class GatedHostView extends LauncherAppWidgetHostView {
        final CountDownLatch workerStarted = new CountDownLatch(1);
        final CountDownLatch releaseWorker = new CountDownLatch(1);
        final AtomicBoolean workerWasMain = new AtomicBoolean();
        final AtomicBoolean applied = new AtomicBoolean();
        final AtomicBoolean appliedOnMain = new AtomicBoolean();

        GatedHostView(Context context) {
            super(context);
        }

        @Override
        public void setExecutor(Executor executor) {
            // Wrap the executor configured by the production constructor, leaving
            // the real framework inflation and application paths in use.
            super.setExecutor(command -> executor.execute(() -> {
                workerWasMain.set(Looper.myLooper() == Looper.getMainLooper());
                workerStarted.countDown();
                try {
                    if (!releaseWorker.await(5, TimeUnit.SECONDS)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                command.run();
            }));
        }

        @Override
        public void onViewAdded(View child) {
            super.onViewAdded(child);
            appliedOnMain.set(Looper.myLooper() == Looper.getMainLooper());
            applied.set(true);
        }
    }
}
