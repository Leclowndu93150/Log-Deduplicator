package com.leclowndu93150.log_deduplicator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    void initCreatesConfigFile(@TempDir Path tempDir) {
        Config.init(tempDir);
        assertTrue(Files.exists(tempDir.resolve("log_deduplicator.toml")));
    }

    @Test
    void initSetsDefaults(@TempDir Path tempDir) {
        Config.init(tempDir);
        assertTrue(Config.enabled);
        assertFalse(Config.debugMode);
        assertEquals(5, Config.errorThreshold);
        assertEquals(20, Config.infoThreshold);
        assertEquals(2000, Config.generalWindowMs);
        assertEquals(500, Config.maxBufferSize);
        assertTrue(Config.normalizeCoordinates);
        assertTrue(Config.showSuppressionMessage);
    }

    @Test
    void initReadsExistingValues(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("log_deduplicator.toml"),
            "enabled = false\ndebug_mode = true\n[thresholds]\nerror = 99\n");
        Config.init(tempDir);
        assertFalse(Config.enabled);
        assertTrue(Config.debugMode);
        assertEquals(99, Config.errorThreshold);
        assertEquals(10, Config.warnThreshold); // not in file → default
    }

    // ---- Regression: thread safety fix #7 — lists must be immutable ----

    @Test
    void listsAreUnmodifiable(@TempDir Path tempDir) {
        Config.init(tempDir);
        assertThrows(UnsupportedOperationException.class, () -> Config.whitelistLoggers.add("x"));
        assertThrows(UnsupportedOperationException.class, () -> Config.blacklistLoggers.add("x"));
    }

    // ---- Regression: thread safety fix #7 — fields must be volatile ----

    @Test
    void criticalFieldsAreVolatile() throws Exception {
        for (String name : new String[]{"enabled", "debugMode", "errorThreshold",
                "generalWindowMs", "whitelistLoggers", "blacklistLoggers",
                "fuzzyMatching", "systemStreamsEnabled", "showSuppressionMessage"}) {
            var field = Config.class.getDeclaredField(name);
            assertTrue(java.lang.reflect.Modifier.isVolatile(field.getModifiers()),
                name + " should be volatile");
        }
    }

    // ---- Concurrent reads don't crash during simulated reload ----

    @Test
    void concurrentReadsDoNotSeeCorruption(@TempDir Path tempDir) throws Exception {
        Config.init(tempDir);
        int threads = 4;
        int iterations = 1000;
        var latch = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        var error = new AtomicReference<Throwable>();

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    for (int i = 0; i < iterations; i++) {
                        // Read various config fields — should never throw or see null list
                        boolean e = Config.enabled;
                        int th = Config.errorThreshold;
                        var wl = Config.whitelistLoggers;
                        var bl = Config.blacklistLoggers;
                        assertNotNull(wl, "whitelistLoggers was null on read");
                        assertNotNull(bl, "blacklistLoggers was null on read");
                        // Mutate in one thread to simulate reload
                        if (Thread.currentThread().getName().contains("1")) {
                            Config.enabled = !e;
                            Config.errorThreshold = th + 1;
                        }
                    }
                } catch (Throwable ex) {
                    error.compareAndSet(null, ex);
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        assertNull(error.get(), () -> "Thread saw corruption: " + error.get());
    }
}
