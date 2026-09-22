package io.github.dinamo541.idearm.emu8086.loader;

import io.github.dinamo541.idearm.emu8086.cpu.CpuRegisters;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;

import java.util.List;

/**
 * High-level loader detecting executable format (MZ or COM) and delegating to the appropriate loader.
 */
public final class ProgramLoader {

    private ProgramLoader() {}

    public static LoadedProgram load(byte[] binaryData,
                                     RealModeMemory memory,
                                     CpuRegisters registers,
                                     List<String> args) {
        if (MzLoader.isMz(binaryData)) {
            return MzLoader.load(binaryData, memory, registers, args);
        } else {
            return ComLoader.load(binaryData, memory, registers, args);
        }
    }
}
