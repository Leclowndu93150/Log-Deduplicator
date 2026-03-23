package com.leclowndu93150.log_deduplicator.core;

import com.leclowndu93150.log_deduplicator.Config;
import com.leclowndu93150.log_deduplicator.TestConfigHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class StackTraceCleanerTest {

    @BeforeEach
    void setUp() {
        TestConfigHelper.setDefaults();
    }

    private static final String SAMPLE_TRACE = String.join("\n",
        "java.lang.NullPointerException: oops",
        "\tat com.example.Foo.bar(Foo.java:42)",
        "\tat com.example.Foo.bar(Foo.java:42)",
        "\tat com.example.Baz.qux(Baz.java:10)",
        "\tat java.lang.reflect.Method.invoke(Method.java:580)",
        "\tat net.neoforged.bus.EventBus.post(EventBus.java:100)"
    );

    // ---- cleanStackTrace ----

    @Nested
    class CleanStackTrace {

        @Test
        void nullReturnsNull() {
            assertNull(StackTraceCleaner.cleanStackTrace(null));
        }

        @Test
        void emptyReturnsEmpty() {
            assertEquals("", StackTraceCleaner.cleanStackTrace(""));
        }

        @Test
        void disabledReturnsOriginal() {
            Config.cleanStacktraces = false;
            assertEquals(SAMPLE_TRACE, StackTraceCleaner.cleanStackTrace(SAMPLE_TRACE));
        }

        @Test
        void removesDuplicateFrames() {
            String result = StackTraceCleaner.cleanStackTrace(SAMPLE_TRACE);
            // The duplicate "com.example.Foo.bar" should be removed
            int count = countOccurrences(result, "com.example.Foo.bar");
            assertEquals(1, count, "Duplicate frame should be removed");
        }

        @Test
        void preservesCausedBy() {
            String trace = String.join("\n",
                "java.lang.RuntimeException: wrapper",
                "\tat com.example.A.method(A.java:1)",
                "Caused by: java.lang.NullPointerException",
                "\tat com.example.A.method(A.java:1)"
            );
            String result = StackTraceCleaner.cleanStackTrace(trace);
            assertTrue(result.contains("Caused by:"));
            // After "Caused by:", seenFrames is reset, so the duplicate should be kept
            assertEquals(2, countOccurrences(result, "com.example.A.method"));
        }

        @Test
        void preservesEllipsis() {
            String trace = String.join("\n",
                "java.lang.Exception: test",
                "\tat com.example.A.a(A.java:1)",
                "... 5 more"
            );
            String result = StackTraceCleaner.cleanStackTrace(trace);
            assertTrue(result.contains("... 5 more"));
        }

        @Test
        void removeCommonFramesWhenEnabled() {
            Config.removeCommonFrames = true;
            String result = StackTraceCleaner.cleanStackTrace(SAMPLE_TRACE);
            assertFalse(result.contains("java.lang.reflect"), "Reflection frame should be removed");
            assertFalse(result.contains("net.neoforged.bus"), "NeoForge bus frame should be removed");
            assertTrue(result.contains("com.example.Foo.bar"), "App frame should be kept");
        }

        @Test
        void maxDepthTruncates() {
            Config.maxStacktraceDepth = 1;
            String result = StackTraceCleaner.cleanStackTrace(SAMPLE_TRACE);
            // Should only have 1 "at" frame, then "... N more"
            int atCount = countOccurrences(result, "\tat ");
            assertEquals(1, atCount, "Should have exactly 1 frame after max depth");
            assertTrue(result.contains("... "), "Should have ellipsis for remaining frames");
        }
    }

    // ---- isStackTraceMessage ----

    @Nested
    class IsStackTraceMessage {

        @Test
        void nullReturnsFalse() {
            assertFalse(StackTraceCleaner.isStackTraceMessage(null));
        }

        @Test
        void containsExceptionReturnsTrue() {
            assertTrue(StackTraceCleaner.isStackTraceMessage("java.lang.NullPointerException: null"));
        }

        @Test
        void containsErrorReturnsTrue() {
            assertTrue(StackTraceCleaner.isStackTraceMessage("java.lang.OutOfMemoryError"));
        }

        @Test
        void containsTabAtReturnsTrue() {
            assertTrue(StackTraceCleaner.isStackTraceMessage("something\n\tat com.example.Foo.bar(Foo.java:1)"));
        }

        @Test
        void containsCausedByReturnsTrue() {
            assertTrue(StackTraceCleaner.isStackTraceMessage("Caused by: something"));
        }

        @Test
        void normalMessageReturnsFalse() {
            assertFalse(StackTraceCleaner.isStackTraceMessage("Server is running fine"));
        }
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
