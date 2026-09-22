package io.github.dinamo541.idearm.emu8086.loader;

import io.github.dinamo541.idearm.emu8086.cpu.CpuRegisters;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Loads flat MS-DOS .COM executables into memory at offset 0x0100 with a PSP.
 */
public final class ComLoader {

    public static final int DEFAULT_SEGMENT = 0x1000;

    private ComLoader() {}

    public static LoadedProgram load(byte[] binaryData,
                                     RealModeMemory memory,
                                     CpuRegisters registers,
                                     List<String> args) {
        return load(binaryData, memory, registers, args, DEFAULT_SEGMENT);
    }

    public static LoadedProgram load(byte[] binaryData,
                                     RealModeMemory memory,
                                     CpuRegisters registers,
                                     List<String> args,
                                     int segment) {
        int seg = segment & 0xFFFF;

        // Initialize 256-byte PSP at segment:0x0000
        setupPsp(memory, seg, args);

        // Load binary at segment:0x0100
        if (binaryData != null && binaryData.length > 0) {
            int length = Math.min(binaryData.length, 0xFF00); // COM max ~64KB minus PSP
            int loadPhys = RealModeMemory.toPhysical(seg, 0x0100);
            memory.loadBlock(loadPhys, binaryData, 0, length);
        }

        // Initialize CPU registers for COM execution
        registers.cs = seg;
        registers.ds = seg;
        registers.es = seg;
        registers.ss = seg;
        registers.ip = 0x0100;
        registers.sp = 0xFFFE;
        registers.bp = 0;
        registers.si = 0x0100;
        registers.di = 0xFFFE;
        registers.ax = 0;
        registers.bx = 0;
        registers.cx = 0;
        registers.dx = seg;
        registers.flags = 0x0202;

        return new LoadedProgram(seg, seg, seg, 0x0100, seg, 0xFFFE, seg, seg, true);
    }

    static void setupPsp(RealModeMemory memory, int segment, List<String> args) {
        int pspPhys = RealModeMemory.toPhysical(segment, 0);

        // Clear PSP
        for (int i = 0; i < 256; i++) {
            memory.writePhysical8(pspPhys + i, 0);
        }

        // Offset 0x00: INT 20h opcode (0xCD, 0x20)
        memory.writePhysical8(pspPhys, 0xCD);
        memory.writePhysical8(pspPhys + 1, 0x20);

        // Offset 0x02: Top of memory segment (0x9FFF)
        memory.writePhysical16(pspPhys + 2, 0x9FFF);

        // Offset 0x2C: Environment segment
        memory.writePhysical16(pspPhys + 0x2C, (segment - 0x10) & 0xFFFF);

        // Offset 0x80: Command tail
        String tail = (args != null && !args.isEmpty()) ? " " + String.join(" ", args) : "";
        byte[] tailBytes = tail.getBytes(StandardCharsets.US_ASCII);
        int tailLen = Math.min(tailBytes.length, 126);

        memory.writePhysical8(pspPhys + 0x80, tailLen);
        for (int i = 0; i < tailLen; i++) {
            memory.writePhysical8(pspPhys + 0x81 + i, tailBytes[i]);
        }
        memory.writePhysical8(pspPhys + 0x81 + tailLen, 0x0D); // CR terminator
    }
}
