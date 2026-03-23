package com.leclowndu93150.log_deduplicator.core;

import com.leclowndu93150.log_deduplicator.Config;
import org.apache.logging.log4j.Level;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

public class JulDeduplicationFilter implements Filter {

    @Override
    public boolean isLoggable(LogRecord record) {
        if (!Config.enabled) {
            return true;
        }

        String message = record.getMessage();
        if (message == null || message.isEmpty()) {
            return true;
        }

        String loggerName = record.getLoggerName();
        Level log4jLevel = mapJulLevel(record.getLevel());

        DeduplicationManager.Result result = DeduplicationManager.getInstance().check(
            message, loggerName, log4jLevel
        );

        return result.isAllowed();
    }

    private static Level mapJulLevel(java.util.logging.Level julLevel) {
        int value = julLevel.intValue();
        if (value >= java.util.logging.Level.SEVERE.intValue()) {
            return Level.ERROR;
        } else if (value >= java.util.logging.Level.WARNING.intValue()) {
            return Level.WARN;
        } else if (value >= java.util.logging.Level.INFO.intValue()) {
            return Level.INFO;
        } else if (value >= java.util.logging.Level.FINE.intValue()) {
            return Level.DEBUG;
        } else {
            return Level.TRACE;
        }
    }
}
