package io.github.dinamo541.idearm.language.knowledge;

import java.util.Locale;

public enum Dialect {
    MASM("Microsoft Macro Assembler"),
    TASM("Borland Turbo Assembler"),
    NASM("Netwide Assembler"),
    GAS("GNU Assembler"),
    COMMON("Common / Dialect-neutral"),
    UNKNOWN("Unknown dialect");

    private final String displayName;

    Dialect(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static Dialect parse(String name) {
        if (name == null || name.isBlank()) return UNKNOWN;
        String s = name.toUpperCase(Locale.ROOT).trim();
        if (s.contains("NASM")) return NASM;
        if (s.contains("TASM")) return TASM;
        if (s.contains("MASM") || s.contains("ML")) return MASM;
        if (s.contains("GAS") || s.contains("AS")) return GAS;
        if (s.contains("COMMON")) return COMMON;
        return UNKNOWN;
    }
}
