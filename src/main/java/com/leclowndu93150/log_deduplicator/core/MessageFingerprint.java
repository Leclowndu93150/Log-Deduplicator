package com.leclowndu93150.log_deduplicator.core;

import org.apache.logging.log4j.Level;

public class MessageFingerprint {
    private final String normalizedMessage;
    private final String loggerName;
    private final Level level;
    private final long hashCode;

    public MessageFingerprint(String message, String loggerName, Level level, boolean normalize) {
        String msg = message != null ? message : "";
        this.normalizedMessage = normalize ? SpamPatternDetector.normalizeMessage(msg) : msg;
        this.loggerName = loggerName;
        this.level = level;
        this.hashCode = computeHash();
    }

    private long computeHash() {
        long hash = 17;
        hash = hash * 31 + (normalizedMessage != null ? normalizedMessage.hashCode() : 0);
        hash = hash * 31 + (loggerName != null ? loggerName.hashCode() : 0);
        hash = hash * 31 + (level != null ? level.hashCode() : 0);
        return hash;
    }

    public String getNormalizedMessage() {
        return normalizedMessage;
    }

    public String getLoggerName() {
        return loggerName;
    }

    public Level getLevel() {
        return level;
    }

    public int fuzzyMatch(MessageFingerprint other) {
        if (this.equals(other)) {
            return 100;
        }

        if (!this.level.equals(other.level) ||
            (this.loggerName == null ? other.loggerName != null : !this.loggerName.equals(other.loggerName))) {
            return 0;
        }

        return levenshteinSimilarity(this.normalizedMessage, other.normalizedMessage);
    }

    private int levenshteinSimilarity(String s1, String s2) {
        if (s1.equals(s2)) {
            return 100;
        }

        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 100;
        }

        int distance = levenshteinDistance(s1, s2);
        return (int) (((double) (maxLen - distance) / maxLen) * 100);
    }

    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

        if (len1 > 200 || len2 > 200) {
            return Math.abs(len1 - len2);
        }

        int[] previousRow = new int[len2 + 1];
        int[] currentRow = new int[len2 + 1];

        for (int j = 0; j <= len2; j++) {
            previousRow[j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            currentRow[0] = i;

            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;

                currentRow[j] = Math.min(
                    Math.min(currentRow[j - 1] + 1, previousRow[j] + 1),
                    previousRow[j - 1] + cost
                );
            }

            int[] temp = previousRow;
            previousRow = currentRow;
            currentRow = temp;
        }

        return previousRow[len2];
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MessageFingerprint)) return false;

        MessageFingerprint other = (MessageFingerprint) obj;
        return this.hashCode == other.hashCode &&
               this.normalizedMessage.equals(other.normalizedMessage) &&
               (this.loggerName == null ? other.loggerName == null : this.loggerName.equals(other.loggerName)) &&
               (this.level == null ? other.level == null : this.level.equals(other.level));
    }

    @Override
    public int hashCode() {
        return (int) (hashCode ^ (hashCode >>> 32));
    }

    @Override
    public String toString() {
        return String.format("[%s/%s] %s", level, loggerName, normalizedMessage);
    }
}
