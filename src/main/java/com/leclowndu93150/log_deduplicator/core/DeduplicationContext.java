package com.leclowndu93150.log_deduplicator.core;

import com.leclowndu93150.log_deduplicator.Config;
import org.apache.logging.log4j.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DeduplicationContext {
    private static class MessageEntry {
        final MessageFingerprint fingerprint;
        long firstSeen;
        long lastSeen;
        final AtomicInteger count;
        long lastReported;
        int reportedCount;

        MessageEntry(MessageFingerprint fingerprint, long timestamp) {
            this.fingerprint = fingerprint;
            this.firstSeen = timestamp;
            this.lastSeen = timestamp;
            this.count = new AtomicInteger(1);
            this.lastReported = timestamp;
            this.reportedCount = 0;
        }

        boolean shouldSuppress(long now, int windowMs, int threshold) {
            if (now - lastSeen > windowMs) {
                return false;
            }

            return count.get() > threshold;
        }

        boolean shouldReportCount(long now) {
            int currentCount = count.get();
            if (currentCount <= reportedCount) {
                return false;
            }

            if (currentCount - reportedCount >= Config.showCountAfter) {
                return true;
            }

            return now - lastReported > Config.generalWindowMs * 5L;
        }

        void markReported(long now) {
            this.lastReported = now;
            this.reportedCount = count.get();
        }
    }

    private final Map<MessageFingerprint, MessageEntry> messageCache;
    private final int maxSize;

    public DeduplicationContext(int maxSize) {
        this.maxSize = maxSize;
        this.messageCache = new LinkedHashMap<MessageFingerprint, MessageEntry>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<MessageFingerprint, MessageEntry> eldest) {
                return size() > maxSize;
            }
        };
    }

    public synchronized DuplicateCheckResult checkMessage(String message, String loggerName, Level level, long timestamp) {
        MessageFingerprint fingerprint = new MessageFingerprint(message, loggerName, level, true);

        MessageEntry entry = messageCache.get(fingerprint);

        if (entry == null) {
            if (Config.fuzzyMatching && messageCache.size() < 100) {
                for (Map.Entry<MessageFingerprint, MessageEntry> existingEntry : messageCache.entrySet()) {
                    int similarity = fingerprint.fuzzyMatch(existingEntry.getKey());
                    if (similarity >= Config.similarityThreshold) {
                        entry = existingEntry.getValue();
                        fingerprint = existingEntry.getKey();
                        break;
                    }
                }
            }
        }

        if (entry == null) {
            entry = new MessageEntry(fingerprint, timestamp);
            messageCache.put(fingerprint, entry);
            return DuplicateCheckResult.allow();
        }

        long timeSinceFirst = timestamp - entry.firstSeen;
        long timeSinceLast = timestamp - entry.lastSeen;

        int windowMs = SpamPatternDetector.estimateSpamWindowMs(message);

        if (timeSinceLast > windowMs) {
            entry.firstSeen = timestamp;
            entry.lastSeen = timestamp;
            entry.count.set(1);
            entry.reportedCount = 0;
            return DuplicateCheckResult.allow();
        }

        entry.lastSeen = timestamp;
        int newCount = entry.count.incrementAndGet();

        int threshold = getThresholdForLevel(level);

        if (newCount <= threshold) {
            return DuplicateCheckResult.allow();
        }

        if (entry.shouldReportCount(timestamp)) {
            entry.markReported(timestamp);
            return DuplicateCheckResult.suppressWithCount(newCount, timeSinceFirst);
        }

        return DuplicateCheckResult.suppress(newCount);
    }

    private int getThresholdForLevel(Level level) {
        return DeduplicationManager.getThresholdForLevel(level);
    }

    public synchronized void cleanup(long now, long maxAgeMs) {
        messageCache.entrySet().removeIf(entry -> {
            MessageEntry msg = entry.getValue();
            return (now - msg.lastSeen) > maxAgeMs;
        });
    }

    public synchronized int size() {
        return messageCache.size();
    }

    public synchronized void clear() {
        messageCache.clear();
    }

    public static class DuplicateCheckResult {
        private final boolean allowed;
        private final boolean showCount;
        private final int count;
        private final long durationMs;

        private DuplicateCheckResult(boolean allowed, boolean showCount, int count, long durationMs) {
            this.allowed = allowed;
            this.showCount = showCount;
            this.count = count;
            this.durationMs = durationMs;
        }

        public static DuplicateCheckResult allow() {
            return new DuplicateCheckResult(true, false, 0, 0);
        }

        public static DuplicateCheckResult allowWithCount(int count, long durationMs) {
            return new DuplicateCheckResult(true, true, count, durationMs);
        }

        public static DuplicateCheckResult suppress(int count) {
            return new DuplicateCheckResult(false, false, count, 0);
        }

        public static DuplicateCheckResult suppressWithCount(int count, long durationMs) {
            return new DuplicateCheckResult(false, true, count, durationMs);
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
