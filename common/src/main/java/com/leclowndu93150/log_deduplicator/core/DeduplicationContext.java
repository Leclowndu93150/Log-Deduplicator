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

    public DeduplicationManager.Result checkMessage(String message, String loggerName, Level level, long timestamp) {
        // Build fingerprint OUTSIDE the lock — normalizeMessage() can trigger class loading,
        // and class loading can trigger logging, which re-enters this filter.
        // Holding the lock during class loading causes deadlocks with modloaders.
        MessageFingerprint fingerprint = new MessageFingerprint(message, loggerName, level, true);
        int windowMs = SpamPatternDetector.estimateSpamWindowMs(message);

        synchronized (this) {
            MessageEntry entry = messageCache.get(fingerprint);

            if (entry == null && Config.fuzzyMatching) {
                int checked = 0;
                for (Map.Entry<MessageFingerprint, MessageEntry> existingEntry : messageCache.entrySet()) {
                    if (++checked > 20) break;
                    int similarity = fingerprint.fuzzyMatch(existingEntry.getKey());
                    if (similarity >= Config.similarityThreshold) {
                        entry = existingEntry.getValue();
                        fingerprint = existingEntry.getKey();
                        break;
                    }
                }
            }

            if (entry == null) {
                entry = new MessageEntry(fingerprint, timestamp);
                messageCache.put(fingerprint, entry);
                return DeduplicationManager.Result.ALLOW;
            }

            long timeSinceFirst = timestamp - entry.firstSeen;
            long timeSinceLast = timestamp - entry.lastSeen;

            if (timeSinceLast > windowMs) {
                entry.firstSeen = timestamp;
                entry.lastSeen = timestamp;
                entry.count.set(1);
                entry.reportedCount = 0;
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

}
