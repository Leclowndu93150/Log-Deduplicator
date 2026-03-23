package com.leclowndu93150.log_deduplicator.core;

import com.leclowndu93150.log_deduplicator.Config;

public class SpamPatternDetector {

    public static String normalizeMessage(String message) {
        if (message == null) return "";
        if (message.isEmpty()) return message;
        return FastMessageScanner.normalize(message);
    }

    public static int estimateSpamWindowMs(String message) {
        if (message == null) {
            return Config.generalWindowMs;
        }

        if (containsIgnoreCase(message, "chunk") || containsIgnoreCase(message, "loading") || containsIgnoreCase(message, "unloading")) {
            return Config.chunkWindowMs;
        }

        if (containsIgnoreCase(message, "entity") || containsIgnoreCase(message, "tick") || containsIgnoreCase(message, "particle")) {
            return Config.coordinatesWindowMs;
        }

        if (containsIgnoreCase(message, "save") || containsIgnoreCase(message, "autosave") || containsIgnoreCase(message, "world")) {
            return Config.generalWindowMs * 2;
        }

        return Config.generalWindowMs;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        int hLen = haystack.length();
        int nLen = needle.length();
        if (nLen > hLen) return false;
        outer:
        for (int i = 0, limit = hLen - nLen; i <= limit; i++) {
            for (int j = 0; j < nLen; j++) {
                char hc = haystack.charAt(i + j);
                char nc = needle.charAt(j);
                if (hc != nc && Character.toLowerCase(hc) != nc) continue outer;
            }
            return true;
        }
        return false;
    }

    public static boolean isStackTraceLine(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }

        String trimmed = message.trim();
        if (trimmed.isEmpty()) return true; // blank lines within a stack trace

        return trimmed.startsWith("at ") ||
               trimmed.startsWith("Caused by:") ||
               trimmed.startsWith("Suppressed:") ||
               trimmed.startsWith("...");
    }

    public static boolean isExceptionMessage(String message) {
        if (message == null) {
            return false;
        }

        // Look for actual exception class name patterns like "java.lang.NullPointerException"
        // or "NullPointerException: some message", not just the word "Error" in any context
        return message.contains("Exception") ||
               (message.contains("Error") && (message.contains(".") || message.contains(":")));
    }
}
