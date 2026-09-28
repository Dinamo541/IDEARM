package io.github.dinamo541.idearm.language.knowledge;

public enum ProcessorMode {
    REAL("Real Mode (16-bit)"),
    PROTECTED_16("Protected Mode 16-bit"),
    PROTECTED_32("Protected Mode 32-bit"),
    COMPATIBILITY("Compatibility Mode"),
    LONG("Long Mode (64-bit)"),
    UNKNOWN("Unknown Mode");

    private final String description;

    ProcessorMode(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
