package com.leclowndu93150.log_deduplicator;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileWatcher;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Config {
    private static Path configPath;
    private static CommentedFileConfig config;

    public static volatile boolean enabled;
    public static volatile boolean debugMode;
    public static volatile int errorThreshold;
    public static volatile int warnThreshold;
    public static volatile int infoThreshold;
    public static volatile int debugThreshold;
    public static volatile int coordinatesWindowMs;
    public static volatile int chunkWindowMs;
    public static volatile int generalWindowMs;
    public static volatile int showCountAfter;
    public static volatile int maxBufferSize;
    public static volatile boolean fuzzyMatching;
    public static volatile int similarityThreshold;
    public static volatile boolean normalizeCoordinates;
    public static volatile boolean normalizeUUIDs;
    public static volatile boolean normalizeMemoryAddresses;
    public static volatile boolean normalizeTimings;
    public static volatile List<String> whitelistLoggers;
    public static volatile List<String> blacklistLoggers;
    public static volatile boolean systemStreamsEnabled;
    public static volatile boolean detectMultiline;
    public static volatile int multilineTimeoutMs;
    public static volatile boolean cleanStacktraces;
    public static volatile boolean removeCommonFrames;
    public static volatile int maxStacktraceDepth;
    public static volatile boolean showSuppressionMessage;

    public static void init(Path configDir) {
        configPath = configDir.resolve("log_deduplicator.toml");
        config = CommentedFileConfig.builder(configPath)
            .autosave()
            .preserveInsertionOrder()
            .build();

        config.load();

        setDefaults();
        loadValues();

        FileWatcher.defaultInstance().addWatch(configPath, Config::reload);
    }

    private static void reload() {
        config.load();
        loadValues();
    }

    public static void save() {
        config.set("enabled", enabled);
        config.set("debug_mode", debugMode);
        config.set("thresholds.error", errorThreshold);
        config.set("thresholds.warn", warnThreshold);
        config.set("thresholds.info", infoThreshold);
        config.set("thresholds.debug", debugThreshold);
        config.set("time_windows.coordinates_ms", coordinatesWindowMs);
        config.set("time_windows.chunk_ms", chunkWindowMs);
        config.set("time_windows.general_ms", generalWindowMs);
        config.set("show_count_after", showCountAfter);
        config.set("max_buffer_size", maxBufferSize);
        config.set("similarity.fuzzy_matching", fuzzyMatching);
        config.set("similarity.threshold", similarityThreshold);
        config.set("normalization.coordinates", normalizeCoordinates);
        config.set("normalization.uuids", normalizeUUIDs);
        config.set("normalization.memory_addresses", normalizeMemoryAddresses);
        config.set("normalization.timings", normalizeTimings);
        config.set("system_streams.enabled", systemStreamsEnabled);
        config.set("system_streams.detect_multiline", detectMultiline);
        config.set("system_streams.multiline_timeout_ms", multilineTimeoutMs);
        config.set("stacktraces.clean", cleanStacktraces);
        config.set("stacktraces.remove_common_frames", removeCommonFrames);
        config.set("stacktraces.max_depth", maxStacktraceDepth);
        config.set("output.show_suppression_message", showSuppressionMessage);
    }

    private static void setDefaults() {
        setDefault("enabled", true, "Master switch to enable/disable deduplication");
        setDefault("debug_mode", false, "Disables deduplication for troubleshooting");

        setDefault("thresholds.error", 5, "Duplicates allowed before suppressing ERROR/FATAL");
        setDefault("thresholds.warn", 10, "Duplicates allowed before suppressing WARN");
        setDefault("thresholds.info", 20, "Duplicates allowed before suppressing INFO");
        setDefault("thresholds.debug", 3, "Duplicates allowed before suppressing DEBUG/TRACE");

        setDefault("time_windows.coordinates_ms", 100, "Window for coordinate spam (ms)");
        setDefault("time_windows.chunk_ms", 500, "Window for chunk operation spam (ms)");
        setDefault("time_windows.general_ms", 2000, "Window for general spam (ms)");

        setDefault("show_count_after", 100, "Show suppression count after N additional duplicates");
        setDefault("max_buffer_size", 500, "Max unique messages tracked per thread");

        setDefault("similarity.fuzzy_matching", true, "Match similar messages with different numbers");
        setDefault("similarity.threshold", 85, "Similarity threshold 0-100 (higher = stricter)");

        setDefault("normalization.coordinates", true, "Normalize coordinates in messages");
        setDefault("normalization.uuids", true, "Normalize UUIDs in messages");
        setDefault("normalization.memory_addresses", true, "Normalize memory addresses");
        setDefault("normalization.timings", true, "Normalize timing values");

        setDefault("filters.whitelist", new ArrayList<String>(), "Only deduplicate these loggers (empty = all)");
        setDefault("filters.blacklist", new ArrayList<String>(), "Completely suppress these loggers");

        setDefault("system_streams.enabled", true, "Deduplicate System.out/err");
        setDefault("system_streams.detect_multiline", true, "Group multi-line messages together");
        setDefault("system_streams.multiline_timeout_ms", 50, "Max time between lines of same message");

        setDefault("stacktraces.clean", true, "Remove duplicate stack frames");
        setDefault("stacktraces.remove_common_frames", false, "Remove framework frames");
        setDefault("stacktraces.max_depth", 0, "Max stack depth (0 = unlimited)");

        setDefault("output.show_suppression_message", true, "Show 'suppressed N times' messages");

        config.save();
    }

    private static <T> void setDefault(String path, T value, String comment) {
        if (!config.contains(path)) {
            config.set(path, value);
            config.setComment(path, comment);
        }
    }

    private static void loadValues() {
        enabled = config.getOrElse("enabled", true);
        debugMode = config.getOrElse("debug_mode", false);

        errorThreshold = config.getOrElse("thresholds.error", 5);
        warnThreshold = config.getOrElse("thresholds.warn", 10);
        infoThreshold = config.getOrElse("thresholds.info", 20);
        debugThreshold = config.getOrElse("thresholds.debug", 3);

        coordinatesWindowMs = config.getOrElse("time_windows.coordinates_ms", 100);
        chunkWindowMs = config.getOrElse("time_windows.chunk_ms", 500);
        generalWindowMs = config.getOrElse("time_windows.general_ms", 2000);

        showCountAfter = config.getOrElse("show_count_after", 100);
        maxBufferSize = config.getOrElse("max_buffer_size", 500);

        fuzzyMatching = config.getOrElse("similarity.fuzzy_matching", true);
        similarityThreshold = config.getOrElse("similarity.threshold", 85);

        normalizeCoordinates = config.getOrElse("normalization.coordinates", true);
        normalizeUUIDs = config.getOrElse("normalization.uuids", true);
        normalizeMemoryAddresses = config.getOrElse("normalization.memory_addresses", true);
        normalizeTimings = config.getOrElse("normalization.timings", true);

        whitelistLoggers = Collections.unmodifiableList(new ArrayList<>(config.getOrElse("filters.whitelist", new ArrayList<>())));
        blacklistLoggers = Collections.unmodifiableList(new ArrayList<>(config.getOrElse("filters.blacklist", new ArrayList<>())));

        systemStreamsEnabled = config.getOrElse("system_streams.enabled", true);
        detectMultiline = config.getOrElse("system_streams.detect_multiline", true);
        multilineTimeoutMs = config.getOrElse("system_streams.multiline_timeout_ms", 50);

        cleanStacktraces = config.getOrElse("stacktraces.clean", true);
        removeCommonFrames = config.getOrElse("stacktraces.remove_common_frames", false);
        maxStacktraceDepth = config.getOrElse("stacktraces.max_depth", 0);

        showSuppressionMessage = config.getOrElse("output.show_suppression_message", true);
    }
}
