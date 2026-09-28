package io.github.dinamo541.idearm.language.knowledge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight, zero-dependency JSON parser and serializer written with pure JDK.
 *
 * <p>Complies strictly with ADR-007 (Layer 3 uses only JDK, no external dependencies,
 * no filesystem I/O, no process spawning).
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String text) {
        if (text == null) return null;
        return new Parser(text).parseValue();
    }

    public static Object parse(InputStream in) throws IOException {
        if (in == null) return null;
        StringBuilder sb = new StringBuilder();
        try (Reader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        }
        return parse(sb.toString());
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object val = parse(text);
        if (val instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected JSON object but got " + (val == null ? "null" : val.getClass().getSimpleName()));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(InputStream in) throws IOException {
        Object val = parse(in);
        if (val instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected JSON object but got " + (val == null ? "null" : val.getClass().getSimpleName()));
    }

    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String text) {
        Object val = parse(text);
        if (val instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new IllegalArgumentException("Expected JSON array but got " + (val == null ? "null" : val.getClass().getSimpleName()));
    }

    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(InputStream in) throws IOException {
        Object val = parse(in);
        if (val instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new IllegalArgumentException("Expected JSON array but got " + (val == null ? "null" : val.getClass().getSimpleName()));
    }

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        appendJson(sb, value);
        return sb.toString();
    }

    public static String stringifyPretty(Object value) {
        StringBuilder sb = new StringBuilder();
        appendJsonPretty(sb, value, 0);
        return sb.toString();
    }

    private static void appendJson(StringBuilder sb, Object val) {
        if (val == null) {
            sb.append("null");
        } else if (val instanceof Boolean || val instanceof Number) {
            sb.append(val);
        } else if (val instanceof CharSequence) {
            appendQuotedString(sb, val.toString());
        } else if (val instanceof Map<?, ?> map) {
            sb.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                appendQuotedString(sb, String.valueOf(entry.getKey()));
                sb.append(":");
                appendJson(sb, entry.getValue());
            }
            sb.append("}");
        } else if (val instanceof Collection<?> coll) {
            sb.append("[");
            boolean first = true;
            for (Object item : coll) {
                if (!first) sb.append(",");
                first = false;
                appendJson(sb, item);
            }
            sb.append("]");
        } else if (val.getClass().isArray()) {
            sb.append("[");
            int len = java.lang.reflect.Array.getLength(val);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(",");
                appendJson(sb, java.lang.reflect.Array.get(val, i));
            }
            sb.append("]");
        } else {
            appendQuotedString(sb, val.toString());
        }
    }

    private static void appendJsonPretty(StringBuilder sb, Object val, int indent) {
        String indentStr = "  ".repeat(indent);
        String nextIndent = "  ".repeat(indent + 1);

        if (val == null) {
            sb.append("null");
        } else if (val instanceof Boolean || val instanceof Number) {
            sb.append(val);
        } else if (val instanceof CharSequence) {
            appendQuotedString(sb, val.toString());
        } else if (val instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (i > 0) sb.append(",\n");
                sb.append(nextIndent);
                appendQuotedString(sb, String.valueOf(entry.getKey()));
                sb.append(": ");
                appendJsonPretty(sb, entry.getValue(), indent + 1);
                i++;
            }
            sb.append("\n").append(indentStr).append("}");
        } else if (val instanceof Collection<?> coll) {
            if (coll.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            int i = 0;
            for (Object item : coll) {
                if (i > 0) sb.append(",\n");
                sb.append(nextIndent);
                appendJsonPretty(sb, item, indent + 1);
                i++;
            }
            sb.append("\n").append(indentStr).append("]");
        } else {
            appendQuotedString(sb, val.toString());
        }
    }

    private static void appendQuotedString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    public static String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null) return defaultValue;
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    public static int getInt(Map<String, Object> map, String key, int defaultValue) {
        if (map == null) return defaultValue;
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    public static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        if (map == null) return defaultValue;
        Object v = map.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s.trim());
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public static List<String> getStringList(Map<String, Object> map, String key) {
        if (map == null) return List.of();
        Object v = map.get(key);
        if (v instanceof List<?> list) {
            List<String> res = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) res.add(item.toString());
            }
            return res;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getObjectList(Map<String, Object> map, String key) {
        if (map == null) return List.of();
        Object v = map.get(key);
        if (v instanceof List<?> list) {
            List<Map<String, Object>> res = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    res.add((Map<String, Object>) m);
                }
            }
            return res;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getObject(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return (v instanceof Map<?, ?> m) ? (Map<String, Object>) m : null;
    }

    private static final class Parser {
        private final String src;
        private final int len;
        private int pos;

        Parser(String src) {
            this.src = src;
            this.len = src.length();
            this.pos = 0;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= len) {
                return null;
            }
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield parseNumber();
                    }
                    throw error("Unexpected character: '" + c + "'");
                }
            };
        }

        private Map<String, Object> parseObject() {
            match('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                match(':');
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (peek('}')) {
                    pos++;
                    break;
                }
                match(',');
            }
            return map;
        }

        private List<Object> parseArray() {
            match('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                if (peek(']')) {
                    pos++;
                    break;
                }
                match(',');
            }
            return list;
        }

        private String parseString() {
            match('"');
            StringBuilder sb = new StringBuilder();
            while (pos < len) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= len) throw error("Unterminated escape sequence");
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > len) throw error("Incomplete unicode escape");
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw error("Invalid unicode escape: \\u" + hex);
                            }
                        }
                        default -> throw error("Invalid escape character: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("Unterminated string literal");
        }

        private Number parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') pos++;
            while (pos < len && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
            boolean isFloat = false;
            if (pos < len && src.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < len && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            if (pos < len && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < len && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < len && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            String numStr = src.substring(start, pos);
            if (isFloat) {
                return Double.parseDouble(numStr);
            }
            try {
                long l = Long.parseLong(numStr);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    return (int) l;
                }
                return l;
            } catch (NumberFormatException e) {
                return Double.parseDouble(numStr);
            }
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw error("Expected boolean literal");
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw error("Expected null literal");
        }

        private void skipWhitespace() {
            while (pos < len) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else if (c == '/' && pos + 1 < len) {
                    // Support line and block comments in JSON resources
                    if (src.charAt(pos + 1) == '/') {
                        pos += 2;
                        while (pos < len && src.charAt(pos) != '\n' && src.charAt(pos) != '\r') {
                            pos++;
                        }
                    } else if (src.charAt(pos + 1) == '*') {
                        pos += 2;
                        while (pos + 1 < len && !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) {
                            pos++;
                        }
                        if (pos + 1 < len) pos += 2;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        private boolean peek(char expected) {
            return pos < len && src.charAt(pos) == expected;
        }

        private void match(char expected) {
            if (pos >= len || src.charAt(pos) != expected) {
                throw error("Expected '" + expected + "' but got " + (pos >= len ? "EOF" : "'" + src.charAt(pos) + "'"));
            }
            pos++;
        }

        private IllegalArgumentException error(String msg) {
            int line = 1;
            int col = 1;
            for (int i = 0; i < pos && i < len; i++) {
                if (src.charAt(i) == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
            }
            return new IllegalArgumentException(msg + " at line " + line + ", column " + col);
        }
    }
}
