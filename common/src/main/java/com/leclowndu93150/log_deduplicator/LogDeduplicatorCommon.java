package com.leclowndu93150.log_deduplicator;

import com.leclowndu93150.log_deduplicator.core.DeduplicationFilter;
import com.leclowndu93150.log_deduplicator.core.DeduplicationManager;
import com.leclowndu93150.log_deduplicator.core.JulDeduplicationFilter;
import com.leclowndu93150.log_deduplicator.stream.SmartPrintStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.nio.file.Path;

public class LogDeduplicatorCommon {
    public static final String MOD_ID = "log_deduplicator";
    public static final Logger LOGGER = LoggerFactory.getLogger("LogDeduplicator");

    private static DeduplicationFilter FILTER;
    private static PrintStream originalOut;
    private static PrintStream originalErr;
    private static SmartPrintStream dedupOut;
    private static SmartPrintStream dedupErr;
    private static volatile boolean filtersInjected;

    public static void init(Path configDir) {
        Config.init(configDir);
        LOGGER.info("Log Deduplicator config loaded");
    }

    public static void lateInit() {
        if (filtersInjected) return;
        filtersInjected = true;
        injectDeduplicationFilter();
        injectJulFilter();
        wrapSystemStreams();
        LOGGER.info("Log Deduplicator filters injected");
    }

    public static void reinjectFilter() {
        injectDeduplicationFilter();
    }

    public static void shutdown() {
        // Always restore if we wrapped them, regardless of current config state
        restoreSystemStreams();
        DeduplicationManager.getInstance().shutdown();
    }

    private static void injectDeduplicationFilter() {
        try {
            if (FILTER == null) {
                FILTER = new DeduplicationFilter();
                FILTER.start();
            }
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            ctx.getConfiguration().addFilter(FILTER);
            ctx.updateLoggers();
        } catch (Exception e) {
            LOGGER.error("Failed to inject Log4j deduplication filter", e);
        }
    }

    private static void injectJulFilter() {
        try {
            java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
            rootLogger.setFilter(new JulDeduplicationFilter());
        } catch (Exception e) {
            LOGGER.error("Failed to inject JUL deduplication filter", e);
        }
    }

    private static void wrapSystemStreams() {
        if (originalOut != null) {
            return;
        }
        try {
            originalOut = System.out;
            originalErr = System.err;
            dedupOut = new SmartPrintStream(originalOut, false);
            dedupErr = new SmartPrintStream(originalErr, true);
            System.setOut(dedupOut);
            System.setErr(dedupErr);
        } catch (Exception e) {
            LOGGER.error("Failed to wrap System streams", e);
        }
    }

    private static void restoreSystemStreams() {
        if (originalOut != null && originalErr != null) {
            try {
                System.setOut(originalOut);
                System.setErr(originalErr);
                if (dedupOut != null) {
                    dedupOut.flush();
                }
                if (dedupErr != null) {
                    dedupErr.flush();
                }
                LOGGER.info("System streams restored");
            } catch (Exception e) {
                LOGGER.error("Failed to restore System streams", e);
            }
        }
    }
}
