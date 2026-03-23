package com.leclowndu93150.log_deduplicator.core;

import com.leclowndu93150.log_deduplicator.Config;
import com.leclowndu93150.log_deduplicator.TestConfigHelper;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DeduplicationManagerTest {

    @BeforeEach
    void setUp() {
        TestConfigHelper.setDefaults();
        DeduplicationManager.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        DeduplicationManager.resetForTesting();
    }

    // ---- Regression: bug #1 — null level used to NPE ----

    @Test
    void nullLevelDoesNotNPE() {
        // Before fix: level.intLevel() threw NPE when level was null
        assertDoesNotThrow(() ->
            DeduplicationManager.getInstance().check("msg", "test", null));
        assertTrue(DeduplicationManager.getInstance().check("msg", "test", null).isAllowed());
    }

    // ---- Regression: bug #9 — eager singleton read Config before init ----

    @Test
    void lazyInitDoesNotReadConfigAtClassLoad() {
        // Before fix: DeduplicationManager.INSTANCE was created in a static final field,
        // reading Config.maxBufferSize (which was 0 before Config.init).
        // After fix: getInstance() uses lazy DCL, so it only creates the instance on first call.
        // This test verifies the instance works properly after Config is set up.
        Config.maxBufferSize = 200;
        DeduplicationManager.resetForTesting();
        var mgr = DeduplicationManager.getInstance();
        // Should be able to process messages without issues — context was created with 200, not 0
        for (int i = 0; i < 100; i++) {
            mgr.check("msg-" + i, "test", Level.INFO);
        }
        assertEquals(100, mgr.getTotalProcessed());
    }

    // ---- Regression: locale fix — format must use dot not comma ----

    @Test
    void formatWithCountUsesDecimalPoint() {
        // Before fix: String.format used system locale, so "5.0s" became "5,0s" in e.g. French
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            String result = DeduplicationManager.formatWithCount("test", 10, 5000);
            assertTrue(result.contains("5.0s"), "Expected dot decimal, got: " + result);
            assertFalse(result.contains("5,0s"), "Comma decimal should never appear: " + result);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void formatWithCountMathIsCorrect() {
        // count=10 should display "9 times" (count-1)
        String r = DeduplicationManager.formatWithCount("x", 10, 5000);
        assertEquals("x [suppressed 9 times over 5.0s]", r);
    }

    // ---- Core behavior ----

    @Test
    void disabledSkipsEverything() {
        Config.enabled = false;
        var mgr = DeduplicationManager.getInstance();
        mgr.check("msg", "test", Level.INFO);
        assertEquals(0, mgr.getTotalProcessed(), "Disabled should not count as processed");
    }

    @Test
    void debugModeSkipsDedup() {
        Config.debugMode = true;
        var mgr = DeduplicationManager.getInstance();
        for (int i = 0; i < 100; i++) {
            assertTrue(mgr.check("same msg", "test", Level.INFO).isAllowed());
        }
    }

    @Test
    void blacklistDeniesImmediately() {
        Config.blacklistLoggers = List.of("spammy");
        var r = DeduplicationManager.getInstance().check("anything", "spammy", Level.INFO);
        assertFalse(r.isAllowed());
        // Should not count toward processed since it's denied before dedup
        assertEquals(0, DeduplicationManager.getInstance().getTotalProcessed());
    }

    @Test
    void blacklistWithNullLoggerDoesNotCrash() {
        Config.blacklistLoggers = List.of("spammy");
        assertTrue(DeduplicationManager.getInstance().check("msg", null, Level.INFO).isAllowed());
    }

    @Test
    void whitelistSkipsDedupForUnlistedLoggers() {
        Config.whitelistLoggers = List.of("target");
        // Non-whitelisted logger passes through without dedup
        var r = DeduplicationManager.getInstance().check("msg", "other", Level.INFO);
        assertTrue(r.isAllowed());
        assertEquals(0, DeduplicationManager.getInstance().getTotalProcessed());
    }

    @Test
    void suppressesAfterThreshold() {
        var mgr = DeduplicationManager.getInstance();
        // INFO threshold = 20
        for (int i = 0; i < 20; i++) {
            assertTrue(mgr.check("repeated", "test", Level.INFO).isAllowed());
        }
        assertFalse(mgr.check("repeated", "test", Level.INFO).isAllowed());
        assertTrue(mgr.getSuppressedCount() > 0);
    }

    @Test
    void suppressionRateCalculation() {
        var mgr = DeduplicationManager.getInstance();
        assertEquals(0.0, mgr.getSuppressionRate());
        for (int i = 0; i < 30; i++) {
            mgr.check("same", "test", Level.INFO);
        }
        double rate = mgr.getSuppressionRate();
        assertTrue(rate > 0 && rate < 100);
    }

    @Test
    void suppressionLoggerIdentified() {
        assertTrue(DeduplicationManager.isSuppressionLogger("log_deduplicator.suppression"));
        assertFalse(DeduplicationManager.isSuppressionLogger("other"));
        assertFalse(DeduplicationManager.isSuppressionLogger(null));
    }

    // ---- Threshold per level ----

    @Test
    void thresholdsMapToCorrectConfigValues() {
        Config.errorThreshold = 3;
        Config.warnThreshold = 7;
        Config.infoThreshold = 15;
        Config.debugThreshold = 2;
        assertEquals(3, DeduplicationManager.getThresholdForLevel(Level.ERROR));
        assertEquals(3, DeduplicationManager.getThresholdForLevel(Level.FATAL));
        assertEquals(7, DeduplicationManager.getThresholdForLevel(Level.WARN));
        assertEquals(15, DeduplicationManager.getThresholdForLevel(Level.INFO));
        assertEquals(2, DeduplicationManager.getThresholdForLevel(Level.DEBUG));
        assertEquals(2, DeduplicationManager.getThresholdForLevel(Level.TRACE));
    }

    // ---- Concurrency ----

    @Nested
    class Concurrency {

        @Test
        void concurrentChecksProduceConsistentStats() throws Exception {
            int threads = 8;
            int perThread = 500;
            var mgr = DeduplicationManager.getInstance();
            var latch = new CountDownLatch(1);
            var executor = Executors.newFixedThreadPool(threads);
            var errors = new AtomicReference<Throwable>();

            for (int t = 0; t < threads; t++) {
                final int tid = t;
                executor.submit(() -> {
                    try {
                        latch.await();
                        for (int i = 0; i < perThread; i++) {
                            mgr.check("thread-" + tid + "-msg-" + i, "test", Level.INFO);
                        }
                    } catch (Throwable e) {
                        errors.compareAndSet(null, e);
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            assertNull(errors.get(), () -> "Thread threw: " + errors.get());

            // total processed should be exactly threads * perThread
            // because each message is unique (thread-X-msg-Y)
            assertEquals(threads * perThread, mgr.getTotalProcessed());
        }

        @Test
        void concurrentSameMessageDoesNotCorrupt() throws Exception {
            int threads = 8;
            int perThread = 200;
            var mgr = DeduplicationManager.getInstance();
            var latch = new CountDownLatch(1);
            var executor = Executors.newFixedThreadPool(threads);
            var allowed = new AtomicInteger();
            var denied = new AtomicInteger();
            var errors = new AtomicReference<Throwable>();

            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        latch.await();
                        for (int i = 0; i < perThread; i++) {
                            var r = mgr.check("identical message", "test", Level.INFO);
                            if (r.isAllowed()) allowed.incrementAndGet();
                            else denied.incrementAndGet();
                        }
                    } catch (Throwable e) {
                        errors.compareAndSet(null, e);
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            assertNull(errors.get(), () -> "Thread threw: " + errors.get());

            int total = allowed.get() + denied.get();
            assertEquals(threads * perThread, total, "Every check should produce a result");
            assertTrue(allowed.get() >= Config.infoThreshold, "At least threshold should be allowed");
            assertTrue(denied.get() > 0, "Some should be denied");
        }
    }
}
