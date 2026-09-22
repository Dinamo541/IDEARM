package io.github.dinamo541.idearm.emu8086;

import io.github.dinamo541.idearm.emu8086.cpu.CpuRegisters;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RealModeMemoryTest {

    @Test
    void testSegmentOffsetToPhysicalConversion() {
        // Standard real mode formula: (seg << 4) + off (modulo 1MB)
        assertEquals(0x00000, RealModeMemory.toPhysical(0x0000, 0x0000));
        assertEquals(0x10000, RealModeMemory.toPhysical(0x1000, 0x0000));
        assertEquals(0x10050, RealModeMemory.toPhysical(0x1000, 0x0050));
        assertEquals(0x10050, RealModeMemory.toPhysical(0x1005, 0x0000));
        // 20-bit wrap at 1MB
        assertEquals(0x0000F, RealModeMemory.toPhysical(0xFFFF, 0x001F));
    }

    @Test
    void testReadWriteWordAndByte() {
        RealModeMemory memory = new RealModeMemory();
        memory.write8(0x2000, 0x0100, 0x34);
        memory.write8(0x2000, 0x0101, 0x12);

        assertEquals(0x34, memory.read8(0x2000, 0x0100));
        assertEquals(0x12, memory.read8(0x2000, 0x0101));
        // Little endian read
        assertEquals(0x1234, memory.read16(0x2000, 0x0100));

        memory.write16(0x3000, 0x0200, 0xABCD);
        assertEquals(0xCD, memory.read8(0x3000, 0x0200));
        assertEquals(0xAB, memory.read8(0x3000, 0x0201));
        assertEquals(0xABCD, memory.read16(0x3000, 0x0200));
    }

    @Test
    void testPushAndPop() {
        RealModeMemory memory = new RealModeMemory();
        CpuRegisters regs = new CpuRegisters();
        regs.ss = 0x5000;
        regs.sp = 0x1000;

        memory.push(regs, 0xCAFE);
        assertEquals(0x0FFE, regs.sp);
        assertEquals(0xCAFE, memory.read16(0x5000, 0x0FFE));

        memory.push(regs, 0xBABE);
        assertEquals(0x0FFC, regs.sp);

        assertEquals(0xBABE, memory.pop(regs));
        assertEquals(0x0FFE, regs.sp);

        assertEquals(0xCAFE, memory.pop(regs));
        assertEquals(0x1000, regs.sp);
    }
}
