package io.github.dinamo541.idearm.language.knowledge;

import io.github.dinamo541.idearm.language.catalog.CpuLevel;
import io.github.dinamo541.idearm.language.catalog.InstructionInfo;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Architecture bitness and execution mode for x86 systems (16-bit, 32-bit, 64-bit).
 * Enables educational and technical filtering across instructions, registers, and syntax elements.
 */
public enum ArchitectureBitness {
    ALL("all", "Todas las arquitecturas", "All architectures", 0),
    BITS_16("16", "16 bits (Modo Real / 8086)", "16-bit (Real Mode / 8086)", 16),
    BITS_32("32", "32 bits (Modo Protegido / IA-32)", "32-bit (Protected Mode / IA-32)", 32),
    BITS_64("64", "64 bits (Modo Largo / x86-64)", "64-bit (Long Mode / x86-64)", 64);

    private final String code;
    private final String displayNameEs;
    private final String displayNameEn;
    private final int bitWidth;

    ArchitectureBitness(String code, String displayNameEs, String displayNameEn, int bitWidth) {
        this.code = code;
        this.displayNameEs = displayNameEs;
        this.displayNameEn = displayNameEn;
        this.bitWidth = bitWidth;
    }

    public String code() {
        return code;
    }

    public String displayNameEs() {
        return displayNameEs;
    }

    public String displayNameEn() {
        return displayNameEn;
    }

    public int bitWidth() {
        return bitWidth;
    }

    public String displayName(String languageCode) {
        if (languageCode != null && languageCode.toLowerCase(Locale.ROOT).startsWith("es")) {
            return displayNameEs;
        }
        return displayNameEn;
    }

    public CompatibilityContext toCompatibilityContext() {
        return switch (this) {
            case ALL -> CompatibilityContext.of(CpuGeneration.UNKNOWN, ProcessorMode.UNKNOWN, null, Dialect.COMMON, null);
            case BITS_16 -> CompatibilityContext.of(CpuGeneration.I80286, ProcessorMode.REAL, Set.of(), Dialect.COMMON, null);
            case BITS_32 -> CompatibilityContext.of(CpuGeneration.P6, ProcessorMode.PROTECTED_32, Set.of(), Dialect.COMMON, null);
            case BITS_64 -> CompatibilityContext.of(CpuGeneration.X86_64, ProcessorMode.LONG, Set.of(), Dialect.COMMON, null);
        };
    }

    /**
     * Checks if this architecture mode supports the given instruction entry.
     */
    public boolean supportsInstruction(InstructionEntry entry) {
        if (this == ALL) return true;
        if (entry == null) return false;
        CompatibilityResult res = CompatibilityResolver.resolve(entry, toCompatibilityContext());
        return res.status() == Availability.AVAILABLE || res.status() == Availability.PARTIAL;
    }

    /**
     * Checks if this architecture mode supports the given instruction info (using Corpus for rich lookup).
     */
    public boolean supportsInstruction(InstructionInfo info, Corpus corpus) {
        if (this == ALL) return true;
        if (info == null) return false;
        if (corpus == null) corpus = Corpus.get();
        return corpus.findInstruction(info.mnemonic())
                .map(this::supportsInstruction)
                .orElseGet(() -> supportsFallback(info));
    }

    private boolean supportsFallback(InstructionInfo info) {
        return switch (this) {
            case ALL -> true;
            case BITS_16 -> info.minCpu().level() <= CpuLevel.CPU_80286.level();
            case BITS_32 -> info.minCpu().level() <= CpuLevel.CPU_P6.level();
            case BITS_64 -> true;
        };
    }

    /**
     * Checks if this architecture mode supports/contains the given register entry.
     */
    public boolean supportsRegister(RegisterEntry reg) {
        if (this == ALL) return true;
        if (reg == null) return false;
        return switch (this) {
            case ALL -> true;
            case BITS_16 -> reg.sizeBits() == 16 || (reg.sizeBits() == 8 && reg.requirement().minGeneration().level() <= CpuGeneration.I80286.level());
            case BITS_32 -> reg.sizeBits() == 32;
            case BITS_64 -> reg.sizeBits() == 64;
        };
    }

    /**
     * Checks if this architecture mode is compatible with the given syntax item.
     */
    public boolean supportsSyntaxItem(SyntaxItem item) {
        if (this == ALL) return true;
        if (item == null) return false;
        String id = item.id().toLowerCase(Locale.ROOT);
        String token = item.token().toLowerCase(Locale.ROOT);
        return switch (this) {
            case ALL -> true;
            case BITS_16 -> !id.contains("use32") && !id.contains("use64") && !id.contains("rel") && !token.equals("resq") && !token.equals("use64") && !token.equals("use32");
            case BITS_32 -> !id.contains("use16") && !id.contains("use64") && !token.equals("use16") && !token.equals("use64");
            case BITS_64 -> !id.contains("use16") && !token.equals("use16");
        };
    }

    public static ArchitectureBitness fromBits(int bits) {
        return switch (bits) {
            case 16 -> BITS_16;
            case 32 -> BITS_32;
            case 64 -> BITS_64;
            default -> ALL;
        };
    }

    public static ArchitectureBitness parse(String value) {
        if (value == null || value.isBlank()) return ALL;
        String clean = value.trim().toLowerCase(Locale.ROOT);
        if (clean.contains("16")) return BITS_16;
        if (clean.contains("32")) return BITS_32;
        if (clean.contains("64")) return BITS_64;
        return ALL;
    }
}
