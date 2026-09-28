package io.github.dinamo541.idearm.domain.debug;

import java.util.Objects;

/**
 * One disassembled instruction, as the debugger's disassembly panel shows it.
 *
 * <p>The address is already written the way the target addresses memory — {@code 1000:0003} for a real-mode DOS
 * program, a flat hexadecimal address for a 32/64-bit one — because only the backend knows which it is.
 *
 * @param location where the instruction is, ready to read
 * @param bytes the machine code, as space-separated hexadecimal byte pairs
 * @param text the instruction in assembly
 * @param current whether this is the instruction the program is about to run
 */
public record DisasmLine(String location, String bytes, String text, boolean current) {

    public DisasmLine {
        Objects.requireNonNull(location, "location cannot be null");
        Objects.requireNonNull(bytes, "bytes cannot be null");
        Objects.requireNonNull(text, "text cannot be null");
    }

    public DisasmLine(String location, String bytes, String text) {
        this(location, bytes, text, false);
    }

    /** The row as one line, for a list that shows plain text. */
    public String toDisplayString() {
        return "%s  %-14s %s".formatted(location, bytes, text);
    }
}
