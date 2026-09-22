package io.github.dinamo541.idearm.language.catalog;

import java.util.List;
import java.util.Objects;

/**
 * Educational metadata for an x86 Assembly instruction.
 */
public record InstructionInfo(
        String mnemonic,
        String summaryEn,
        String summaryEs,
        List<String> syntaxVariants,
        CpuLevel minCpu,
        FlagSummary flags,
        String descriptionEn,
        String descriptionEs,
        String example
) {
    public InstructionInfo {
        Objects.requireNonNull(mnemonic, "mnemonic cannot be null");
        Objects.requireNonNull(summaryEn, "summaryEn cannot be null");
        Objects.requireNonNull(summaryEs, "summaryEs cannot be null");
        syntaxVariants = syntaxVariants != null ? List.copyOf(syntaxVariants) : List.of();
        flags = flags != null ? flags : FlagSummary.none();
        minCpu = minCpu != null ? minCpu : CpuLevel.CPU_8086;
    }
}
