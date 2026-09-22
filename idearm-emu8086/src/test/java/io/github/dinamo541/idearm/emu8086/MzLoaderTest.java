package io.github.dinamo541.idearm.emu8086;

import io.github.dinamo541.idearm.emu8086.cpu.CpuRegisters;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;
import io.github.dinamo541.idearm.emu8086.loader.LoadedProgram;
import io.github.dinamo541.idearm.emu8086.loader.MzLoader;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MzLoaderTest {

    @Test
    void testLoadsMzWithRelocationsAndPsp() {
        byte[] fileBytes = new byte[64];
        ByteBuffer buf = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN);

        // Header:
        buf.putShort(0, (short) 0x5A4D); // 'MZ'
        buf.putShort(2, (short) 64);     // e_cblp: 64 bytes on last page
        buf.putShort(4, (short) 1);      // e_cp: 1 page total
        buf.putShort(6, (short) 1);      // e_crlc: 1 relocation
        buf.putShort(8, (short) 2);      // e_cparhdr: 2 paragraphs header (32 bytes)
        buf.putShort(14, (short) 0x10);  // e_ss: SS is +0x10 relative to program
        buf.putShort(16, (short) 0x200); // e_sp: SP is 0x200
        buf.putShort(20, (short) 0x00);  // e_ip: IP is 0
        buf.putShort(22, (short) 0x00);  // e_cs: CS is 0 relative to program
        buf.putShort(24, (short) 28);    // e_lfarlc: reloc table at offset 28

        // Relocation entry at byte 28 (4 bytes: offset, segment)
        buf.putShort(28, (short) 1);     // reloc offset: 1
        buf.putShort(30, (short) 0);     // reloc segment: 0

        // Program image at byte 32 (header size 2 * 16):
        // B8 00 00  (MOV AX, 0000) -> byte at offset 1 is the 16-bit operand 0000
        fileBytes[32] = (byte) 0xB8;
        fileBytes[33] = 0x00;
        fileBytes[34] = 0x00;
        // CD 21
        fileBytes[35] = (byte) 0xCD;
        fileBytes[36] = 0x21;

        RealModeMemory memory = new RealModeMemory();
        CpuRegisters registers = new CpuRegisters();

        LoadedProgram prog = MzLoader.load(fileBytes, memory, registers, List.of("arg1", "arg2"), 0x1000);

        assertEquals(0x1000, prog.loadSegment());
        assertEquals(0x1010, prog.programSegment());
        assertEquals(0x1010, registers.cs);
        assertEquals(0x0000, registers.ip);
        assertEquals(0x1020, registers.ss); // 0x1010 + 0x10
        assertEquals(0x0200, registers.sp);
        assertEquals(0x1000, registers.ds); // DS points to PSP

        // Check relocation applied at programSegment:0001
        int relocatedVal = memory.read16(0x1010, 1);
        assertEquals(0x1010, relocatedVal);

        // Check PSP INT 20h at loadSegment:0000
        assertEquals(0xCD, memory.read8(0x1000, 0));
        assertEquals(0x20, memory.read8(0x1000, 1));
    }
}
