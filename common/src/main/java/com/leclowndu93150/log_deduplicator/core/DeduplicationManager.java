package com.leclowndu93150.log_deduplicator.core;

import com.leclowndu93150.log_deduplicator.Config;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DeduplicationManager {
    private static volatile DeduplicationManager INSTANCE;
    private static final String SUPPRESSION_LOGGER = "log_deduplicator.suppression";
    private static final ThreadLocal<Boolean> bypassing = ThreadLocal.withInitial(() -> false);

    private final Logger suppressionLogger;
    private final DeduplicationContext context;
    private final ScheduledExecutorService cleanupExecutor;
    private final AtomicLong suppressedCount;
    private final AtomicLong totalProcessed;

    private DeduplicationManager() {
        this.suppressionLogger = LogManager.getLogger(SUPPRESSION_LOGGER);
        this.context = new DeduplicationContext(Math.max(Config.maxBufferSize, 50));
        this.suppressedCount = new AtomicLong(0);
        this.totalProcessed = new AtomicLong(0);

        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-dedup-cleanup");
            t.setDaemon(true);
            return t;
        });

        this.cleanupExecutor.scheduleAtFixedRate(this::cleanup, 30, 30, TimeUnit.SECONDS);
    }

    public static DeduplicationManager getInstance() {
        DeduplicationManager inst = INSTANCE;
        if (inst == null) {
            synchronized (DeduplicationManager.class) {
                inst = INSTANCE;
                if (inst == null) {
                    inst = new DeduplicationManager();
                    INSTANCE = inst;
                }
            }
        }
        return inst;
    }

    public static boolean isBypassing() {
        return bypassing.get();
    }

    public static boolean isSuppressionLogger(String loggerName) {
        return SUPPRESSION_LOGGER.equals(loggerName);
    }

    public Result check(String message, String loggerName, Level level) {
        if (bypassing.get()) {
            return Result.ALLOW;
        }

        if (!Config.enabled || Config.debugMode) {
            return Result.ALLOW;
        }

        if (level == null || level.intLevel() > Level.TRACE.intLevel()) {
            return Result.ALLOW;
        }

        if (loggerName != null && Config.blacklistLoggers.contains(loggerName)) {
            return Result.DENY;
        }

        if (!Config.whitelistLoggers.isEmpty() &&
            (loggerName == null || !Config.whitelistLoggers.contains(loggerName))) {
            return Result.ALLOW;
        }

        totalProcessed.incrementAndGet();

        Result result = context.checkMessage(message, loggerName, level, System.currentTimeMillis());

        if (!result.isAllowed()) {
            suppressedCount.incrementAndGet();
            if (result.shouldShowCount() && Config.showSuppressionMessage) {
                logSuppression(loggerName, message, result.getCount(), result.getDurationMs());
            }
        }

        return result;
    }

    private void logSuppression(String loggerName, String message, int count, long durationMs) {
        if (loggerName != null && loggerName.startsWith("System.")) {
            return;
        }

        bypassing.set(true);
        try {
            String formatted = String.format(Locale.US, "[%s] %s [suppressed %d times over %.1fs]",
                loggerName, truncateMessage(message, 100), count - 1, durationMs / 1000.0);
            suppressionLogger.info(formatted);
        } finally {
            bypassing.set(false);
        }
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "";
        if (message.length() <= maxLength) return message;
        return message.substring(0, maxLength) + "...";
    }

    public static String formatWithCount(String message, int count, long durationMs) {
        return String.format(Locale.US, "%s [suppressed %d times over %.1fs]",
            message, count - 1, durationMs / 1000.0);
    }

    public static int getThresholdForLevel(Level level) {
        if (level == Level.FATAL || level == Level.ERROR) {
            return Config.errorThreshold;
        } else if (level == Level.WARN) {
            return Config.warnThreshold;
        } else if (level == Level.INFO) {
            return Config.infoThreshold;
        } else {
            return Config.debugThreshold;
        }
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        long maxAgeMs = Config.generalWindowMs * 10L;
        context.cleanup(now, maxAgeMs);
    }

    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Visible for testing — shuts down the current instance so getInstance() creates a fresh one
    public static void resetForTesting() {
        synchronized (DeduplicationManager.class) {
            DeduplicationManager inst = INSTANCE;
            if (inst != null) {
                inst.shutdown();
                INSTANCE = null;
            }
        }
    }

    public long getSuppressedCount() {
        return suppressedCount.get();
    }

    public long getTotalProcessed() {
        return totalProcessed.get();
    }

    public double getSuppressionRate() {
        long total = totalProcessed.get();
        if (total == 0) return 0.0;
        return (suppressedCount.get() * 100.0) / total;
    }

    public static class Result {
        public static final Result ALLOW = new Result(true, false, 0, 0);
        public static final Result DENY = new Result(false, false, 0, 0);

        private final boolean allowed;
        private final boolean showCount;
        private final int count;
        private final long durationMs;

        private Result(boolean allowed, boolean showCount, int count, long durationMs) {
            this.allowed = allowed;
            this.showCount = showCount;
            this.count = count;
            this.durationMs = durationMs;
        }

        public static Result denyWithCount(int count, long durationMs) {
            return new Result(false, true, count, durationMs);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public boolean shouldShowCount() {
            return showCount;
        }

        public int getCount() {
            return count;
        }

        public long getDurationMs() {
            return durationMs;
        }
    }
}
