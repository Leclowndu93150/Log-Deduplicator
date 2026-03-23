package com.leclowndu93150.log_deduplicator.stream;

import com.leclowndu93150.log_deduplicator.Config;
import com.leclowndu93150.log_deduplicator.TestConfigHelper;
import com.leclowndu93150.log_deduplicator.core.DeduplicationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class SmartPrintStreamTest {

    private ByteArrayOutputStream capture;
    private SmartPrintStream stream;

    @BeforeEach
    void setUp() {
        TestConfigHelper.setDefaults();
        DeduplicationManager.resetForTesting();
        capture = new ByteArrayOutputStream();
        stream = new SmartPrintStream(new PrintStream(capture), false);
    }

    @AfterEach
    void tearDown() {
        stream.close();
        DeduplicationManager.resetForTesting();
    }

    private String output() {
        stream.flush();
        return capture.toString();
    }

    // ---- Basic passthrough ----

    @Test
    void printlnPassesThrough() {
        stream.println("hello");
        assertTrue(output().contains("hello"));
    }

    @Test
    void printlnNullSafe() {
        stream.println((String) null);
        assertTrue(output().contains("null"));
    }

    @Test
    void writeByteByByte() {
        for (char c : "test\n".toCharArray()) stream.write(c);
        assertTrue(output().contains("test"));
    }

    @Test
    void writeByteBuf() {
        byte[] b = "test\n".getBytes();
        stream.write(b, 0, b.length);
        assertTrue(output().contains("test"));
    }

    @Test
    void flushDrainsPartialLine() {
        stream.write("no newline".getBytes(), 0, 10);
        stream.flush();
        assertTrue(output().contains("no newline"));
    }

    // ---- Deduplication ----

    @Test
    void disabledPassesAll() {
        Config.enabled = false;
        for (int i = 0; i < 50; i++) stream.println("repeated");
        assertEquals(50, count(output(), "repeated"));
    }

    @Test
    void disabledSystemStreamsPassesAll() {
        Config.systemStreamsEnabled = false;
        for (int i = 0; i < 50; i++) stream.println("repeated");
        assertEquals(50, count(output(), "repeated"));
    }

    @Test
    void repeatedMessagesSuppressed() {
        for (int i = 0; i < 50; i++) stream.println("spam");
        int c = count(output(), "spam");
        assertTrue(c >= Config.infoThreshold, "At least threshold should pass: " + c);
        assertTrue(c < 50, "Some should be suppressed: " + c);
    }

    @Test
    void errorStreamUsesLowerThreshold() {
        var errStream = new SmartPrintStream(new PrintStream(capture), true);
        for (int i = 0; i < 30; i++) errStream.println("err");
        errStream.flush();
        int c = count(capture.toString(), "err");
        // ERROR threshold = 5, should suppress much more aggressively
        assertTrue(c < 30, "Error stream should suppress: " + c);
        assertTrue(c >= Config.errorThreshold, "At least error threshold should pass: " + c);
        errStream.close();
    }

    // ---- Multiline ----

    @Test
    void stackTraceGrouped() {
        stream.println("java.lang.NullPointerException: msg");
        stream.println("\tat com.Foo.bar(Foo.java:1)");
        stream.println("\tat com.Baz.qux(Baz.java:2)");
        stream.println("next line"); // triggers flush of grouped trace
        String out = output();
        assertTrue(out.contains("NullPointerException"));
        assertTrue(out.contains("next line"));
    }

    @Test
    void disabledMultilineTreatsLinesIndependently() {
        Config.detectMultiline = false;
        stream.println("java.lang.NullPointerException: msg");
        stream.println("\tat com.Foo.bar(Foo.java:1)");
        assertTrue(output().contains("NullPointerException"));
    }

    // ---- Edge cases ----

    @Test
    void veryLongLine() {
        String big = "x".repeat(10000);
        stream.println(big);
        assertTrue(output().contains(big));
    }

    @Test
    void mixedWriteMethods() {
        stream.println("one");
        stream.write("two\n".getBytes(), 0, 4);
        for (char c : "three\n".toCharArray()) stream.write(c);
        String out = output();
        assertTrue(out.contains("one"));
        assertTrue(out.contains("two"));
        assertTrue(out.contains("three"));
    }

    private int count(String haystack, String needle) {
        return haystack.split(needle, -1).length - 1;
    }
}
