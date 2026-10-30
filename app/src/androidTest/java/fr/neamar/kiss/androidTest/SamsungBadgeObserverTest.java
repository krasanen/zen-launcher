package fr.neamar.kiss.androidTest;

import android.os.Handler;
import android.os.Looper;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import fr.neamar.kiss.SamsungBadgeObserver;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SamsungBadgeObserverTest {
    @Test
    public void slowProviderDoesNotBlockMainThreadOrLeakRegistrationAfterPause() throws Exception {
        RecordingObserver observer = new RecordingObserver();
        try {
            getInstrumentation().runOnMainSync(observer::startObserving);
            assertTrue(observer.queryStarted.await(5, TimeUnit.SECONDS));
            // Pause and other UI work must run even while the provider is stalled.
            getInstrumentation().runOnMainSync(observer::stopObserving);
            assertEquals(0, observer.registrations.get());
            observer.releaseQuery.countDown();
            assertTrue(observer.stopped.await(5, TimeUnit.SECONDS));
            assertEquals(1, observer.registrations.get());
            assertEquals(1, observer.refreshes.get());
            assertEquals(1, observer.unregistrations.get());
            assertFalse(observer.providerWorkOnMain.get());
        } finally {
            observer.releaseQuery.countDown();
            observer.stopObserving();
        }
    }

    @Test
    public void repeatedResumeRegistersOnlyOnceAndPauseCleansUp() throws Exception {
        RecordingObserver observer = new RecordingObserver();
        observer.releaseQuery.countDown();
        try {
            getInstrumentation().runOnMainSync(() -> {
                observer.startObserving();
                observer.startObserving();
                observer.stopObserving();
                observer.stopObserving();
            });
            assertTrue(observer.stopped.await(5, TimeUnit.SECONDS));
            assertEquals(1, observer.registrations.get());
            assertEquals(2, observer.refreshes.get());
            assertEquals(1, observer.unregistrations.get());
            assertFalse(observer.providerWorkOnMain.get());
        } finally {
            observer.stopObserving();
        }
    }

    private static class RecordingObserver extends SamsungBadgeObserver {
        final CountDownLatch queryStarted = new CountDownLatch(1);
        final CountDownLatch releaseQuery = new CountDownLatch(1);
        final CountDownLatch stopped = new CountDownLatch(1);
        final AtomicBoolean providerWorkOnMain = new AtomicBoolean();
        final AtomicInteger registrations = new AtomicInteger();
        final AtomicInteger refreshes = new AtomicInteger();
        final AtomicInteger unregistrations = new AtomicInteger();

        RecordingObserver() {
            super(new Handler(Looper.getMainLooper()), getInstrumentation().getTargetContext());
        }

        private void recordThread() {
            if (Looper.myLooper() == Looper.getMainLooper()) providerWorkOnMain.set(true);
        }

        @Override
        protected boolean providerAvailable() {
            recordThread();
            queryStarted.countDown();
            try {
                return releaseQuery.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        @Override
        protected void registerObserver() {
            recordThread();
            registrations.incrementAndGet();
        }

        @Override
        protected void refreshBadges() {
            recordThread();
            refreshes.incrementAndGet();
        }

        @Override
        protected void unregisterObserver() {
            recordThread();
            unregistrations.incrementAndGet();
            stopped.countDown();
        }
    }
}
