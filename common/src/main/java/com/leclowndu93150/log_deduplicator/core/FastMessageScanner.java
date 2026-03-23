package com.leclowndu93150.log_deduplicator.core;

import com.leclowndu93150.log_deduplicator.Config;

public class FastMessageScanner {

    private static final int UUID_LENGTH = 36; // 8-4-4-4-12
    private static final int MIN_HEX_ADDR = 6;
    private static final int MAX_HEX_ADDR = 16;

    public static String normalize(String message) {
        if (message == null) return "";
        int len = message.length();
        if (len == 0) return message;

        boolean normCoords = Config.normalizeCoordinates;
        boolean normUUIDs = Config.normalizeUUIDs;
        boolean normMem = Config.normalizeMemoryAddresses;
        boolean normTime = Config.normalizeTimings;

        if (!normCoords && !normUUIDs && !normMem && !normTime) return message;

        StringBuilder sb = null;
        int lastCopy = 0;

        for (int i = 0; i < len; i++) {
            char c = message.charAt(i);

            // UUID: 8-4-4-4-12 hex with dashes
            if (normUUIDs && isHexChar(c) && i + UUID_LENGTH <= len) {
                int uuidEnd = tryMatchUUID(message, i);
                if (uuidEnd > 0) {
                    if (sb == null) sb = new StringBuilder(len);
                    sb.append(message, lastCopy, i);
                    sb.append("*UUID*");
                    lastCopy = uuidEnd;
                    i = uuidEnd - 1;
                    continue;
                }
            }

            // Memory address: @hexhexhex (6-16 hex chars after @)
            if (normMem && c == '@' && i + MIN_HEX_ADDR < len) {
                int addrEnd = tryMatchMemoryAddress(message, i + 1, len);
                if (addrEnd > 0) {
                    if (sb == null) sb = new StringBuilder(len);
                    sb.append(message, lastCopy, i);
                    sb.append("@*");
                    lastCopy = addrEnd;
                    i = addrEnd - 1;
                    continue;
                }
            }

            // Numbers: coordinates, timings, chunk coords
            if ((c >= '0' && c <= '9') || (c == '-' && i + 1 < len && message.charAt(i + 1) >= '0' && message.charAt(i + 1) <= '9')) {
                boolean isNegative = c == '-';
                int numStart = i;

                // Check for entity coords: (float, float, float)
                if (normCoords && numStart > 0 && message.charAt(numStart - 1) == '(' && !isWordCharBefore(message, numStart - 1)) {
                    int tripleEnd = tryMatchEntityCoords(message, numStart, len);
                    if (tripleEnd > 0) {
                        if (sb == null) sb = new StringBuilder(len);
                        sb.append(message, lastCopy, numStart - 1);
                        sb.append("(*, *, *)");
                        lastCopy = tripleEnd;
                        i = tripleEnd - 1;
                        continue;
                    }
                }

                // Check for chunk coords: [int, int]
                if (normCoords && numStart > 0 && message.charAt(numStart - 1) == '[') {
                    int pairEnd = tryMatchChunkCoords(message, numStart, len);
                    if (pairEnd > 0) {
                        if (sb == null) sb = new StringBuilder(len);
                        sb.append(message, lastCopy, numStart - 1);
                        sb.append("[*, *]");
                        lastCopy = pairEnd;
                        i = pairEnd - 1;
                        continue;
                    }
                }

                int numEnd = scanNumber(message, isNegative ? i + 1 : i, len);
                if (numEnd <= (isNegative ? i + 1 : i)) continue;

                boolean isFloat = false;
                if (numEnd < len && message.charAt(numEnd) == '.') {
                    int afterDot = scanDigits(message, numEnd + 1, len);
                    if (afterDot > numEnd + 1) {
                        numEnd = afterDot;
                        isFloat = true;
                    }
                }

                // Timing: 123ms
                if (normTime && numEnd + 1 < len && message.charAt(numEnd) == 'm' && message.charAt(numEnd + 1) == 's') {
                    if (sb == null) sb = new StringBuilder(len);
                    sb.append(message, lastCopy, numStart);
                    sb.append("*ms");
                    lastCopy = numEnd + 2;
                    i = lastCopy - 1;
                    continue;
                }

                // Timing: 123 tick
                if (normTime && numEnd + 5 <= len && message.charAt(numEnd) == ' ') {
                    if (message.startsWith("tick", numEnd + 1)) {
                        if (sb == null) sb = new StringBuilder(len);
                        sb.append(message, lastCopy, numStart);
                        sb.append("* tick");
                        lastCopy = numEnd + 5;
                        i = lastCopy - 1;
                        continue;
                    }
                }

                // Float coordinate pair: 123.45, -67.89 (not inside parens/brackets — those are handled above)
                if (normCoords && isFloat) {
                    boolean wordBefore = numStart > 0 && isWordChar(message.charAt(numStart - 1));
                    boolean wordAfter = numEnd < len && isWordChar(message.charAt(numEnd));
                    if (!wordBefore && !wordAfter) {
                        int pairEnd = tryMatchFloatPair(message, numEnd, len);
                        if (pairEnd > 0) {
                            if (sb == null) sb = new StringBuilder(len);
                            sb.append(message, lastCopy, numStart);
                            sb.append("*");
                            lastCopy = pairEnd;
                            i = pairEnd - 1;
                            continue;
                        }
                        // Single float coordinate
                        if (sb == null) sb = new StringBuilder(len);
                        sb.append(message, lastCopy, numStart);
                        sb.append("*");
                        lastCopy = numEnd;
                        i = numEnd - 1;
                        continue;
                    }
                }

                i = numEnd - 1;
            }
        }

        if (sb == null) return message;
        if (lastCopy < len) sb.append(message, lastCopy, len);
        return sb.toString();
    }

