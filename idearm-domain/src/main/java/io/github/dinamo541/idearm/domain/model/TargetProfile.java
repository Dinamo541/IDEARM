package io.github.dinamo541.idearm.domain.model;

import java.util.Locale;

/** Generated-code properties, independent of the host binary that assembles it. */
public record TargetProfile(String id, String architecture, String cpuBaseline, int codeMode,
                            String processorMode, String platform, String executableFormat,
                            String memoryModel, String objectFormat) {

    public TargetSupport support() { return new TargetSupport(codeMode, objectFormat, executableFormat, platform); }

    /** Whether programs for this target run under DOS, whose tools only accept 8.3 file names. */
    public boolean isDos() {
        return "DOS".equalsIgnoreCase(platform);
    }

    /** The extension of object files for this target, without the dot, always lower case. */
    public String objectExtension() {
        return "ELF".equalsIgnoreCase(objectFormat) ? "o" : "obj";
    }

    /**
     * The extension of the program file, without the dot, always lower case; empty for formats that use none
     * (a Linux ELF program is just its name).
     */
    public String executableExtension() {
        return switch (executableFormat.toUpperCase(Locale.ROOT)) {
            case "COM" -> "com";
            case "ELF32", "ELF64" -> "";
            case "RAW" -> "bin";
            default -> "exe";
        };
    }
}
