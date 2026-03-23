package com.leclowndu93150.log_deduplicator;

import com.leclowndu93150.log_deduplicator.core.DeduplicationManager;
import com.leclowndu93150.log_deduplicator.core.MessageFingerprint;
import com.leclowndu93150.log_deduplicator.core.SpamPatternDetector;
import com.leclowndu93150.log_deduplicator.core.StackTraceCleaner;
import com.leclowndu93150.log_deduplicator.core.DeduplicationContext;
import com.leclowndu93150.log_deduplicator.stream.SmartPrintStream;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceTest {

    private static final String HEAVY_STACK_TRACE = String.join("\n",
        "java.lang.NullPointerException: Cannot invoke method on null reference",
        "\tat net.minecraft.world.entity.Mob.tick(Mob.java:342)",
        "\tat net.minecraft.world.entity.LivingEntity.aiStep(LivingEntity.java:2814)",
        "\tat net.minecraft.world.entity.Mob.aiStep(Mob.java:198)",
        "\tat net.minecraft.world.entity.animal.Cow.aiStep(Cow.java:55)",
        "\tat net.minecraft.world.entity.LivingEntity.tick(LivingEntity.java:2629)",
        "\tat net.minecraft.world.entity.Mob.tick(Mob.java:301)",
        "\tat net.minecraft.server.level.ServerLevel.tickNonPassenger(ServerLevel.java:782)",
        "\tat net.minecraft.world.level.Level.guardEntityTick(Level.java:494)",
        "\tat net.minecraft.server.level.ServerLevel.lambda$tick$6(ServerLevel.java:396)",
        "\tat net.minecraft.world.level.entity.EntityTickList.forEach(EntityTickList.java:33)",
        "\tat net.minecraft.server.level.ServerLevel.tick(ServerLevel.java:388)",
        "\tat net.minecraft.server.MinecraftServer.tickChildren(MinecraftServer.java:935)",
        "\tat net.minecraft.server.MinecraftServer.tickServer(MinecraftServer.java:867)",
        "\tat net.minecraft.server.MinecraftServer.runServer(MinecraftServer.java:696)",
        "\tat net.minecraft.server.MinecraftServer.lambda$spin$0(MinecraftServer.java:291)",
        "\tat java.lang.Thread.run(Thread.java:840)",
        "Caused by: java.lang.IllegalStateException: Entity has no valid position",
        "\tat net.minecraft.world.entity.Entity.position(Entity.java:1200)",
        "\tat net.minecraft.world.entity.Entity.getOnPos(Entity.java:1180)",
        "\tat net.minecraft.world.entity.LivingEntity.checkFallDamage(LivingEntity.java:1908)",
        "\t... 12 more"
    );

    private static final String NESTED_STACK_TRACE = String.join("\n",
        "java.lang.RuntimeException: Failed to process event",
        "\tat net.neoforged.bus.EventBus.post(EventBus.java:100)",
        "\tat net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent.dispatch(FMLCommonSetupEvent.java:28)",
        "\tat cpw.mods.modlauncher.LaunchServiceHandler.launch(LaunchServiceHandler.java:54)",
        "\tat java.lang.reflect.Method.invoke(Method.java:580)",
        "\tat jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)",
        "Caused by: java.io.IOException: Stream closed",
        "\tat java.io.BufferedReader.read(BufferedReader.java:177)",
        "\tat com.example.mod.DataLoader.loadConfig(DataLoader.java:89)",
        "\tat com.example.mod.DataLoader.init(DataLoader.java:42)",
        "\t... 5 more",
        "Suppressed: java.lang.IllegalStateException: Cleanup failed",
        "\tat com.example.mod.DataLoader.cleanup(DataLoader.java:120)",
        "\tat com.example.mod.DataLoader.close(DataLoader.java:130)",
        "\t... 3 more"
    );

    private final Random rng = new Random(42);

    @BeforeEach
    void setUp() {
        TestConfigHelper.setDefaults();
        Config.fuzzyMatching = true;
        Config.similarityThreshold = 85;
        Config.normalizeCoordinates = true;
        Config.normalizeUUIDs = true;
        Config.normalizeMemoryAddresses = true;
        Config.normalizeTimings = true;
        Config.cleanStacktraces = true;
        Config.removeCommonFrames = true;
        Config.maxStacktraceDepth = 30;
        Config.maxBufferSize = 500;
        Config.errorThreshold = 5;
        Config.warnThreshold = 10;
        Config.infoThreshold = 20;
        Config.debugThreshold = 3;
        DeduplicationManager.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        DeduplicationManager.resetForTesting();
    }

    // ---- Message generators ----

    private String randomCoordMessage() {
        double x = rng.nextDouble() * 10000 - 5000;
        double y = rng.nextDouble() * 320;
        double z = rng.nextDouble() * 10000 - 5000;
        int ci = rng.nextInt(5);
        return switch (ci) {
            case 0 -> String.format("Entity Creeper moved to (%.3f, %.3f, %.3f) in dimension overworld", x, y, z);
            case 1 -> String.format("Chunk [%d, %d] loaded in region r.%d.%d.mca with %d entities", rng.nextInt(1000), rng.nextInt(1000), rng.nextInt(32), rng.nextInt(32), rng.nextInt(200));
            case 2 -> String.format("Block update at %.2f, %.2f, %.2f triggered by piston", x, y, z);
            case 3 -> String.format("Player teleported from (%.1f, %.1f, %.1f) to (%.1f, %.1f, %.1f)", x, y, z, x + 100, y, z + 100);
            case 4 -> String.format("Particle spawned at coordinates %.4f, %.4f near entity #%d", x, z, rng.nextInt(10000));
            default -> "Entity at " + x + ", " + y + ", " + z;
        };
    }

    private String randomUUIDMessage() {
        String uuid = UUID.randomUUID().toString();
        String name = "Player" + rng.nextInt(100);
        int ci = rng.nextInt(5);
        return switch (ci) {
            case 0 -> name + " joined the game [uuid=" + uuid + "]";
            case 1 -> "Entity " + name + " with UUID " + uuid + " removed from world";
            case 2 -> "Session " + uuid + " authenticated for player " + name;
            case 3 -> "Saving player data for " + name + " (" + uuid + ")";
            case 4 -> "Inventory sync for " + name + " failed, retrying";
            default -> "Player " + uuid;
        };
    }

    private String randomTimingMessage() {
        int ms = rng.nextInt(5000);
        int count = rng.nextInt(1000);
        int ci = rng.nextInt(5);
        return switch (ci) {
            case 0 -> "Chunk loading took " + ms + "ms for region (" + rng.nextInt(500) + ", " + rng.nextInt(500) + ")";
            case 1 -> "World save completed in " + ms + "ms, " + count + " chunks written";
            case 2 -> "Tick " + rng.nextInt(10000) + " took " + ms + "ms (expected 50ms), " + count + " entities processed";
            case 3 -> "Recipe reload finished in " + ms + "ms, " + count + " recipes loaded";
            case 4 -> "Network packet processed in " + ms + "ms from 192.168." + rng.nextInt(256) + "." + rng.nextInt(256);
            default -> "Operation took " + ms + "ms";
        };
    }

    private String randomMemoryMessage() {
        String addr = Long.toHexString(rng.nextLong(0xFFFFFFFFFFL));
        int ci = rng.nextInt(4);
        return switch (ci) {
            case 0 -> "Object pool@" + addr + " allocated " + rng.nextInt(100000) + " instances";
            case 1 -> "GC pressure: live objects@" + addr + ", freed " + rng.nextInt(100000) + " bytes";
            case 2 -> "Buffer@" + addr + " resized from " + rng.nextInt(10000) + " to " + rng.nextInt(100000) + " bytes";
            case 3 -> String.format("Cache@%s hit ratio: %.2f%% over %d lookups", addr, rng.nextDouble() * 100, rng.nextInt(10000));
            default -> "Memory@" + addr;
        };
    }

    private String randomGenericSpam() {
        int ci = rng.nextInt(10);
        return switch (ci) {
            case 0 -> "Server tick took too long! Did the system time change?";
            case 1 -> "Can't keep up! Is the server overloaded? Running " + rng.nextInt(5000) + "ms behind";
            case 2 -> "Skipping entity tick for entity_" + rng.nextInt(100) + ", too far from player";
            case 3 -> "Block entity at [" + rng.nextInt(256) + ", " + rng.nextInt(256) + ", " + rng.nextInt(256) + "] is ticking without valid state";
            case 4 -> "Recipe recipe_" + rng.nextInt(100) + " references unknown item item_" + rng.nextInt(100);
            case 5 -> "Advancement adv_" + rng.nextInt(50) + " has unresolved criteria";
            case 6 -> "Biome source for dimension dim_" + rng.nextInt(3) + " is using deprecated API";
            case 7 -> "Connection throttled for 192.168." + rng.nextInt(256) + "." + rng.nextInt(256) + ", too many packets";
            case 8 -> "Lighting engine recalculating chunk section at [" + rng.nextInt(1000) + ", " + rng.nextInt(1000) + "]";
            case 9 -> "Structure fortress could not be placed at chunk [" + rng.nextInt(1000) + ", " + rng.nextInt(1000) + "], terrain mismatch";
            default -> "Generic log message " + rng.nextInt(10000);
        };
    }

    private String randomMessage() {
        int type = rng.nextInt(6);
        return switch (type) {
            case 0 -> randomCoordMessage();
            case 1 -> randomUUIDMessage();
            case 2 -> randomTimingMessage();
            case 3 -> randomMemoryMessage();
            case 4 -> randomGenericSpam();
            case 5 -> rng.nextBoolean() ? HEAVY_STACK_TRACE : NESTED_STACK_TRACE;
            default -> "Unknown message type";
        };
    }

    private Level randomLevel() {
        int r = rng.nextInt(10);
        if (r < 1) return Level.ERROR;
        if (r < 3) return Level.WARN;
        if (r < 7) return Level.INFO;
        return Level.DEBUG;
    }

    // ---- Benchmarks ----

    @Test
    void normalizationThroughput() {
        List<String> messages = new ArrayList<>(10000);
        for (int i = 0; i < 10000; i++) {
            messages.add(randomMessage());
        }

        // warmup
        for (String msg : messages) SpamPatternDetector.normalizeMessage(msg);

        long start = System.nanoTime();
        int iterations = 20;
        for (int iter = 0; iter < iterations; iter++) {
            for (String msg : messages) {
                SpamPatternDetector.normalizeMessage(msg);
            }
        }
        long elapsed = System.nanoTime() - start;
        long totalOps = (long) iterations * messages.size();
        double opsPerSec = totalOps / (elapsed / 1_000_000_000.0);
        double nsPerOp = (double) elapsed / totalOps;

        System.out.printf("Normalization: %.0f ops/sec, %.0f ns/op (%d total ops)%n", opsPerSec, nsPerOp, totalOps);
        assertTrue(nsPerOp < 50_000, "Normalization too slow: " + nsPerOp + " ns/op");
    }

    @Test
    void fingerprintCreationThroughput() {
        List<String> messages = new ArrayList<>(10000);
        for (int i = 0; i < 10000; i++) messages.add(randomMessage());

        // warmup
        for (String msg : messages) new MessageFingerprint(msg, "test.Logger", Level.INFO, true);

        long start = System.nanoTime();
        int iterations = 20;
        for (int iter = 0; iter < iterations; iter++) {
            for (String msg : messages) {
                new MessageFingerprint(msg, "test.Logger", Level.INFO, true);
            }
        }
        long elapsed = System.nanoTime() - start;
        long totalOps = (long) iterations * messages.size();
        double opsPerSec = totalOps / (elapsed / 1_000_000_000.0);
        double nsPerOp = (double) elapsed / totalOps;

        System.out.printf("Fingerprint creation: %.0f ops/sec, %.0f ns/op (%d total ops)%n", opsPerSec, nsPerOp, totalOps);
        assertTrue(nsPerOp < 100_000, "Fingerprint creation too slow: " + nsPerOp + " ns/op");
    }

    @Test
    void stackTraceCleaningThroughput() {
        // warmup
        for (int i = 0; i < 1000; i++) {
            StackTraceCleaner.cleanStackTrace(HEAVY_STACK_TRACE);
            StackTraceCleaner.cleanStackTrace(NESTED_STACK_TRACE);
        }

        long start = System.nanoTime();
        int iterations = 50000;
        for (int i = 0; i < iterations; i++) {
            StackTraceCleaner.cleanStackTrace(HEAVY_STACK_TRACE);
            StackTraceCleaner.cleanStackTrace(NESTED_STACK_TRACE);
        }
        long elapsed = System.nanoTime() - start;
        long totalOps = iterations * 2L;
        double opsPerSec = totalOps / (elapsed / 1_000_000_000.0);
        double nsPerOp = (double) elapsed / totalOps;

        System.out.printf("Stack trace cleaning: %.0f ops/sec, %.0f ns/op (%d total ops)%n", opsPerSec, nsPerOp, totalOps);
        assertTrue(nsPerOp < 200_000, "Stack trace cleaning too slow: " + nsPerOp + " ns/op");
    }

    @Test
    void deduplicationContextUnderLoad() {
        DeduplicationContext ctx = new DeduplicationContext(500);
        List<String> messages = new ArrayList<>(5000);
        // 100 unique templates, each with random numbers = many unique but normalizable messages
        for (int i = 0; i < 5000; i++) messages.add(randomMessage());

        // warmup
        for (int i = 0; i < 1000; i++) {
            ctx.checkMessage(messages.get(i % messages.size()), "test", Level.INFO, System.currentTimeMillis());
        }
        ctx.clear();

        long start = System.nanoTime();
        long ts = System.currentTimeMillis();
        int iterations = 100_000;
        for (int i = 0; i < iterations; i++) {
            String msg = messages.get(i % messages.size());
            Level level = randomLevel();
            ctx.checkMessage(msg, "test.Logger." + (i % 10), level, ts + (i / 10));
        }
        long elapsed = System.nanoTime() - start;
        double opsPerSec = iterations / (elapsed / 1_000_000_000.0);
        double nsPerOp = (double) elapsed / iterations;

        System.out.printf("Context check: %.0f ops/sec, %.0f ns/op (%d total ops, cache size: %d)%n",
            opsPerSec, nsPerOp, iterations, ctx.size());
        assertTrue(nsPerOp < 500_000, "Context check too slow: " + nsPerOp + " ns/op");
    }

    @Test
    void fullPipelineSingleThread() {
        var mgr = DeduplicationManager.getInstance();
        List<String> messages = new ArrayList<>(5000);
        for (int i = 0; i < 5000; i++) messages.add(randomMessage());

        // warmup
        for (int i = 0; i < 2000; i++) {
            mgr.check(messages.get(i % messages.size()), "test", Level.INFO);
        }
        DeduplicationManager.resetForTesting();
        mgr = DeduplicationManager.getInstance();

        long start = System.nanoTime();
        int iterations = 200_000;
        int allowed = 0, denied = 0;
        for (int i = 0; i < iterations; i++) {
            String msg = messages.get(i % messages.size());
            Level level = randomLevel();
            String logger = "mod.logger." + (i % 20);
            var result = mgr.check(msg, logger, level);
            if (result.isAllowed()) allowed++;
            else denied++;
        }
        long elapsed = System.nanoTime() - start;
        double opsPerSec = iterations / (elapsed / 1_000_000_000.0);
        double nsPerOp = (double) elapsed / iterations;

        System.out.printf("Full pipeline (single thread): %.0f ops/sec, %.0f ns/op%n", opsPerSec, nsPerOp);
        System.out.printf("  allowed=%d, denied=%d, suppression=%.1f%%%n", allowed, denied, (denied * 100.0 / iterations));
        assertTrue(nsPerOp < 500_000, "Full pipeline too slow: " + nsPerOp + " ns/op");
    }

    @Test
    void fullPipelineConcurrent() throws Exception {
        int threadCount = 8;
        int messagesPerThread = 50_000;
        var mgr = DeduplicationManager.getInstance();

        List<String> messages = new ArrayList<>(5000);
        for (int i = 0; i < 5000; i++) messages.add(randomMessage());

        // warmup
        for (int i = 0; i < 2000; i++) mgr.check(messages.get(i % messages.size()), "test", Level.INFO);
        DeduplicationManager.resetForTesting();
        mgr = DeduplicationManager.getInstance();
        var finalMgr = mgr;

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        AtomicLong totalAllowed = new AtomicLong();
        AtomicLong totalDenied = new AtomicLong();
        AtomicLong errors = new AtomicLong();

        Level[] levels = {Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG};

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            pool.submit(() -> {
                Random threadRng = new Random(threadId * 1337L);
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }

                long localAllowed = 0, localDenied = 0;
                try {
                    for (int i = 0; i < messagesPerThread; i++) {
                        String msg = messages.get(threadRng.nextInt(messages.size()));
                        Level level = levels[threadRng.nextInt(levels.length)];
                        String logger = "mod" + (threadId % 5) + ".logger." + (i % 10);
                        var result = finalMgr.check(msg, logger, level);
                        if (result.isAllowed()) localAllowed++;
                        else localDenied++;
                    }
                } catch (Throwable e) {
                    errors.incrementAndGet();
                    e.printStackTrace();
                }
                totalAllowed.addAndGet(localAllowed);
                totalDenied.addAndGet(localDenied);
            });
        }

        ready.await();
        long start = System.nanoTime();
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "Threads did not finish in time");
        long elapsed = System.nanoTime() - start;

        long totalOps = (long) threadCount * messagesPerThread;
        double opsPerSec = totalOps / (elapsed / 1_000_000_000.0);
        double nsPerOp = (double) elapsed / totalOps;

        System.out.printf("Full pipeline (%d threads): %.0f ops/sec, %.0f ns/op%n", threadCount, opsPerSec, nsPerOp);
        System.out.printf("  total=%d, allowed=%d, denied=%d, errors=%d%n",
            totalOps, totalAllowed.get(), totalDenied.get(), errors.get());

        assertEquals(0, errors.get(), "No threads should have thrown exceptions");
        assertEquals(totalOps, totalAllowed.get() + totalDenied.get(), "Every message should produce a result");
    }

    @Test
    void smartPrintStreamUnderLoad() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream(1024 * 1024);
        SmartPrintStream stream = new SmartPrintStream(new PrintStream(sink), false);

        List<String> messages = new ArrayList<>(2000);
        for (int i = 0; i < 500; i++) messages.add(randomCoordMessage());
        for (int i = 0; i < 500; i++) messages.add(randomUUIDMessage());
        for (int i = 0; i < 500; i++) messages.add(randomTimingMessage());
        for (int i = 0; i < 500; i++) messages.add(randomGenericSpam());

        // warmup
        for (int i = 0; i < 500; i++) stream.println(messages.get(i));

        sink.reset();
        long start = System.nanoTime();
        int iterations = 100_000;
        for (int i = 0; i < iterations; i++) {
            stream.println(messages.get(i % messages.size()));
        }
        stream.flush();
        long elapsed = System.nanoTime() - start;
        double opsPerSec = iterations / (elapsed / 1_000_000_000.0);
        double nsPerOp = (double) elapsed / iterations;

        System.out.printf("SmartPrintStream: %.0f ops/sec, %.0f ns/op, output size=%d bytes%n",
            opsPerSec, nsPerOp, sink.size());
        assertTrue(nsPerOp < 500_000, "SmartPrintStream too slow: " + nsPerOp + " ns/op");
        stream.close();
    }

    @Test
    void stackTraceSpamThroughSmartPrintStream() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream(1024 * 1024);
        SmartPrintStream stream = new SmartPrintStream(new PrintStream(sink), true); // error stream

        // warmup
        for (int i = 0; i < 100; i++) {
            for (String line : HEAVY_STACK_TRACE.split("\n")) stream.println(line);
        }

        sink.reset();
        long start = System.nanoTime();
        int repetitions = 5000;
        for (int i = 0; i < repetitions; i++) {
            for (String line : HEAVY_STACK_TRACE.split("\n")) {
                stream.println(line);
            }
        }
        stream.flush();
        long elapsed = System.nanoTime() - start;
        double msTotal = elapsed / 1_000_000.0;

        System.out.printf("Stack trace spam (%d traces): %.1fms total, output size=%d bytes%n",
            repetitions, msTotal, sink.size());

        // With dedup, output should be WAY smaller than raw (5000 * trace)
        int rawSize = HEAVY_STACK_TRACE.length() * repetitions;
        assertTrue(sink.size() < rawSize / 2,
            "Dedup should reduce output significantly: raw=" + rawSize + " actual=" + sink.size());
        stream.close();
    }

    @Test
    void cacheEvictionUnderPressure() {
        // Small cache, massive unique message stream = constant eviction
        DeduplicationContext ctx = new DeduplicationContext(50);
        Config.fuzzyMatching = true;
        Config.similarityThreshold = 85;

        long start = System.nanoTime();
        long ts = System.currentTimeMillis();
        int iterations = 50_000;
        for (int i = 0; i < iterations; i++) {
            // Mostly unique messages to force eviction
            String msg = "Unique message #" + i + " with coord (%.2f, %.2f, %.2f) at " + i + "ms";
            msg = String.format(msg, rng.nextDouble() * 1000, rng.nextDouble() * 320, rng.nextDouble() * 1000);
            ctx.checkMessage(msg, "test", Level.INFO, ts + i);
        }
        long elapsed = System.nanoTime() - start;
        double nsPerOp = (double) elapsed / iterations;

        System.out.printf("Cache eviction stress: %.0f ns/op, final cache size=%d%n", nsPerOp, ctx.size());
        assertTrue(ctx.size() <= 50, "Cache should respect max size");
        assertTrue(nsPerOp < 1_000_000, "Eviction too slow: " + nsPerOp + " ns/op");
    }
}
