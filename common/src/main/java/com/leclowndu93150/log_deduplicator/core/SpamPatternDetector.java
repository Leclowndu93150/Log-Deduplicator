package com.leclowndu93150.log_deduplicator.core;

import com.leclowndu93150.log_deduplicator.Config;

import java.util.regex.Pattern;

public class SpamPatternDetector {
    private static final Pattern COORDINATES_PATTERN = Pattern.compile("(?<![\\w.])-?\\d+\\.\\d+,\\s*-?\\d+\\.\\d+(?:,\\s*-?\\d+\\.\\d+)?(?![\\w.])");
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern MEMORY_ADDRESS_PATTERN = Pattern.compile("@[0-9a-fA-F]{6,16}");
    private static final Pattern MILLISECOND_PATTERN = Pattern.compile("\\d+ms");
    private static final Pattern TICK_PATTERN = Pattern.compile("\\d+ tick");
    
    private static final Pattern CHUNK_COORDS = Pattern.compile("\\[(-?\\d+),\\s*(-?\\d+)\\]");
    private static final Pattern ENTITY_COORDS = Pattern.compile("\\((-?\\d+\\.\\d+),\\s*(-?\\d+\\.\\d+),\\s*(-?\\d+\\.\\d+)\\)");

    public static String normalizeMessage(String message) {
        if (message == null) {
            return "";
        }
        if (message.isEmpty()) {
            return message;
        }

        String normalized = message;

        if (Config.normalizeCoordinates) {
            normalized = CHUNK_COORDS.matcher(normalized).replaceAll("[*, *]");
            normalized = ENTITY_COORDS.matcher(normalized).replaceAll("(*, *, *)");
            normalized = COORDINATES_PATTERN.matcher(normalized).replaceAll("*");
        }

        if (Config.normalizeUUIDs) {
            normalized = UUID_PATTERN.matcher(normalized).replaceAll("*UUID*");
        }

        if (Config.normalizeMemoryAddresses) {
            normalized = MEMORY_ADDRESS_PATTERN.matcher(normalized).replaceAll("@*");
        }

        if (Config.normalizeTimings) {
            normalized = MILLISECOND_PATTERN.matcher(normalized).replaceAll("*ms");
            normalized = TICK_PATTERN.matcher(normalized).replaceAll("* tick");
        }

        return normalized;
    }

    public static int estimateSpamWindowMs(String message) {
        if (message == null) {
            return Config.generalWindowMs;
        }

        String lower = message.toLowerCase();

        if (lower.contains("chunk") || lower.contains("loading") || lower.contains("unloading")) {
            return Config.chunkWindowMs;
        }

        if (lower.contains("entity") || lower.contains("tick") || lower.contains("particle")) {
            return Config.coordinatesWindowMs;
        }

        if (lower.contains("save") || lower.contains("autosave") || lower.contains("world")) {
            return Config.generalWindowMs * 2;
        }

        return Config.generalWindowMs;
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
