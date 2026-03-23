package com.leclowndu93150.log_deduplicator.core;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

public class DeduplicationFilter extends AbstractFilter {

    public DeduplicationFilter() {
        super(Result.NEUTRAL, Result.NEUTRAL);
    }

    @Override
    public Result filter(LogEvent event) {
        if (DeduplicationManager.isBypassing()) {
            return Result.NEUTRAL;
        }

        if (DeduplicationManager.isSuppressionLogger(event.getLoggerName())) {
            return Result.NEUTRAL;
        }

        DeduplicationManager.Result result = DeduplicationManager.getInstance().check(
            event.getMessage().getFormattedMessage(),
            event.getLoggerName(),
            event.getLevel()
        );

        return result.isAllowed() ? Result.NEUTRAL : Result.DENY;
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        if (DeduplicationManager.isBypassing() || DeduplicationManager.isSuppressionLogger(logger.getName())) {
            return Result.NEUTRAL;
        }

        String formattedMsg = formatMessage(msg, params);
        return checkAndFilter(formattedMsg, logger.getName(), level);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        if (DeduplicationManager.isBypassing() || DeduplicationManager.isSuppressionLogger(logger.getName())) {
            return Result.NEUTRAL;
        }

        return checkAndFilter(String.valueOf(msg), logger.getName(), level);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        if (DeduplicationManager.isBypassing() || DeduplicationManager.isSuppressionLogger(logger.getName())) {
            return Result.NEUTRAL;
        }

        return checkAndFilter(msg.getFormattedMessage(), logger.getName(), level);
    }

    private Result checkAndFilter(String message, String loggerName, Level level) {
        DeduplicationManager.Result result = DeduplicationManager.getInstance().check(message, loggerName, level);
        return result.isAllowed() ? Result.NEUTRAL : Result.DENY;
    }

    private String formatMessage(String msg, Object... params) {
        if (msg == null) return "";
        if (params == null || params.length == 0) return msg;
        StringBuilder sb = new StringBuilder(msg.length() + 32);
        int paramIdx = 0;
        int i = 0;
        while (i < msg.length()) {
            if (i + 1 < msg.length() && msg.charAt(i) == '{' && msg.charAt(i + 1) == '}' && paramIdx < params.length) {
                sb.append(params[paramIdx++]);
                i += 2;
            } else {
                sb.append(msg.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }
}
