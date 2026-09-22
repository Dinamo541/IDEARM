package io.github.dinamo541.idearm.infrastructure.process;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Streaming parser for GDB Machine Interface (MI) output lines.
 */
public final class GdbMiParser {

    private GdbMiParser() {}

    public static GdbMiRecord parse(String line) {
        if (line == null) return null;
        String raw = line.trim();
        if (raw.isEmpty()) return null;

        if (raw.equals("(gdb)") || raw.startsWith("(gdb)")) {
            return new GdbMiRecord(GdbMiRecord.Type.PROMPT, null, "prompt", Map.of(), "", raw);
        }

        char first = raw.charAt(0);
        if (first == '~' || first == '@' || first == '&') {
            GdbMiRecord.Type type = switch (first) {
                case '~' -> GdbMiRecord.Type.CONSOLE_STREAM;
                case '@' -> GdbMiRecord.Type.TARGET_STREAM;
                default -> GdbMiRecord.Type.LOG_STREAM;
            };
            String text = unescapeStream(raw.substring(1));
            return new GdbMiRecord(type, null, "stream", Map.of(), text, raw);
        }

        Cursor cursor = new Cursor(raw);
        Long token = cursor.readToken();

        char indicator = cursor.peek();
        GdbMiRecord.Type type;
        switch (indicator) {
            case '^' -> type = GdbMiRecord.Type.RESULT;
            case '*' -> type = GdbMiRecord.Type.EXEC_ASYNC;
            case '+' -> type = GdbMiRecord.Type.STATUS_ASYNC;
            case '=' -> type = GdbMiRecord.Type.NOTIFY_ASYNC;
            default -> {
                return new GdbMiRecord(GdbMiRecord.Type.UNKNOWN, token, "unknown", Map.of(), "", raw);
            }
        }
        cursor.next(); // consume indicator

        String recordClass = cursor.readIdentifierOrWord();
        Map<String, Object> results = new LinkedHashMap<>();

        while (cursor.hasMore()) {
            cursor.skipWhitespace();
            if (!cursor.hasMore()) break;
            if (cursor.peek() == ',') {
                cursor.next();
            }
            cursor.skipWhitespace();
            if (!cursor.hasMore()) break;

            int startPos = cursor.pos();
            String key = cursor.readKey();
            if (!key.isEmpty()) {
                cursor.skipWhitespace();
                if (cursor.peek() == '=') {
                    cursor.next();
                    Object val = cursor.readValue();
                    results.put(key, val);
                } else {
                    results.put(key, "");
                }
            }
            if (cursor.pos() == startPos) {
                cursor.next();
            }
        }

        return new GdbMiRecord(type, token, recordClass, results, "", raw);
    }

    private static String unescapeStream(String text) {
        String str = text.trim();
        if (str.startsWith("\"") && str.endsWith("\"") && str.length() >= 2) {
            Cursor cursor = new Cursor(str);
            return cursor.readCString();
        }
        return str;
    }

    private static final class Cursor {
        private final String s;
        private int pos;

        Cursor(String s) {
            this.s = s;
            this.pos = 0;
        }

        int pos() {
            return pos;
        }

        boolean hasMore() {
            return pos < s.length();
        }

        char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        char next() {
            return pos < s.length() ? s.charAt(pos++) : '\0';
        }

        void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Long readToken() {
            int start = pos;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                pos++;
            }
            if (pos > start) {
                try {
                    return Long.parseLong(s.substring(start, pos));
                } catch (NumberFormatException ignored) {}
            }
            return null;
        }

        String readIdentifierOrWord() {
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ',' || c == '=' || Character.isWhitespace(c)) break;
                pos++;
            }
            return s.substring(start, pos);
        }

        String readKey() {
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                    pos++;
                } else {
                    break;
                }
            }
            return s.substring(start, pos);
        }

        Object readValue() {
            skipWhitespace();
            if (!hasMore()) return "";
            char c = peek();
            if (c == '"') {
                return readCString();
            } else if (c == '{') {
                return readTuple();
            } else if (c == '[') {
                return readList();
            } else {
                return readBareValue();
            }
        }

        String readBareValue() {
            int start = pos;
            while (pos < s.length()) {
                char ch = s.charAt(pos);
                if (ch == ',' || ch == '}' || ch == ']' || Character.isWhitespace(ch)) break;
                pos++;
            }
            return s.substring(start, pos);
        }

        String readCString() {
            if (peek() != '"') return "";
            next(); // skip opening quote
            StringBuilder sb = new StringBuilder();
            while (hasMore()) {
                char c = next();
                if (c == '"') {
                    break;
                }
                if (c == '\\' && hasMore()) {
                    char esc = next();
                    switch (esc) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '0', '1', '2', '3', '4', '5', '6', '7' -> {
                            int octal = esc - '0';
                            int count = 1;
                            while (count < 3 && hasMore() && peek() >= '0' && peek() <= '7') {
                                octal = (octal << 3) + (next() - '0');
                                count++;
                            }
                            sb.append((char) octal);
                        }
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Map<String, Object> readTuple() {
            if (peek() != '{') return Map.of();
            next(); // skip '{'
            Map<String, Object> map = new LinkedHashMap<>();
            while (hasMore()) {
                skipWhitespace();
                if (!hasMore()) break;
                if (peek() == '}') {
                    next();
                    break;
                }
                if (peek() == ',') {
                    next();
                    continue;
                }
                int start = pos;
                String key = readKey();
                if (!key.isEmpty()) {
                    skipWhitespace();
                    if (peek() == '=') {
                        next();
                        Object val = readValue();
                        map.put(key, val);
                    } else {
                        map.put(key, "");
                    }
                }
                if (pos == start) {
                    next();
                }
            }
            return map;
        }

        List<Object> readList() {
            if (peek() != '[') return List.of();
            next(); // skip '['
            List<Object> list = new ArrayList<>();
            while (hasMore()) {
                skipWhitespace();
                if (!hasMore()) break;
                if (peek() == ']') {
                    next();
                    break;
                }
                if (peek() == ',') {
                    next();
                    continue;
                }
                int start = pos;
                char c = peek();
                if (c == '{' || c == '[' || c == '"') {
                    list.add(readValue());
                } else {
                    String potentialKey = readKey();
                    skipWhitespace();
                    if (!potentialKey.isEmpty() && peek() == '=') {
                        next(); // skip '='
                        Object val = readValue();
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put(potentialKey, val);
                        list.add(entry);
                    } else if (!potentialKey.isEmpty()) {
                        list.add(potentialKey);
                    }
                }
                if (pos == start) {
                    next();
                }
            }
            return list;
        }
    }
}
