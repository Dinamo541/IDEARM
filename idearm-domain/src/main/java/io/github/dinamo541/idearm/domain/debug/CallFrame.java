package io.github.dinamo541.idearm.domain.debug;

/**
 * Represents a call frame in the execution call stack (e.g. from GDB or emulator call stack).
 *
 * @param level frame index, where 0 is the innermost active frame
 * @param address instruction pointer address as hex string
 * @param function function or symbol name (defaults to "??" if empty)
 * @param file source file name or relative/absolute path
 * @param line source line number, or 0 if unknown
 */
public record CallFrame(int level, String address, String function, String file, int line) {

    public CallFrame {
        address = address != null ? address : "";
        function = (function != null && !function.isBlank()) ? function : "??";
        file = file != null ? file : "";
    }

    /**
     * Formats this frame as a single display line (e.g., "#0 0x00401000 in main at src/main.asm:12").
     */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("#").append(level);
        if (!address.isEmpty()) {
            sb.append(" ").append(address);
        }
        sb.append(" in ").append(function);
        if (!file.isEmpty()) {
            sb.append(" at ").append(file);
            if (line > 0) {
                sb.append(":").append(line);
            }
        }
        return sb.toString();
    }
}
