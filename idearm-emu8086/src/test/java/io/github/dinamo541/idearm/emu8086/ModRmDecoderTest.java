package io.github.dinamo541.idearm.emu8086;

import io.github.dinamo541.idearm.emu8086.cpu.CpuRegisters;
import io.github.dinamo541.idearm.emu8086.cpu.ModRmDecoder;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModRmDecoderTest {

    @Test
    void testDirectRegisterAddressing() {
        CpuRegisters regs = new CpuRegisters();
        RealModeMemory memory = new RealModeMemory();

        // Mod = 11b (register direct), Reg = 000b (AX), RM = 011b (BX) -> 0xC3
        var decoded = ModRmDecoder.decode(0xC3, regs, memory, -1);
        assertTrue(decoded.isRegister());
        assertEquals(0, decoded.reg());
        assertEquals(3, decoded.rm());
        assertEquals(0, decoded.bytesConsumed());

        regs.bx = 0x55AA;
        assertEquals(0x55AA, ModRmDecoder.readRm16(regs, memory, decoded));
        ModRmDecoder.writeRm16(regs, memory, decoded, 0x1234);
        assertEquals(0x1234, regs.bx);
    }

    @Test
    void testBxSiAddressing() {
        CpuRegisters regs = new CpuRegisters();
        RealModeMemory memory = new RealModeMemory();
        regs.ds = 0x2000;
        regs.bx = 0x0100;
        regs.si = 0x0020;

        // Mod = 00b, Reg = 000b, RM = 000b ([BX+SI]) -> 0x00
        var decoded = ModRmDecoder.decode(0x00, regs, memory, -1);
        assertFalse(decoded.isRegister());
        assertEquals(0x2000, decoded.segment());
        assertEquals(0x0120, decoded.offset());
        assertEquals(0, decoded.bytesConsumed());
    }

    @Test
    void testBpDiWithDisp8AddressingDefaultsToStackSegment() {
        CpuRegisters regs = new CpuRegisters();
        RealModeMemory memory = new RealModeMemory();
        regs.ss = 0x4000;
        regs.ds = 0x2000;
        regs.cs = 0x1000;
        regs.ip = 0x0005;
        regs.bp = 0x0200;
        regs.di = 0x0010;

        // Write disp8 = +4 at CS:IP
        memory.write8(0x1000, 0x0005, 0x04);

        // Mod = 01b (disp8), Reg = 000b, RM = 011b ([BP+DI+disp8]) -> 0x43
        var decoded = ModRmDecoder.decode(0x43, regs, memory, -1);
        assertFalse(decoded.isRegister());
        assertEquals(0x4000, decoded.segment()); // defaults to SS because BP is used
        assertEquals(0x0214, decoded.offset());  // 0x0200 + 0x0010 + 0x04 = 0x0214
        assertEquals(1, decoded.bytesConsumed());
    }

    @Test
    void testSegmentOverridePrefixTakesPrecedence() {
        CpuRegisters regs = new CpuRegisters();
        RealModeMemory memory = new RealModeMemory();
        regs.es = 0x7000;
        regs.ss = 0x4000;
        regs.cs = 0x1000;
        regs.ip = 0x0005;
        regs.bp = 0x0200;

        memory.write8(0x1000, 0x0005, 0x00);

        // Mod = 01b, RM = 110b ([BP+disp8]), override ES (index 0)
        var decoded = ModRmDecoder.decode(0x46, regs, memory, 0);
        assertEquals(0x7000, decoded.segment()); // Overridden to ES
    }
}
