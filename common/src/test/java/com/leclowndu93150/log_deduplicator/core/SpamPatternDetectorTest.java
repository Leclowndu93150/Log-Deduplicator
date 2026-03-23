package com.leclowndu93150.log_deduplicator.core;

import com.leclowndu93150.log_deduplicator.Config;
import com.leclowndu93150.log_deduplicator.TestConfigHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpamPatternDetectorTest {

    @BeforeEach
    void setUp() {
        TestConfigHelper.setDefaults();
    }

    // ---- Regression: bug #3 — isExceptionMessage was wrong ----

    @Test
    void realExceptionClassDetected() {
        assertTrue(SpamPatternDetector.isExceptionMessage("java.lang.NullPointerException: msg"));
    }

    @Test
    void realErrorClassDetected() {
        // Before fix: this was rejected because message also contained "[ERROR]" check was inverted
        assertTrue(SpamPatternDetector.isExceptionMessage("java.lang.OutOfMemoryError: heap"));
    }

    @Test
    void bareWordErrorWithoutContextIsNotException() {
        // "Error" alone without a dot or colon is just prose
        assertFalse(SpamPatternDetector.isExceptionMessage("Error"));
    }

    @Test
    void errorInSentenceIsNotException() {
        assertFalse(SpamPatternDetector.isExceptionMessage("An error occurred while processing"));
    }

    @Test
    void errorWithColonIsException() {
        assertTrue(SpamPatternDetector.isExceptionMessage("OutOfMemoryError: Java heap space"));
    }

    @Test
    void normalMessageNotException() {
        assertFalse(SpamPatternDetector.isExceptionMessage("Server started successfully"));
    }

    // ---- Regression: bug #4 — isStackTraceLine dead code for whitespace ----

    @Test
    void whitespaceOnlyLineIsStackTraceLine() {
        // Before fix: empty string was caught at line 72, but whitespace-only
        // went through to .trim().isEmpty() which was unreachable (dead code)
        assertTrue(SpamPatternDetector.isStackTraceLine("   "));
        assertTrue(SpamPatternDetector.isStackTraceLine("\t"));
    }

    @Test
    void emptyIsNotStackTraceLine() {
        assertFalse(SpamPatternDetector.isStackTraceLine(""));
    }

    @Test
    void nullIsNotStackTraceLine() {
        assertFalse(SpamPatternDetector.isStackTraceLine(null));
    }

    @Test
    void atLineDetected() {
        assertTrue(SpamPatternDetector.isStackTraceLine("\tat com.example.Foo.bar(Foo.java:42)"));
    }

    @Test
    void causedByDetected() {
        assertTrue(SpamPatternDetector.isStackTraceLine("Caused by: java.lang.NPE"));
    }

    @Test
    void suppressedDetected() {
        assertTrue(SpamPatternDetector.isStackTraceLine("Suppressed: java.io.IOException"));
    }

    @Test
    void ellipsisDetected() {
        assertTrue(SpamPatternDetector.isStackTraceLine("... 15 more"));
    }

    @Test
    void normalLineNotStackTrace() {
        assertFalse(SpamPatternDetector.isStackTraceLine("Server started on port 25565"));
    }

    // ---- normalizeMessage ----

    @Test
    void nullNormalizesToEmpty() {
        assertEquals("", SpamPatternDetector.normalizeMessage(null));
    }

    @Test
    void emptyNormalizesToEmpty() {
        assertEquals("", SpamPatternDetector.normalizeMessage(""));
    }

    @Test
    void plainTextUnchanged() {
        assertEquals("hello world", SpamPatternDetector.normalizeMessage("hello world"));
    }

    @Test
    void chunkCoordsNormalized() {
        assertEquals("chunk [*, *]", SpamPatternDetector.normalizeMessage("chunk [14, -7]"));
    }

    @Test
    void entityCoordsNormalized() {
        assertEquals("at (*, *, *)", SpamPatternDetector.normalizeMessage("at (1.0, 2.0, 3.0)"));
    }

    @Test
    void uuidNormalized() {
        assertEquals("player *UUID*", SpamPatternDetector.normalizeMessage(
            "player 550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    void memoryAddressNormalized() {
        assertEquals("obj@*", SpamPatternDetector.normalizeMessage("obj@1a2b3c4d5e"));
    }

    @Test
    void millisecondsNormalized() {
        assertEquals("took *ms", SpamPatternDetector.normalizeMessage("took 1234ms"));
    }

    @Test
    void ticksNormalized() {
        assertEquals("ran * ticks", SpamPatternDetector.normalizeMessage("ran 42 ticks"));
    }

    @Test
    void multiplePatterns() {
        String input = "Entity 550e8400-e29b-41d4-a716-446655440000 at (1.0, 2.0, 3.0) took 50ms";
        String result = SpamPatternDetector.normalizeMessage(input);
        assertEquals("Entity *UUID* at (*, *, *) took *ms", result);
    }

    @Test
    void disabledNormalizationPassesThrough() {
        Config.normalizeCoordinates = false;
        Config.normalizeUUIDs = false;
        Config.normalizeMemoryAddresses = false;
        Config.normalizeTimings = false;
        String input = "at (1.0, 2.0, 3.0) 550e8400-e29b-41d4-a716-446655440000 @abc123 500ms";
        assertEquals(input, SpamPatternDetector.normalizeMessage(input));
    }

    // ---- estimateSpamWindowMs ----

    @Test
    void nullUsesGeneralWindow() {
        assertEquals(Config.generalWindowMs, SpamPatternDetector.estimateSpamWindowMs(null));
    }

    @Test
    void chunkKeywordUsesChunkWindow() {
        assertEquals(Config.chunkWindowMs, SpamPatternDetector.estimateSpamWindowMs("Loading chunk"));
    }

    @Test
    void entityKeywordUsesCoordinatesWindow() {
        assertEquals(Config.coordinatesWindowMs, SpamPatternDetector.estimateSpamWindowMs("entity spawned"));
    }

    @Test
    void saveKeywordUsesDoubleGeneralWindow() {
        assertEquals(Config.generalWindowMs * 2, SpamPatternDetector.estimateSpamWindowMs("saving world"));
    }

    @Test
    void genericUsesGeneralWindow() {
        assertEquals(Config.generalWindowMs, SpamPatternDetector.estimateSpamWindowMs("some random line"));
    }
}
