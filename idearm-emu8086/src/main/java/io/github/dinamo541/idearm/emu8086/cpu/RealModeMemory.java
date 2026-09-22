package io.github.dinamo541.idearm.emu8086.cpu;

import java.util.Arrays;

/**
 * 1 Megabyte Real Mode memory space with 20-bit physical addressing (segment:offset).
 */
public final class RealModeMemory {

    public static final int MEMORY_SIZE = 1024 * 1024; // 1 MB (0x100000)
    public static final int ADDRESS_MASK = 0xFFFFF;

    private final byte[] memory = new byte[MEMORY_SIZE];

    public RealModeMemory() {
    }

    public static int toPhysical(int segment, int offset) {
        return (((segment & 0xFFFF) << 4) + (offset & 0xFFFF)) & ADDRESS_MASK;
    }

    public int read8(int segment, int offset) {
        return memory[toPhysical(segment, offset)] & 0xFF;
    }

    public int read16(int segment, int offset) {
        int low = read8(segment, offset);
        int high = read8(segment, (offset + 1) & 0xFFFF);
        return (high << 8) | low;
    }

    public void write8(int segment, int offset, int value) {
        memory[toPhysical(segment, offset)] = (byte) value;
    }

    public void write16(int segment, int offset, int value) {
        write8(segment, offset, value & 0xFF);
        write8(segment, (offset + 1) & 0xFFFF, (value >> 8) & 0xFF);
    }

    public int readPhysical8(int physicalAddr) {
        return memory[physicalAddr & ADDRESS_MASK] & 0xFF;
    }

    public int readPhysical16(int physicalAddr) {
        int low = readPhysical8(physicalAddr);
        int high = readPhysical8(physicalAddr + 1);
        return (high << 8) | low;
    }

    public void writePhysical8(int physicalAddr, int value) {
        memory[physicalAddr & ADDRESS_MASK] = (byte) value;
    }

    public void writePhysical16(int physicalAddr, int value) {
        writePhysical8(physicalAddr, value & 0xFF);
        writePhysical8(physicalAddr + 1, (value >> 8) & 0xFF);
    }

    public byte[] readBlock(int segment, int offset, int length) {
        int len = Math.max(0, length);
        byte[] dest = new byte[len];
        for (int i = 0; i < len; i++) {
            dest[i] = (byte) read8(segment, (offset + i) & 0xFFFF);
        }
        return dest;
    }

    public void loadBlock(int physicalAddr, byte[] data, int srcOffset, int length) {
        if (data == null || length <= 0) return;
        for (int i = 0; i < length; i++) {
            int targetAddr = (physicalAddr + i) & ADDRESS_MASK;
            memory[targetAddr] = data[srcOffset + i];
        }
    }

    public void push(CpuRegisters regs, int val) {
        regs.sp = (regs.sp - 2) & 0xFFFF;
        write16(regs.ss, regs.sp, val);
    }

    public int pop(CpuRegisters regs) {
        int val = read16(regs.ss, regs.sp);
        regs.sp = (regs.sp + 2) & 0xFFFF;
        return val;
    }

    public void clear() {
        Arrays.fill(memory, (byte) 0);
    }
}
