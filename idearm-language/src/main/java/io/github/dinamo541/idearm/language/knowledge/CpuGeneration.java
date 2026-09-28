package io.github.dinamo541.idearm.language.knowledge;

import io.github.dinamo541.idearm.language.catalog.CpuLevel;

import java.util.Locale;

/**
 * CPU generation hierarchy from original 8086 to 64-bit AMD64/Intel 64.
 * Includes P6 (Pentium Pro / Pentium II), fixing finding A-03.
 */
public enum CpuGeneration {
    I8086("8086", 0),
    I8087("8087", 0),
    I80186("80186", 1),
    I80286("80286", 2),
    I80287("80287", 2),
    I80386("80386", 3),
    I80387("80387", 3),
    I80486("80486", 4),
    PENTIUM("Pentium", 5),
    P6("P6", 6),
    X86_64("x86-64", 7),
    UNKNOWN("Unknown", 99);

    private final String displayName;
    private final int level;

    CpuGeneration(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String displayName() {
        return displayName;
    }

    public int level() {
        return level;
    }

    public boolean isSupportedOn(CpuGeneration targetCpu) {
        if (targetCpu == null || targetCpu == UNKNOWN || this == UNKNOWN) {
            return false;
        }
        return this.level <= targetCpu.level;
    }

    public boolean is64Bit() {
        return this == X86_64;
    }

    public CpuLevel toCpuLevel() {
        return switch (this) {
            case I8086, I8087 -> CpuLevel.CPU_8086;
            case I80186 -> CpuLevel.CPU_80186;
            case I80286, I80287 -> CpuLevel.CPU_80286;
            case I80386, I80387 -> CpuLevel.CPU_80386;
            case I80486 -> CpuLevel.CPU_80486;
            case PENTIUM -> CpuLevel.CPU_PENTIUM;
            case P6 -> CpuLevel.CPU_P6;
            case X86_64 -> CpuLevel.CPU_X86_64;
            case UNKNOWN -> CpuLevel.CPU_8086;
        };
    }

    public static CpuGeneration parse(String cpuString) {
        if (cpuString == null || cpuString.isBlank()) return UNKNOWN;
        String clean = CpuLevel.normalizedName(cpuString);
        if (clean.contains("64") || clean.contains("AMD64") || clean.contains("X86-64") || clean.contains("X86_64")) return X86_64;
        if (clean.contains("686") || clean.contains("P6") || clean.contains("PENTIUM PRO") || clean.contains("PENTIUM II") || clean.contains("PENTIUM III")) return P6;
        if (clean.contains("586") || clean.contains("PENT")) return PENTIUM;
        if (clean.contains("486")) return I80486;
        if (clean.contains("386")) return I80386;
        if (clean.contains("286")) return I80286;
        if (clean.contains("186")) return I80186;
        if (clean.contains("8087")) return I8087;
        if (clean.contains("80287")) return I80287;
        if (clean.contains("80387")) return I80387;
        if (clean.contains("8086") || clean.contains("8088")) return I8086;
        return UNKNOWN;
    }
}