    private static int tryMatchUUID(String s, int start) {
        // 8-4-4-4-12 = 36 chars total
        if (start + UUID_LENGTH > s.length()) return -1;

        // Check structure: 8 hex, dash, 4 hex, dash, 4 hex, dash, 4 hex, dash, 12 hex
        int pos = start;
        if (!isHexRun(s, pos, 8)) return -1;
        pos += 8;
        if (s.charAt(pos) != '-') return -1;
        pos++;
        if (!isHexRun(s, pos, 4)) return -1;
        pos += 4;
        if (s.charAt(pos) != '-') return -1;
        pos++;
        if (!isHexRun(s, pos, 4)) return -1;
        pos += 4;
        if (s.charAt(pos) != '-') return -1;
        pos++;
        if (!isHexRun(s, pos, 4)) return -1;
        pos += 4;
        if (s.charAt(pos) != '-') return -1;
        pos++;
        if (!isHexRun(s, pos, 12)) return -1;
        pos += 12;

        // Don't match if surrounded by hex chars (part of a larger hex string)
        if (start > 0 && isHexChar(s.charAt(start - 1))) return -1;
        if (pos < s.length() && isHexChar(s.charAt(pos))) return -1;

        return pos;
    }

    private static int tryMatchMemoryAddress(String s, int start, int len) {
        int end = start;
        while (end < len && isHexChar(s.charAt(end)) && end - start < MAX_HEX_ADDR) {
            end++;
        }
        int hexLen = end - start;
        return (hexLen >= MIN_HEX_ADDR) ? end : -1;
    }

    private static int tryMatchEntityCoords(String s, int start, int len) {
        // Expect: float, float, float)
        int pos = skipFloat(s, start, len);
        if (pos < 0) return -1;
        pos = skipCommaSpace(s, pos, len);
        if (pos < 0) return -1;
        pos = skipFloat(s, pos, len);
        if (pos < 0) return -1;
        pos = skipCommaSpace(s, pos, len);
        if (pos < 0) return -1;
        pos = skipFloat(s, pos, len);
        if (pos < 0) return -1;
        if (pos >= len || s.charAt(pos) != ')') return -1;
        return pos + 1;
    }

    private static int tryMatchChunkCoords(String s, int start, int len) {
        // Expect: int, int]
        int pos = skipInt(s, start, len);
        if (pos < 0) return -1;
        pos = skipCommaSpace(s, pos, len);
        if (pos < 0) return -1;
        pos = skipInt(s, pos, len);
        if (pos < 0) return -1;
        if (pos >= len || s.charAt(pos) != ']') return -1;
        return pos + 1;
    }

    private static int tryMatchFloatPair(String s, int afterFirst, int len) {
        // After the first float, check for ", float" or ", float, float"
        int pos = skipCommaSpace(s, afterFirst, len);
        if (pos < 0) return -1;
        pos = skipFloat(s, pos, len);
        if (pos < 0) return -1;

        // Optional third
        int pos2 = skipCommaSpace(s, pos, len);
        if (pos2 > 0) {
            int pos3 = skipFloat(s, pos2, len);
            if (pos3 > 0) return pos3;
        }

        return pos;
    }

    private static int skipFloat(String s, int start, int len) {
        int pos = start;
        if (pos < len && s.charAt(pos) == '-') pos++;
        int digitStart = pos;
        pos = scanDigits(s, pos, len);
        if (pos == digitStart) return -1;
        if (pos < len && s.charAt(pos) == '.') {
            int afterDot = scanDigits(s, pos + 1, len);
            if (afterDot > pos + 1) pos = afterDot;
        }
        return pos;
    }

    private static int skipInt(String s, int start, int len) {
        int pos = start;
        if (pos < len && s.charAt(pos) == '-') pos++;
        int digitStart = pos;
        pos = scanDigits(s, pos, len);
        return pos > digitStart ? pos : -1;
    }

    private static int skipCommaSpace(String s, int start, int len) {
        if (start >= len || s.charAt(start) != ',') return -1;
        int pos = start + 1;
        while (pos < len && s.charAt(pos) == ' ') pos++;
        return pos;
    }

    private static int scanNumber(String s, int start, int len) {
        return scanDigits(s, start, len);
    }

    private static int scanDigits(String s, int start, int len) {
        int i = start;
        while (i < len && s.charAt(i) >= '0' && s.charAt(i) <= '9') i++;
        return i;
    }

    private static boolean isHexRun(String s, int start, int count) {
        if (start + count > s.length()) return false;
        for (int i = start; i < start + count; i++) {
            if (!isHexChar(s.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '.';
    }

    private static boolean isWordCharBefore(String s, int index) {
        return index > 0 && isWordChar(s.charAt(index - 1));
    }
}
