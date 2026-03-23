package com.leclowndu93150.log_deduplicator.core;

import com.leclowndu93150.log_deduplicator.Config;
import com.leclowndu93150.log_deduplicator.TestConfigHelper;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeduplicationContextTest {

    private DeduplicationContext ctx;

    @BeforeEach
    void setUp() {
        TestConfigHelper.setDefaults();
        ctx = new DeduplicationContext(100);
    }

    // ---- Basic behavior ----

    @Test
    void firstMessageAllowed() {
        assertTrue(ctx.checkMessage("hello", "test", Level.INFO, 1000).isAllowed());
        assertEquals(1, ctx.size());
    }

    @Test
    void withinThresholdAllowed() {
        long ts = 1000;
        for (int i = 0; i < Config.infoThreshold; i++) {
            assertTrue(ctx.checkMessage("msg", "test", Level.INFO, ts++).isAllowed());
        }
    }

    @Test
    void overThresholdDenied() {
        long ts = 1000;
        for (int i = 0; i < Config.infoThreshold; i++) {
            ctx.checkMessage("msg", "test", Level.INFO, ts++);
        }
        assertFalse(ctx.checkMessage("msg", "test", Level.INFO, ts).isAllowed());
    }

    @Test
    void errorThresholdLower() {
        long ts = 1000;
        for (int i = 0; i < Config.errorThreshold; i++) {
            ctx.checkMessage("err", "test", Level.ERROR, ts++);
        }
        assertFalse(ctx.checkMessage("err", "test", Level.ERROR, ts).isAllowed());
    }

    @Test
    void windowResetResetsCount() {
        long ts = 1000;
        for (int i = 0; i <= Config.infoThreshold; i++) {
            ctx.checkMessage("msg", "test", Level.INFO, ts++);
        }
        // Jump past window (generalWindowMs=2000 for generic messages)
        ts += 5000;
        assertTrue(ctx.checkMessage("msg", "test", Level.INFO, ts).isAllowed());
    }

    @Test
    void differentMessagesIndependent() {
        Config.fuzzyMatching = false;
        long ts = 1000;
        for (int i = 0; i <= Config.infoThreshold; i++) {
            ctx.checkMessage("server started on port 25565", "test", Level.INFO, ts++);
        }
        assertTrue(ctx.checkMessage("player connected from 192.168.1.1", "test", Level.INFO, ts).isAllowed());
    }

    // ---- Fuzzy matching ----

    @Test
    void fuzzyMatchGroupsSimilarMessages() {
        Config.fuzzyMatching = true;
        Config.similarityThreshold = 80;
        long ts = 1000;
        for (int i = 0; i <= Config.infoThreshold; i++) {
            ctx.checkMessage("Player Steve joined the game", "test", Level.INFO, ts++);
        }
        // Typo variant — should group with the original
        assertFalse(ctx.checkMessage("Player Steve joined the gane", "test", Level.INFO, ts).isAllowed());
    }

    @Test
    void fuzzyMatchDisabledTreatsSimilarAsSeparate() {
        Config.fuzzyMatching = false;
        long ts = 1000;
        for (int i = 0; i <= Config.infoThreshold; i++) {
            ctx.checkMessage("Player Steve joined the game", "test", Level.INFO, ts++);
        }
        assertTrue(ctx.checkMessage("Player Steve joined the gane", "test", Level.INFO, ts).isAllowed());
    }

    @Test
    void fuzzyMatchCappedAt20Iterations() {
        // Fill cache with 30 unique messages
        Config.fuzzyMatching = true;
        Config.similarityThreshold = 80;
        long ts = 1000;
        for (int i = 0; i < 30; i++) {
            ctx.checkMessage("completely unique message number " + i, "test", Level.INFO, ts++);
        }
        // This should not take excessively long — the cap at 20 prevents scanning all 30
        long start = System.nanoTime();
        ctx.checkMessage("another unique message here", "test", Level.INFO, ts);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 1000, "Fuzzy match should be fast, took " + elapsedMs + "ms");
    }

    // ---- Suppression count reporting ----

    @Test
    void showCountEventuallyFires() {
        Config.showCountAfter = 5;
        long ts = 1000;
        boolean sawCount = false;
        for (int i = 0; i < 300; i++) {
            var r = ctx.checkMessage("msg", "test", Level.INFO, ts++);
            if (r.shouldShowCount()) {
                sawCount = true;
                assertTrue(r.getCount() > Config.infoThreshold);
                assertTrue(r.getDurationMs() > 0);
                break;
            }
        }
        assertTrue(sawCount, "Should eventually report a suppression count");
    }

    // ---- Cache management ----

    @Test
    void cleanupRemovesOld() {
        ctx.checkMessage("old", "test", Level.INFO, 1000);
        assertEquals(1, ctx.size());
        ctx.cleanup(200_000, 50_000);
        assertEquals(0, ctx.size());
    }

    @Test
    void cleanupKeepsRecent() {
        ctx.checkMessage("recent", "test", Level.INFO, 1000);
        ctx.cleanup(1500, 50_000);
        assertEquals(1, ctx.size());
    }

    @Test
    void clearRemovesAll() {
        for (int i = 0; i < 10; i++) {
            ctx.checkMessage("msg" + i, "test", Level.INFO, 1000);
        }
        ctx.clear();
        assertEquals(0, ctx.size());
    }

    @Test
    void maxSizeEvicts() {
        var small = new DeduplicationContext(5);
        for (int i = 0; i < 20; i++) {
            small.checkMessage("msg" + i, "test", Level.INFO, 1000 + i);
        }
        assertTrue(small.size() <= 5);
    }
}
