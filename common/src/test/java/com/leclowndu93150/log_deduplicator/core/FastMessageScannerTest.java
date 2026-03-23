package com.leclowndu93150.log_deduplicator.core;

import com.leclowndu93150.log_deduplicator.TestConfigHelper;
import com.leclowndu93150.log_deduplicator.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FastMessageScannerTest {

    @BeforeEach
    void setUp() {
        TestConfigHelper.setDefaults();
    }

    @Test
    void nullReturnsEmpty() {
        assertEquals("", FastMessageScanner.normalize(null));
    }

    @Test
    void emptyReturnsEmpty() {
        assertEquals("", FastMessageScanner.normalize(""));
    }

    @Test
    void plainTextUnchanged() {
        assertEquals("hello world", FastMessageScanner.normalize("hello world"));
    }

    @Test
    void noDigitsNoAtUnchanged() {
        assertEquals("Server started successfully", FastMessageScanner.normalize("Server started successfully"));
    }

    // ---- UUIDs ----

    @Test
    void uuid() {
        assertEquals("Player *UUID* joined",
            FastMessageScanner.normalize("Player 550e8400-e29b-41d4-a716-446655440000 joined"));
    }

    @Test
    void multipleUUIDs() {
        String input = "From 550e8400-e29b-41d4-a716-446655440000 to a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String result = FastMessageScanner.normalize(input);
        assertEquals("From *UUID* to *UUID*", result);
    }

    @Test
    void uuidDisabled() {
        Config.normalizeUUIDs = false;
        String input = "Player 550e8400-e29b-41d4-a716-446655440000 joined";
        assertEquals(input, FastMessageScanner.normalize(input));
    }

    // ---- Memory addresses ----

    @Test
    void memoryAddress() {
        assertEquals("Object@* created", FastMessageScanner.normalize("Object@1a2b3c4d5e created"));
    }

    @Test
    void shortHexNotAddress() {
        // Less than 6 hex chars after @ should not match
        assertEquals("email@abc", FastMessageScanner.normalize("email@abc"));
    }

    @Test
    void memoryAddressDisabled() {
        Config.normalizeMemoryAddresses = false;
        assertEquals("Object@1a2b3c4d5e", FastMessageScanner.normalize("Object@1a2b3c4d5e"));
    }

    // ---- Entity coordinates: (float, float, float) ----

    @Test
    void entityCoords() {
        assertEquals("Entity at (*, *, *)",
            FastMessageScanner.normalize("Entity at (123.456, 64.000, -789.012)"));
    }

    @Test
    void entityCoordsNegative() {
        assertEquals("pos (*, *, *) end",
            FastMessageScanner.normalize("pos (-1.0, -2.0, -3.0) end"));
    }

    // ---- Chunk coordinates: [int, int] ----

    @Test
    void chunkCoords() {
        assertEquals("Chunk [*, *] loaded",
            FastMessageScanner.normalize("Chunk [14, -7] loaded"));
    }

    // ---- Timings ----

    @Test
    void milliseconds() {
        assertEquals("Took *ms to complete",
            FastMessageScanner.normalize("Took 1234ms to complete"));
    }

    @Test
    void ticks() {
        assertEquals("Ran * ticks",
            FastMessageScanner.normalize("Ran 42 ticks"));
    }

    @Test
    void timingsDisabled() {
        Config.normalizeTimings = false;
        assertEquals("Took 500ms", FastMessageScanner.normalize("Took 500ms"));
    }

    // ---- Float coordinates ----

    @Test
    void floatCoordPair() {
        assertEquals("Moved to *",
            FastMessageScanner.normalize("Moved to 123.45, -67.89"));
    }

    @Test
    void floatTriple() {
        assertEquals("at *",
            FastMessageScanner.normalize("at 1.0, 2.0, 3.0"));
    }

    @Test
    void coordsDisabled() {
        Config.normalizeCoordinates = false;
        assertEquals("Chunk [14, -7]", FastMessageScanner.normalize("Chunk [14, -7]"));
    }

    // ---- Combined patterns ----

    @Test
    void multiplePatterns() {
        String input = "Entity 550e8400-e29b-41d4-a716-446655440000 at (1.0, 2.0, 3.0) took 50ms";
        String result = FastMessageScanner.normalize(input);
        assertEquals("Entity *UUID* at (*, *, *) took *ms", result);
    }

    @Test
    void chunkAndTiming() {
        String input = "Chunk [14, -7] loaded in 250ms";
        assertEquals("Chunk [*, *] loaded in *ms", FastMessageScanner.normalize(input));
    }

    @Test
    void memoryAndUUID() {
        String input = "Cache@1a2b3c4d5e for 550e8400-e29b-41d4-a716-446655440000";
        String result = FastMessageScanner.normalize(input);
        assertEquals("Cache@* for *UUID*", result);
    }

    // ---- Edge cases ----

    @Test
    void allDisabled() {
        Config.normalizeCoordinates = false;
        Config.normalizeUUIDs = false;
        Config.normalizeMemoryAddresses = false;
        Config.normalizeTimings = false;
        String input = "Entity at (1.0, 2.0, 3.0) 550e8400-e29b-41d4-a716-446655440000 @abc123def 500ms";
        assertEquals(input, FastMessageScanner.normalize(input));
    }

    @Test
    void numberInWordNotReplaced() {
        // "log4j" should not have "4" replaced
        String input = "org.apache.logging.log4j.core";
        String result = FastMessageScanner.normalize(input);
        // The number is surrounded by word chars, should not be treated as a coordinate
        assertTrue(result.contains("log4j"), "log4j should not be mangled: " + result);
    }

    @Test
    void realMinecraftMessages() {
        assertEquals("Failed to save chunk [*, *]",
            FastMessageScanner.normalize("Failed to save chunk [14, -7]"));

        assertEquals("Block entity at invalid position: (*, *, *)",
            FastMessageScanner.normalize("Block entity at invalid position: (100.0, 64.0, -200.0)"));

        assertEquals("Loaded *ms advancements",
            FastMessageScanner.normalize("Loaded 42ms advancements"));
    }

    @Test
    void dontBreakResourceLocations() {
        // minecraft:stone should not be mangled
        String input = "Recipe minecraft:stone references unknown item modid:item_name";
        String result = FastMessageScanner.normalize(input);
        assertTrue(result.contains("minecraft:stone"), "Resource location mangled: " + result);
    }
}
