package io.github.dinamo541.idearm.language.knowledge;

public enum RegisterGroup {
    G1_GENERAL("General Purpose Registers"),
    G2_POINTERS_INDEXES("Pointer & Index Registers"),
    G3_SEGMENT("Segment Registers"),
    G4_EXECUTION_CONTROL("Execution Control & Flags"),
    G5_X87_SIMD("x87 FPU & SIMD Vector Registers"),
    G6_SYSTEM("System & Control Registers");

    private final String description;

    RegisterGroup(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
