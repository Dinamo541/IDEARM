package io.github.dinamo541.idearm.language.knowledge;

public enum VectorStatus {
    ARCHITECTURAL_CPU("CPU Exception or Hardware Fault"),
    BIOS_STANDARD("BIOS Hardware Service / Device Driver"),
    DOS_KERNEL("MS-DOS Core Kernel Service"),
    RESERVED("Reserved by Architecture / OS"),
    ENVIRONMENT_DEPENDENT("Environment or Host Dependent Hook"),
    UNDOCUMENTED("Undocumented / Internal Interface"),
    PENDING("Unspecified / Pending Research");

    private final String description;

    VectorStatus(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
