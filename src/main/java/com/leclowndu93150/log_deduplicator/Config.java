package com.leclowndu93150.log_deduplicator;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileWatcher;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Config {
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("log_deduplicator.toml");
    private static CommentedFileConfig config;

    public static boolean enabled;
    public static boolean debugMode;
    public static int errorThreshold;
    public static int warnThreshold;
    public static int infoThreshold;
    public static int debugThreshold;
    public static int coordinatesWindowMs;
    public static int chunkWindowMs;
    public static int generalWindowMs;
    public static int showCountAfter;
    public static int maxBufferSize;
    public static boolean fuzzyMatching;
    public static int similarityThreshold;
    public static boolean normalizeCoordinates;
    public static boolean normalizeUUIDs;
    public static boolean normalizeMemoryAddresses;
    public static boolean normalizeTimings;
    public static List<String> whitelistLoggers;
    public static List<String> blacklistLoggers;
    public static boolean systemStreamsEnabled;
    public static boolean detectMultiline;
    public static int multilineTimeoutMs;
    public static boolean cleanStacktraces;
    public static boolean removeCommonFrames;
    public static int maxStacktraceDepth;
    public static boolean showSuppressionMessage;

    public static void init() {
        config = CommentedFileConfig.builder(CONFIG_PATH)
            .autosave()
            .preserveInsertionOrder()
            .build();

        config.load();

        setDefaults();
        loadValues();

        FileWatcher.defaultInstance().addWatch(CONFIG_PATH, Config::reload);
    }

    private static void reload() {
        config.load();
        loadValues();
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

        whitelistLoggers = config.getOrElse("filters.whitelist", new ArrayList<>());
        blacklistLoggers = config.getOrElse("filters.blacklist", new ArrayList<>());

        systemStreamsEnabled = config.getOrElse("system_streams.enabled", true);
        detectMultiline = config.getOrElse("system_streams.detect_multiline", true);
        multilineTimeoutMs = config.getOrElse("system_streams.multiline_timeout_ms", 50);

        cleanStacktraces = config.getOrElse("stacktraces.clean", true);
        removeCommonFrames = config.getOrElse("stacktraces.remove_common_frames", false);
        maxStacktraceDepth = config.getOrElse("stacktraces.max_depth", 0);

        showSuppressionMessage = config.getOrElse("output.show_suppression_message", true);
    }
}
