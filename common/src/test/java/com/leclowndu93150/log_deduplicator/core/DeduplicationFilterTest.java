package com.leclowndu93150.log_deduplicator.core;

import com.leclowndu93150.log_deduplicator.TestConfigHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class DeduplicationFilterTest {

    private DeduplicationFilter filter;

    @BeforeEach
    void setUp() {
        TestConfigHelper.setDefaults();
        DeduplicationManager.resetForTesting();
        filter = new DeduplicationFilter();
        filter.start();
    }

    @AfterEach
    void tearDown() {
        DeduplicationManager.resetForTesting();
    }

    private String fmt(String msg, Object... params) throws Exception {
        Method m = DeduplicationFilter.class.getDeclaredMethod("formatMessage", String.class, Object[].class);
        m.setAccessible(true);
        return (String) m.invoke(filter, msg, params);
    }

    // ---- Regression: bug #5 — old formatMessage mangled % signs ----

    @Test
    void percentSignPreserved() throws Exception {
        // Before fix: "%" was replaced with "%%" then passed to String.format, doubling percents
        assertEquals("CPU at 100% usage", fmt("CPU at 100% usage"));
    }

    @Test
    void percentWithParamPreserved() throws Exception {
        // Before fix: String.format("CPU at 100%% on host %s", "srv") would blow up
        // or produce "CPU at 100%% on host srv"
        assertEquals("CPU at 100% on host srv1", fmt("CPU at 100% on host {}", "srv1"));
    }

    // ---- Basic placeholder replacement ----

    @Test
    void singlePlaceholder() throws Exception {
        assertEquals("hello Steve", fmt("hello {}", "Steve"));
    }

    @Test
    void multiplePlaceholders() throws Exception {
        assertEquals("a=1 b=2 c=3", fmt("a={} b={} c={}", 1, 2, 3));
    }

    @Test
    void extraParamsIgnored() throws Exception {
        assertEquals("x=1", fmt("x={}", 1, 2, 3));
    }

    @Test
    void fewerParamsLeavesPlaceholder() throws Exception {
        assertEquals("a=1 b={}", fmt("a={} b={}", 1));
    }

    @Test
    void noPlaceholdersNoParams() throws Exception {
        assertEquals("plain message", fmt("plain message"));
    }

    @Test
    void nullReturnsEmpty() throws Exception {
        assertEquals("", fmt(null));
    }

    @Test
    void nullParamsReturnsOriginal() throws Exception {
        assertEquals("hello", fmt("hello", (Object[]) null));
    }

    @Test
    void emptyParamsReturnsOriginal() throws Exception {
        assertEquals("hello", fmt("hello", new Object[]{}));
    }

    @Test
    void nullParamValue() throws Exception {
        assertEquals("value is null", fmt("value is {}", (Object) null));
    }

    @Test
    void bracesNotPlaceholder() throws Exception {
        // Single brace should not be treated as placeholder
        assertEquals("x{y", fmt("x{y"));
    }
}
