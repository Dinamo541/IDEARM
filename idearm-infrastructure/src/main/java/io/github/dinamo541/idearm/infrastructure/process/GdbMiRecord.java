package io.github.dinamo541.idearm.infrastructure.process;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structured model representing a single GDB Machine Interface (MI) line.
 */
public final class GdbMiRecord {

    public enum Type {
        RESULT,          // ^done, ^running, ^error, ^exit
        EXEC_ASYNC,      // *stopped, *running
        STATUS_ASYNC,    // +download, etc.
        NOTIFY_ASYNC,    // =thread-group-added, =breakpoint-modified, etc.
        CONSOLE_STREAM,  // ~"..."
        TARGET_STREAM,   // @"..."
        LOG_STREAM,      // &"..."
        PROMPT,          // (gdb)
        UNKNOWN
    }

    private final Type type;
    private final Long token;
    private final String recordClass;
    private final Map<String, Object> results;
    private final String streamMessage;
    private final String raw;

    public GdbMiRecord(Type type, Long token, String recordClass, Map<String, Object> results, String streamMessage, String raw) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.token = token;
        this.recordClass = recordClass != null ? recordClass : "";
        this.results = results != null ? Collections.unmodifiableMap(results) : Map.of();
        this.streamMessage = streamMessage != null ? streamMessage : "";
        this.raw = raw != null ? raw : "";
    }

    public Type type() { return type; }
    public Long token() { return token; }
    public String recordClass() { return recordClass; }
    public Map<String, Object> results() { return results; }
    public String streamMessage() { return streamMessage; }
    public String raw() { return raw; }

    public boolean isDone() { return "done".equalsIgnoreCase(recordClass); }
    public boolean isRunning() { return "running".equalsIgnoreCase(recordClass); }
    public boolean isError() { return "error".equalsIgnoreCase(recordClass); }
    public boolean isStopped() { return "stopped".equalsIgnoreCase(recordClass); }
    public boolean isExit() { return "exit".equalsIgnoreCase(recordClass); }

    public String getString(String key) {
        Object val = results.get(key);
        return val instanceof String s ? s : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String key) {
        Object val = results.get(key);
        return val instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    @SuppressWarnings("unchecked")
    public List<Object> getList(String key) {
        Object val = results.get(key);
        return val instanceof List<?> l ? (List<Object>) l : null;
    }

    public Integer getInteger(String key) {
        Object val = results.get(key);
        if (val instanceof Integer i) return i;
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public Long getLong(String key) {
        Object val = results.get(key);
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            try {
                String str = s.trim();
                if (str.startsWith("0x") || str.startsWith("0X")) {
                    return Long.parseUnsignedLong(str.substring(2), 16);
                }
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    @Override
    public String toString() {
        return "GdbMiRecord{" +
                "type=" + type +
                ", token=" + token +
                ", recordClass='" + recordClass + '\'' +
                ", results=" + results +
                ", streamMessage='" + streamMessage + '\'' +
                '}';
    }
}
