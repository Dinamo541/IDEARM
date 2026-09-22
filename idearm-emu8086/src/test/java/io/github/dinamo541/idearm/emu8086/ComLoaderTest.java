package io.github.dinamo541.idearm.emu8086;

import io.github.dinamo541.idearm.emu8086.cpu.CpuRegisters;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;
import io.github.dinamo541.idearm.emu8086.loader.ComLoader;
import io.github.dinamo541.idearm.emu8086.loader.LoadedProgram;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComLoaderTest {

    @Test
    void testLoadsComAtOffset0100WithPsp() {
        byte[] comCode = new byte[] { (byte) 0xB4, 0x4C, (byte) 0xCD, 0x21 }; // MOV AH, 4Ch; INT 21h

        RealModeMemory memory = new RealModeMemory();
        CpuRegisters regs = new CpuRegisters();

        LoadedProgram prog = ComLoader.load(comCode, memory, regs, List.of("test"), 0x2000);

        assertEquals(0x2000, prog.loadSegment());
        assertEquals(0x2000, regs.cs);
        assertEquals(0x2000, regs.ds);
        assertEquals(0x2000, regs.ss);
        assertEquals(0x0100, regs.ip);
        assertEquals(0xFFFE, regs.sp);

        // Byte at 0x2000:0100
        assertEquals(0xB4, memory.read8(0x2000, 0x0100));

        // PSP at 0x2000:0000 has CD 20
        assertEquals(0xCD, memory.read8(0x2000, 0x0000));
        assertEquals(0x20, memory.read8(0x2000, 0x0001));

        // PSP command line tail at 0x80
        int tailLen = memory.read8(0x2000, 0x0080);
        assertTrue(tailLen > 0);
    }
}
