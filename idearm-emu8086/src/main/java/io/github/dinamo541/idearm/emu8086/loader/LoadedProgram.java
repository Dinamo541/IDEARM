package io.github.dinamo541.idearm.emu8086.loader;

/**
 * Result of loading an MZ or COM executable into 8086 Real Mode memory.
 */
public record LoadedProgram(
        int loadSegment,
        int programSegment,
        int initialCs,
        int initialIp,
        int initialSs,
        int initialSp,
        int initialDs,
        int initialEs,
        boolean isCom) {
}
