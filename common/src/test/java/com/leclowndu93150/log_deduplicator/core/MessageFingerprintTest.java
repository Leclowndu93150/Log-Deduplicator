package com.leclowndu93150.log_deduplicator.core;

import com.leclowndu93150.log_deduplicator.TestConfigHelper;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageFingerprintTest {

    @BeforeEach
    void setUp() {
        TestConfigHelper.setDefaults();
    }

    // ---- Regression: bug #2 — null level in fuzzyMatch NPE'd ----

    @Test
    void fuzzyMatchWithNullLevelOnThisDoesNotNPE() {
        var a = new MessageFingerprint("hello", "test", null, false);
        var b = new MessageFingerprint("hello", "test", Level.INFO, false);
        // Before fix: this.level.equals(other.level) threw NPE
        assertDoesNotThrow(() -> a.fuzzyMatch(b));
        assertEquals(0, a.fuzzyMatch(b)); // different levels → 0
    }

    @Test
    void fuzzyMatchWithNullLevelOnOtherDoesNotNPE() {
        var a = new MessageFingerprint("hello", "test", Level.INFO, false);
        var b = new MessageFingerprint("hello", "test", null, false);
        assertDoesNotThrow(() -> a.fuzzyMatch(b));
        assertEquals(0, a.fuzzyMatch(b));
    }

    @Test
    void fuzzyMatchBothNullLevelsEquals100() {
        var a = new MessageFingerprint("hello world", null, null, false);
        var b = new MessageFingerprint("hello world", null, null, false);
        assertEquals(100, a.fuzzyMatch(b));
    }

    @Test
    void fuzzyMatchNullOtherReturns0() {
        var a = new MessageFingerprint("hello", "test", Level.INFO, false);
        assertEquals(0, a.fuzzyMatch(null));
    }

    // ---- Equality basics ----

    @Test
    void sameContentEquals() {
        var a = new MessageFingerprint("msg", "logger", Level.WARN, false);
        var b = new MessageFingerprint("msg", "logger", Level.WARN, false);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentMessageNotEqual() {
        var a = new MessageFingerprint("aaa", "test", Level.INFO, false);
        var b = new MessageFingerprint("bbb", "test", Level.INFO, false);
        assertNotEquals(a, b);
    }

    @Test
    void differentLevelNotEqual() {
        var a = new MessageFingerprint("msg", "test", Level.INFO, false);
        var b = new MessageFingerprint("msg", "test", Level.ERROR, false);
        assertNotEquals(a, b);
    }

    @Test
    void differentLoggerNotEqual() {
        var a = new MessageFingerprint("msg", "A", Level.INFO, false);
        var b = new MessageFingerprint("msg", "B", Level.INFO, false);
        assertNotEquals(a, b);
    }

    @Test
    void nullLoggerBothSidesEqual() {
        var a = new MessageFingerprint("msg", null, Level.INFO, false);
        var b = new MessageFingerprint("msg", null, Level.INFO, false);
        assertEquals(a, b);
    }

    @Test
    void nullVsNonNullLoggerNotEqual() {
        var a = new MessageFingerprint("msg", null, Level.INFO, false);
        var b = new MessageFingerprint("msg", "test", Level.INFO, false);
        assertNotEquals(a, b);
    }

    @Test
    void nullMessageBecomesEmpty() {
        var fp = new MessageFingerprint(null, "test", Level.INFO, false);
        assertEquals("", fp.getNormalizedMessage());
    }

    @Test
    void notEqualToOtherTypes() {
        var fp = new MessageFingerprint("msg", "test", Level.INFO, false);
        assertNotEquals("msg", fp);
        assertNotEquals(null, fp);
    }

    @Test
    void equalToSelf() {
        var fp = new MessageFingerprint("msg", "test", Level.INFO, false);
        assertEquals(fp, fp);
    }

    // ---- Normalization ----

    @Test
    void normalizedCoordinatesMatch() {
        var a = new MessageFingerprint("Entity at (1.0, 2.0, 3.0)", "test", Level.INFO, true);
        var b = new MessageFingerprint("Entity at (9.0, 8.0, 7.0)", "test", Level.INFO, true);
        assertEquals(a, b, "Different coordinates should normalize to same fingerprint");
    }

    @Test
    void normalizedUUIDsMatch() {
        var a = new MessageFingerprint("Player 550e8400-e29b-41d4-a716-446655440000 left", "test", Level.INFO, true);
        var b = new MessageFingerprint("Player a1b2c3d4-e5f6-7890-abcd-ef1234567890 left", "test", Level.INFO, true);
        assertEquals(a, b, "Different UUIDs should normalize to same fingerprint");
    }

    // ---- Fuzzy matching ----

    @Test
    void similarMessagesScoreHigh() {
        var a = new MessageFingerprint("Player Steve joined the game", "test", Level.INFO, false);
        var b = new MessageFingerprint("Player Steve joined the gane", "test", Level.INFO, false);
        assertTrue(a.fuzzyMatch(b) > 80);
    }

    @Test
    void totallyDifferentMessagesScoreLow() {
        var a = new MessageFingerprint("hello world", "test", Level.INFO, false);
        var b = new MessageFingerprint("xyz 12345", "test", Level.INFO, false);
        assertTrue(a.fuzzyMatch(b) < 50);
    }

    @Test
    void differentLevelsAlwaysReturn0() {
        var a = new MessageFingerprint("same msg", "test", Level.INFO, false);
        var b = new MessageFingerprint("same msg", "test", Level.ERROR, false);
        assertEquals(0, a.fuzzyMatch(b));
    }

    @Test
    void longStringsDoNotCrash() {
        // Levenshtein has a >200 char shortcut — verify it doesn't blow up
        var a = new MessageFingerprint("A".repeat(500), "test", Level.INFO, false);
        var b = new MessageFingerprint("A".repeat(498) + "BB", "test", Level.INFO, false);
        int score = a.fuzzyMatch(b);
        assertTrue(score >= 0 && score <= 100);
    }
}
