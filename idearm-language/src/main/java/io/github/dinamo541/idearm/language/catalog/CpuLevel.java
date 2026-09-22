package io.github.dinamo541.idearm.language.catalog;

import java.util.Locale;

/**
 * Baseline CPU generation for x86 architectures.
 */
public enum CpuLevel {
    CPU_8086("8086", 0),
    CPU_80186("80186", 1),
    CPU_80286("80286", 2),
    CPU_80386("80386", 3),
    CPU_80486("80486", 4),
    CPU_PENTIUM("Pentium", 5),
    CPU_X86_64("x86-64", 6);

    private final String displayName;
    private final int level;

    CpuLevel(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String displayName() {
        return displayName;
    }

    public int level() {
        return level;
    }

    public boolean isSupportedOn(String targetCpu) {
        if (targetCpu == null) return true;
        int targetLevel = parseLevel(targetCpu);
        return this.level <= targetLevel;
    }

    public static int parseLevel(String cpuString) {
        String clean = cpuString.toUpperCase(Locale.ROOT).replace("I", "").replace("CPU", "").trim();
        if (clean.contains("64") || clean.contains("AMD64") || clean.contains("X86-64") || clean.contains("X86_64")) return 6;
        if (clean.contains("8086") || clean.contains("8088")) return 0;
        if (clean.contains("186")) return 1;
        if (clean.contains("286")) return 2;
        if (clean.contains("386")) return 3;
        if (clean.contains("486")) return 4;
        if (clean.contains("586") || clean.contains("PENT")) return 5;
        return 0;
    }
}
