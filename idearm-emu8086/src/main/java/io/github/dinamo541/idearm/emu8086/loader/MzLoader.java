package io.github.dinamo541.idearm.emu8086.loader;

import io.github.dinamo541.idearm.emu8086.cpu.CpuRegisters;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;

import java.util.List;

/**
 * Parses DOS MZ (Mark Zbikowski) executable headers, relocates segments, and prepares the execution state.
 */
public final class MzLoader {

    public static final int DEFAULT_LOAD_SEGMENT = 0x1000;
    public static final int PSP_PARAGRAPHS = 0x10; // 256 bytes / 16

    private MzLoader() {}

    public static boolean isMz(byte[] data) {
        if (data == null || data.length < 28) {
            return false;
        }
        int magic = (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
        return magic == 0x5A4D || magic == 0x4D5A; // 'MZ' (0x5A4D) or 'ZM' (0x4D5A)
    }

    public static LoadedProgram load(byte[] fileBytes,
                                     RealModeMemory memory,
                                     CpuRegisters registers,
                                     List<String> args) {
        return load(fileBytes, memory, registers, args, DEFAULT_LOAD_SEGMENT);
    }

    public static LoadedProgram load(byte[] fileBytes,
                                     RealModeMemory memory,
                                     CpuRegisters registers,
                                     List<String> args,
                                     int loadSegment) {
        if (!isMz(fileBytes)) {
            throw new IllegalArgumentException("Invalid MZ executable: magic header not found.");
        }

        int eCblp = readWord(fileBytes, 2);
        int eCp = readWord(fileBytes, 4);
        int eCrlc = readWord(fileBytes, 6);
        int eCparhdr = readWord(fileBytes, 8);
        int eSs = readWord(fileBytes, 14);
        int eSp = readWord(fileBytes, 16);
        int eIp = readWord(fileBytes, 20);
        int eCs = readWord(fileBytes, 22);
        int eLfarlc = readWord(fileBytes, 24);

        int loadSeg = loadSegment & 0xFFFF;
        int programSeg = (loadSeg + PSP_PARAGRAPHS) & 0xFFFF;

        // 1. Setup 256-byte PSP at loadSeg:0000
        ComLoader.setupPsp(memory, loadSeg, args);

        // 2. Compute image offset and length in file
        int headerSizeBytes = eCparhdr * 16;
        int totalFileBytesInHeader = (eCblp == 0)
                ? (eCp * 512)
                : ((eCp - 1) * 512 + eCblp);
        int imageSize = Math.max(0, totalFileBytesInHeader - headerSizeBytes);
        if (headerSizeBytes + imageSize > fileBytes.length) {
            imageSize = Math.max(0, fileBytes.length - headerSizeBytes);
        }

        // 3. Load program image into memory at programSeg:0000
        int destPhys = RealModeMemory.toPhysical(programSeg, 0);
        memory.loadBlock(destPhys, fileBytes, headerSizeBytes, imageSize);

        // 4. Apply relocations
        for (int i = 0; i < eCrlc; i++) {
            int tableEntryOffset = eLfarlc + (i * 4);
            if (tableEntryOffset + 4 <= fileBytes.length) {
                int relocOffset = readWord(fileBytes, tableEntryOffset);
                int relocSegment = readWord(fileBytes, tableEntryOffset + 2);

                int patchSeg = (programSeg + relocSegment) & 0xFFFF;
                int currentVal = memory.read16(patchSeg, relocOffset);
                int patchedVal = (currentVal + programSeg) & 0xFFFF;
                memory.write16(patchSeg, relocOffset, patchedVal);
            }
        }

        // 5. Initialize CPU registers
        int initialCs = (programSeg + eCs) & 0xFFFF;
        int initialIp = eIp & 0xFFFF;
        int initialSs = (programSeg + eSs) & 0xFFFF;
        int initialSp = eSp & 0xFFFF;

        registers.cs = initialCs;
        registers.ip = initialIp;
        registers.ss = initialSs;
        registers.sp = initialSp;
        registers.ds = loadSeg; // DS and ES initially point to PSP
        registers.es = loadSeg;
        registers.ax = 0;
        registers.bx = 0;
        registers.cx = 0;
        registers.dx = 0;
        registers.si = 0;
        registers.di = 0;
        registers.bp = 0;
        registers.flags = 0x0202;

        return new LoadedProgram(
                loadSeg,
                programSeg,
                initialCs,
                initialIp,
                initialSs,
                initialSp,
                loadSeg,
                loadSeg,
                false
        );
    }

    private static int readWord(byte[] data, int offset) {
        if (offset + 1 >= data.length) return 0;
        int low = data[offset] & 0xFF;
        int high = data[offset + 1] & 0xFF;
        return (high << 8) | low;
    }
}
