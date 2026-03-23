package com.leclowndu93150.log_deduplicator.core;

import com.leclowndu93150.log_deduplicator.Config;
import org.apache.logging.log4j.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DeduplicationContext {
    private static class MessageEntry {
        final MessageFingerprint fingerprint;
        volatile long firstSeen;
        volatile long lastSeen;
        final AtomicInteger count;
        volatile long lastReported;
        volatile int reportedCount;

        MessageEntry(MessageFingerprint fingerprint, long timestamp) {
            this.fingerprint = fingerprint;
            this.firstSeen = timestamp;
            this.lastSeen = timestamp;
            this.count = new AtomicInteger(1);
            this.lastReported = timestamp;
            this.reportedCount = 0;
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

        void reset(long timestamp) {
            this.firstSeen = timestamp;
            this.lastSeen = timestamp;
            this.count.set(1);
            this.reportedCount = 0;
        }
    }

    private final ConcurrentHashMap<MessageFingerprint, MessageEntry> messageCache;
    private final int maxSize;

    public DeduplicationContext(int maxSize) {
        this.maxSize = maxSize;
        this.messageCache = new ConcurrentHashMap<>(maxSize, 0.75f, 4);
    }

    public DeduplicationManager.Result checkMessage(String message, String loggerName, Level level, long timestamp) {
        MessageFingerprint fingerprint = new MessageFingerprint(message, loggerName, level, true);
        int windowMs = SpamPatternDetector.estimateSpamWindowMs(message);

        MessageEntry entry = messageCache.get(fingerprint);

        if (entry == null && Config.fuzzyMatching) {
            int checked = 0;
            for (Map.Entry<MessageFingerprint, MessageEntry> existingEntry : messageCache.entrySet()) {
                if (++checked > 20) break;
                int similarity = fingerprint.fuzzyMatch(existingEntry.getKey());
                if (similarity >= Config.similarityThreshold) {
                    entry = existingEntry.getValue();
                    break;
                }
            }
        }

        if (entry == null) {
            entry = new MessageEntry(fingerprint, timestamp);
            MessageEntry existing = messageCache.putIfAbsent(fingerprint, entry);
            if (existing != null) {
                entry = existing;
            } else {
                evictIfNeeded();
                return DeduplicationManager.Result.ALLOW;
            }
        }

        long timeSinceFirst = timestamp - entry.firstSeen;
        long timeSinceLast = timestamp - entry.lastSeen;

        if (timeSinceLast > windowMs) {
            entry.reset(timestamp);
            return DeduplicationManager.Result.ALLOW;
        }

        entry.lastSeen = timestamp;
        int newCount = entry.count.incrementAndGet();

        int threshold = getThresholdForLevel(level);

        if (newCount <= threshold) {
            return DeduplicationManager.Result.ALLOW;
        }

        if (entry.shouldReportCount(timestamp)) {
            entry.markReported(timestamp);
            return DeduplicationManager.Result.denyWithCount(newCount, timeSinceFirst);
        }

        return DeduplicationManager.Result.DENY;
    }

    private void evictIfNeeded() {
        int excess = messageCache.size() - maxSize;
        if (excess <= 0) return;

        long now = System.currentTimeMillis();
        int removed = 0;
        for (var it = messageCache.entrySet().iterator(); it.hasNext() && removed < excess + 10; ) {
            var e = it.next();
            if (now - e.getValue().lastSeen > Config.generalWindowMs * 2L) {
                it.remove();
                removed++;
            }
        }

        if (messageCache.size() > maxSize) {
            for (var it = messageCache.entrySet().iterator(); it.hasNext() && messageCache.size() > maxSize; ) {
                it.next();
                it.remove();
            }
        }
    }

    private int getThresholdForLevel(Level level) {
        return DeduplicationManager.getThresholdForLevel(level);
    }

    public void cleanup(long now, long maxAgeMs) {
        messageCache.entrySet().removeIf(entry -> (now - entry.getValue().lastSeen) > maxAgeMs);
    }

    public int size() {
        return messageCache.size();
    }

    public void clear() {
        messageCache.clear();
    }

}
