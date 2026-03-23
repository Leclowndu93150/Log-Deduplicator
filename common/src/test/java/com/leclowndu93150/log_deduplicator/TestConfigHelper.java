package com.leclowndu93150.log_deduplicator;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Sets Config static fields to sensible defaults for testing without needing a real TOML file.
 */
public class TestConfigHelper {

    public static void setDefaults() {
        Config.enabled = true;
        Config.debugMode = false;
        Config.errorThreshold = 5;
        Config.warnThreshold = 10;
        Config.infoThreshold = 20;
        Config.debugThreshold = 3;
        Config.coordinatesWindowMs = 100;
        Config.chunkWindowMs = 500;
        Config.generalWindowMs = 2000;
        Config.showCountAfter = 100;
        Config.maxBufferSize = 500;
        Config.fuzzyMatching = true;
        Config.similarityThreshold = 85;
        Config.normalizeCoordinates = true;
        Config.normalizeUUIDs = true;
        Config.normalizeMemoryAddresses = true;
        Config.normalizeTimings = true;
        Config.whitelistLoggers = Collections.emptyList();
        Config.blacklistLoggers = Collections.emptyList();
        Config.systemStreamsEnabled = true;
        Config.detectMultiline = true;
        Config.multilineTimeoutMs = 50;
        Config.cleanStacktraces = true;
        Config.removeCommonFrames = false;
        Config.maxStacktraceDepth = 0;
        Config.showSuppressionMessage = true;
    }
}
