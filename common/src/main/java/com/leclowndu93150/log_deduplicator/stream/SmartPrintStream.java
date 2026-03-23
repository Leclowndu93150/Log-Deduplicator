package com.leclowndu93150.log_deduplicator.stream;

import com.leclowndu93150.log_deduplicator.Config;
import com.leclowndu93150.log_deduplicator.core.DeduplicationManager;
import com.leclowndu93150.log_deduplicator.core.SpamPatternDetector;
import com.leclowndu93150.log_deduplicator.core.StackTraceCleaner;
import org.apache.logging.log4j.Level;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.locks.ReentrantLock;

public class SmartPrintStream extends PrintStream {
    private final PrintStream wrapped;
    private final boolean isError;
    private final ReentrantLock lock;

    private final StringBuilder lineBuffer;
    private final StringBuilder multilineBuffer;
    private PrintStreamState state;
    private long lastLineTime;

    private enum PrintStreamState {
        NORMAL,
        IN_STACK_TRACE,
        IN_MULTILINE_MESSAGE
    }

    public SmartPrintStream(PrintStream wrapped, boolean isError) {
        super(new DummyOutputStream());
        this.wrapped = wrapped;
        this.isError = isError;
        this.lock = new ReentrantLock();
        this.lineBuffer = new StringBuilder(256);
        this.multilineBuffer = new StringBuilder(1024);
        this.state = PrintStreamState.NORMAL;
        this.lastLineTime = System.currentTimeMillis();
    }

    @Override
    public void write(int b) {
        lock.lock();
        try {
            if (b == '\n') {
                processLine(lineBuffer.toString());
                lineBuffer.setLength(0);
            } else if (b != '\r') {
                lineBuffer.append((char) b);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        lock.lock();
        try {
            for (int i = off; i < off + len; i++) {
                byte b = buf[i];
                if (b == '\n') {
                    processLine(lineBuffer.toString());
                    lineBuffer.setLength(0);
                } else if (b != '\r') {
                    lineBuffer.append((char) b);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void println(String x) {
        lock.lock();
        try {
            processLine(x != null ? x : "null");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void println(Object x) {
        println(String.valueOf(x));
    }

    private void processLine(String line) {
        if (line == null || line.isEmpty()) {
            if (state != PrintStreamState.NORMAL) {
                multilineBuffer.append('\n');
            }
            return;
        }

        if (!Config.enabled || !Config.systemStreamsEnabled || !Config.detectMultiline) {
            writeThroughDedup(line);
            return;
        }

        long now = System.currentTimeMillis();
        long timeSinceLast = now - lastLineTime;

        if (state == PrintStreamState.IN_STACK_TRACE) {
            if (SpamPatternDetector.isStackTraceLine(line)) {
                multilineBuffer.append('\n').append(line);
                lastLineTime = now;
                return;
            } else {
                String completeMessage = multilineBuffer.toString();
                writeThroughDedup(completeMessage);
                multilineBuffer.setLength(0);
                state = PrintStreamState.NORMAL;
            }
        }

        if (SpamPatternDetector.isExceptionMessage(line)) {
            if (multilineBuffer.length() > 0) {
                writeThroughDedup(multilineBuffer.toString());
                multilineBuffer.setLength(0);
            }
            state = PrintStreamState.IN_STACK_TRACE;
            multilineBuffer.append(line);
            lastLineTime = now;
            return;
        }

        if (state == PrintStreamState.IN_MULTILINE_MESSAGE) {
            if (timeSinceLast < Config.multilineTimeoutMs) {
                multilineBuffer.append('\n').append(line);
                lastLineTime = now;
                return;
            } else {
                String completeMessage = multilineBuffer.toString();
                writeThroughDedup(completeMessage);
                multilineBuffer.setLength(0);
                state = PrintStreamState.NORMAL;
            }
        }

        if (state == PrintStreamState.NORMAL) {
            writeThroughDedup(line);
        }

        lastLineTime = now;
    }

    private void writeThroughDedup(String message) {
        if (!Config.enabled || !Config.systemStreamsEnabled) {
            writeToWrapped(message);
            return;
        }

        Level level = isError ? Level.ERROR : Level.INFO;
        String loggerName = "System." + (isError ? "err" : "out");

        DeduplicationManager.Result result = DeduplicationManager.getInstance().check(
            message, loggerName, level
        );

        if (result.isAllowed()) {
            writeToWrapped(message);
        } else if (result.shouldShowCount()) {
            String cleaned = Config.cleanStacktraces && StackTraceCleaner.isStackTraceMessage(message)
                ? StackTraceCleaner.cleanStackTrace(message)
                : message;
            writeToWrapped(DeduplicationManager.formatWithCount(
                cleaned, result.getCount(), result.getDurationMs()
            ));
        }
    }

    private void writeToWrapped(String message) {
        wrapped.println(message);
    }

    @Override
    public void flush() {
        lock.lock();
        try {
            if (lineBuffer.length() > 0) {
                processLine(lineBuffer.toString());
                lineBuffer.setLength(0);
            }
            if (multilineBuffer.length() > 0) {
                writeThroughDedup(multilineBuffer.toString());
                multilineBuffer.setLength(0);
                state = PrintStreamState.NORMAL;
            }
            wrapped.flush();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        flush();
        wrapped.close();
    }

    private static class DummyOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
        }
    }
}
