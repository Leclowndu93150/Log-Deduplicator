package com.leclowndu93150.log_deduplicator.core;

import com.leclowndu93150.log_deduplicator.Config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StackTraceCleaner {
    private static final Set<String> COMMON_FRAMEWORK_PACKAGES = Set.of(
        "java.lang.reflect",
        "jdk.internal.reflect",
        "sun.reflect",
        "net.neoforged.fml.event",
        "net.neoforged.bus",
        "net.minecraftforge.eventbus",
        "cpw.mods.modlauncher",
        "org.apache.logging.log4j"
    );

    public static String cleanStackTrace(String stackTrace) {
        if (!Config.cleanStacktraces || stackTrace == null || stackTrace.isEmpty()) {
            return stackTrace;
        }

        String[] lines = stackTrace.split("\n");
        List<String> cleanedLines = new ArrayList<>();
        Set<String> seenFrames = new HashSet<>();
        int depth = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("Caused by:") || trimmed.startsWith("Suppressed:")) {
                cleanedLines.add(line);
                depth = 0;
                seenFrames.clear();
                continue;
            }

            if (trimmed.startsWith("... ") && trimmed.endsWith(" more")) {
                cleanedLines.add(line);
                continue;
            }

            if (trimmed.startsWith("at ")) {
                FrameInfo frame = parseFrame(trimmed);
                if (frame != null) {
                    if (seenFrames.contains(frame.fullMethod)) {
                        continue;
                    }

                    if (Config.removeCommonFrames && isCommonFramework(frame.packageName)) {
                        continue;
                    }

                    if (Config.maxStacktraceDepth > 0 && depth >= Config.maxStacktraceDepth) {
                        int remaining = lines.length - cleanedLines.size();
                        if (remaining > 0) {
                            String indent = line.substring(0, line.indexOf("at"));
                            cleanedLines.add(indent + "... " + remaining + " more");
                        }
                        break;
                    }

                    cleanedLines.add(line);
                    seenFrames.add(frame.fullMethod);
                    depth++;
                } else {
                    cleanedLines.add(line);
                }
            } else {
                cleanedLines.add(line);
            }
        }

        return String.join("\n", cleanedLines);
    }

    private static FrameInfo parseFrame(String trimmed) {
        // Expected format: "at package.Class.method(Source.java:123)"
        if (!trimmed.startsWith("at ") || !trimmed.endsWith(")")) {
            return null;
        }

        int parenStart = trimmed.lastIndexOf('(');
        if (parenStart < 0) {
            return null;
        }

        String classAndMethod = trimmed.substring(3, parenStart);
        int lastDot = classAndMethod.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }

        String className = classAndMethod.substring(0, lastDot);
        String packageName = extractPackage(className);

        return new FrameInfo(classAndMethod, packageName);
    }

    private static String extractPackage(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            return className.substring(0, lastDot);
        }
        return "";
    }

    private static boolean isCommonFramework(String packageName) {
        for (String commonPkg : COMMON_FRAMEWORK_PACKAGES) {
            if (packageName.startsWith(commonPkg)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isStackTraceMessage(String message) {
        if (message == null) {
            return false;
        }

        return message.contains("Exception") ||
               message.contains("Error") ||
               message.contains("\tat ") ||
               message.contains("Caused by:");
    }

    private static class FrameInfo {
        final String fullMethod;
        final String packageName;

        FrameInfo(String fullMethod, String packageName) {
            this.fullMethod = fullMethod;
            this.packageName = packageName;
        }
    }
}
