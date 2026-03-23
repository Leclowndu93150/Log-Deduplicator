package com.leclowndu93150.log_deduplicator;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfigScreen {

    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("Log Deduplicator"))
            .category(generalCategory())
            .category(thresholdsCategory())
            .category(timeWindowsCategory())
            .category(similarityCategory())
            .category(normalizationCategory())
            .category(systemStreamsCategory())
            .category(stacktracesCategory())
            .save(Config::save)
            .build()
            .generateScreen(parent);
    }

    private static ConfigCategory generalCategory() {
        return ConfigCategory.createBuilder()
            .name(Component.literal("General"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enabled"))
                .description(OptionDescription.of(Component.literal("Master switch to enable/disable deduplication")))
                .binding(Binding.generic(true, () -> Config.enabled, v -> Config.enabled = v))
                .controller(BooleanControllerBuilder::create)
                .build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Debug Mode"))
                .description(OptionDescription.of(Component.literal("Disables deduplication for troubleshooting")))
                .binding(Binding.generic(false, () -> Config.debugMode, v -> Config.debugMode = v))
                .controller(BooleanControllerBuilder::create)
                .build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Show Suppression Messages"))
                .description(OptionDescription.of(Component.literal("Show 'suppressed N times' messages in the log")))
                .binding(Binding.generic(true, () -> Config.showSuppressionMessage, v -> Config.showSuppressionMessage = v))
                .controller(BooleanControllerBuilder::create)
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Show Count After"))
                .description(OptionDescription.of(Component.literal("Show suppression count after N additional duplicates")))
                .binding(Binding.generic(100, () -> Config.showCountAfter, v -> Config.showCountAfter = v))
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).min(1).max(10000))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Max Buffer Size"))
                .description(OptionDescription.of(Component.literal("Max unique messages tracked")))
                .binding(Binding.generic(500, () -> Config.maxBufferSize, v -> Config.maxBufferSize = v))
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).min(10).max(10000))
                .build())
            .build();
    }

    private static ConfigCategory thresholdsCategory() {
        return ConfigCategory.createBuilder()
            .name(Component.literal("Thresholds"))
            .tooltip(Component.literal("Number of duplicates allowed before suppression begins"))
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Error Threshold"))
                .description(OptionDescription.of(Component.literal("Duplicates allowed before suppressing ERROR/FATAL messages")))
                .binding(Binding.generic(5, () -> Config.errorThreshold, v -> Config.errorThreshold = v))
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 100).step(1))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Warn Threshold"))
                .description(OptionDescription.of(Component.literal("Duplicates allowed before suppressing WARN messages")))
                .binding(Binding.generic(10, () -> Config.warnThreshold, v -> Config.warnThreshold = v))
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 100).step(1))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Info Threshold"))
                .description(OptionDescription.of(Component.literal("Duplicates allowed before suppressing INFO messages")))
                .binding(Binding.generic(20, () -> Config.infoThreshold, v -> Config.infoThreshold = v))
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 100).step(1))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Debug Threshold"))
                .description(OptionDescription.of(Component.literal("Duplicates allowed before suppressing DEBUG/TRACE messages")))
                .binding(Binding.generic(3, () -> Config.debugThreshold, v -> Config.debugThreshold = v))
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 100).step(1))
                .build())
            .build();
    }

    private static ConfigCategory timeWindowsCategory() {
        return ConfigCategory.createBuilder()
            .name(Component.literal("Time Windows"))
            .tooltip(Component.literal("Time windows for grouping duplicate messages (in milliseconds)"))
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Coordinates Window (ms)"))
                .description(OptionDescription.of(Component.literal("Window for coordinate-related spam")))
                .binding(Binding.generic(100, () -> Config.coordinatesWindowMs, v -> Config.coordinatesWindowMs = v))
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).min(10).max(60000))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Chunk Window (ms)"))
                .description(OptionDescription.of(Component.literal("Window for chunk operation spam")))
                .binding(Binding.generic(500, () -> Config.chunkWindowMs, v -> Config.chunkWindowMs = v))
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).min(10).max(60000))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("General Window (ms)"))
                .description(OptionDescription.of(Component.literal("Window for general spam")))
                .binding(Binding.generic(2000, () -> Config.generalWindowMs, v -> Config.generalWindowMs = v))
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).min(100).max(60000))
                .build())
            .build();
    }

    private static ConfigCategory similarityCategory() {
        return ConfigCategory.createBuilder()
            .name(Component.literal("Similarity"))
            .tooltip(Component.literal("Fuzzy matching settings for similar messages"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Fuzzy Matching"))
                .description(OptionDescription.of(Component.literal("Match similar messages with different numbers/values")))
                .binding(Binding.generic(true, () -> Config.fuzzyMatching, v -> Config.fuzzyMatching = v))
                .controller(BooleanControllerBuilder::create)
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Similarity Threshold"))
                .description(OptionDescription.of(Component.literal("0-100: higher = stricter matching")))
                .binding(Binding.generic(85, () -> Config.similarityThreshold, v -> Config.similarityThreshold = v))
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(50, 100).step(1))
                .build())
            .build();
    }

    private static ConfigCategory normalizationCategory() {
        return ConfigCategory.createBuilder()
            .name(Component.literal("Normalization"))
            .tooltip(Component.literal("Normalize variable parts of messages to improve deduplication"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Normalize Coordinates"))
                .description(OptionDescription.of(Component.literal("Replace coordinate values with wildcards")))
                .binding(Binding.generic(true, () -> Config.normalizeCoordinates, v -> Config.normalizeCoordinates = v))
                .controller(BooleanControllerBuilder::create)
                .build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Normalize UUIDs"))
                .description(OptionDescription.of(Component.literal("Replace UUIDs with wildcards")))
                .binding(Binding.generic(true, () -> Config.normalizeUUIDs, v -> Config.normalizeUUIDs = v))
                .controller(BooleanControllerBuilder::create)
                .build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Normalize Memory Addresses"))
                .description(OptionDescription.of(Component.literal("Replace memory addresses with wildcards")))
                .binding(Binding.generic(true, () -> Config.normalizeMemoryAddresses, v -> Config.normalizeMemoryAddresses = v))
                .controller(BooleanControllerBuilder::create)
                .build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Normalize Timings"))
                .description(OptionDescription.of(Component.literal("Replace timing values (ms, ticks) with wildcards")))
                .binding(Binding.generic(true, () -> Config.normalizeTimings, v -> Config.normalizeTimings = v))
                .controller(BooleanControllerBuilder::create)
                .build())
            .build();
    }

    private static ConfigCategory systemStreamsCategory() {
        return ConfigCategory.createBuilder()
            .name(Component.literal("System Streams"))
            .tooltip(Component.literal("Settings for System.out/err deduplication"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enabled"))
                .description(OptionDescription.of(Component.literal("Deduplicate System.out and System.err")))
                .binding(Binding.generic(true, () -> Config.systemStreamsEnabled, v -> Config.systemStreamsEnabled = v))
                .controller(BooleanControllerBuilder::create)
                .build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Detect Multiline"))
                .description(OptionDescription.of(Component.literal("Group multi-line messages (like stack traces) together")))
                .binding(Binding.generic(true, () -> Config.detectMultiline, v -> Config.detectMultiline = v))
                .controller(BooleanControllerBuilder::create)
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Multiline Timeout (ms)"))
                .description(OptionDescription.of(Component.literal("Max time between lines of the same message")))
                .binding(Binding.generic(50, () -> Config.multilineTimeoutMs, v -> Config.multilineTimeoutMs = v))
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).min(10).max(5000))
                .build())
            .build();
    }

    private static ConfigCategory stacktracesCategory() {
        return ConfigCategory.createBuilder()
            .name(Component.literal("Stack Traces"))
            .tooltip(Component.literal("Stack trace cleaning settings"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Clean Stack Traces"))
                .description(OptionDescription.of(Component.literal("Remove duplicate stack frames from repeated traces")))
                .binding(Binding.generic(true, () -> Config.cleanStacktraces, v -> Config.cleanStacktraces = v))
                .controller(BooleanControllerBuilder::create)
                .build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Remove Framework Frames"))
                .description(OptionDescription.of(Component.literal("Remove common framework frames (reflection, FML, etc.)")))
                .binding(Binding.generic(false, () -> Config.removeCommonFrames, v -> Config.removeCommonFrames = v))
                .controller(BooleanControllerBuilder::create)
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Max Stack Depth"))
                .description(OptionDescription.of(Component.literal("Maximum stack trace depth (0 = unlimited)")))
                .binding(Binding.generic(0, () -> Config.maxStacktraceDepth, v -> Config.maxStacktraceDepth = v))
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).min(0).max(200))
                .build())
            .build();
    }
}
