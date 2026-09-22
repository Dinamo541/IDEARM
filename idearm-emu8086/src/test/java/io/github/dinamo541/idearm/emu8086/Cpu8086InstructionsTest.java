package io.github.dinamo541.idearm.emu8086;

import io.github.dinamo541.idearm.emu8086.cpu.Cpu8086;
import io.github.dinamo541.idearm.emu8086.cpu.CpuRegisters;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Cpu8086InstructionsTest {

    private CpuRegisters regs;
    private RealModeMemory memory;
    private Cpu8086 cpu;

    @BeforeEach
    void setUp() {
        regs = new CpuRegisters();
        memory = new RealModeMemory();
        cpu = new Cpu8086(regs, memory);
        regs.cs = 0x1000;
        regs.ip = 0x0000;
        regs.ds = 0x2000;
        regs.es = 0x2000;
        regs.ss = 0x3000;
        regs.sp = 0xFFFE;
    }

    private void writeCode(int... bytes) {
        for (int i = 0; i < bytes.length; i++) {
            memory.write8(regs.cs, i, bytes[i]);
        }
    }

    @Test
    void testMovImmediateToReg() {
        // MOV AX, 1234h (B8 34 12)
        // MOV BX, 5678h (BB 78 56)
        writeCode(0xB8, 0x34, 0x12, 0xBB, 0x78, 0x56);

        assertTrue(cpu.step());
        assertEquals(0x1234, regs.ax);
        assertEquals(3, regs.ip);

        assertTrue(cpu.step());
        assertEquals(0x5678, regs.bx);
        assertEquals(6, regs.ip);
    }

    @Test
    void testAddAndSubWithFlags() {
        // MOV AL, 50h   (B0 50)
        // ADD AL, 40h   (04 40) -> AL = 90h, SF=1, ZF=0, CF=0, OF=1 (overflow: +80 + +64 = +144 > 127)
        writeCode(0xB0, 0x50, 0x04, 0x40);

        cpu.step();
        cpu.step();

        assertEquals(0x90, regs.getAl());
        assertTrue(regs.isSf());
        assertFalse(regs.isZf());
        assertFalse(regs.isCf());
        assertTrue(regs.isOf());

        // SUB AL, 90h   (2C 90) -> AL = 0, ZF=1, SF=0, CF=0
        memory.write8(regs.cs, 4, 0x2C);
        memory.write8(regs.cs, 5, 0x90);

        cpu.step();
        assertEquals(0x00, regs.getAl());
        assertTrue(regs.isZf());
        assertFalse(regs.isSf());
        assertFalse(regs.isCf());
    }

    @Test
    void testCallAndRet() {
        // 0000: CALL 0005 (E8 02 00)  [target IP = 3 + 2 = 5]
        // 0003: HLT (F4)
        // 0004: NOP (90)
        // 0005: MOV AX, 42h (B8 42 00)
        // 0008: RET (C3)
        writeCode(
                0xE8, 0x02, 0x00,
                0xF4,
                0x90,
                0xB8, 0x42, 0x00,
                0xC3
        );

        cpu.step(); // CALL 0005
        assertEquals(0x0005, regs.ip);
        assertEquals(0x0003, memory.read16(regs.ss, regs.sp)); // return address pushed

        cpu.step(); // MOV AX, 42h
        assertEquals(0x0042, regs.ax);
        assertEquals(0x0008, regs.ip);

        cpu.step(); // RET
        assertEquals(0x0003, regs.ip); // Returned back to after call!
    }

    @Test
    void testConditionalJumps() {
        // CMP AX, 0   (3D 00 00) -> ZF=1
        // JZ +4        (74 04) -> jumps over next instruction to offset 9
        // MOV BX, 1   (BB 01 00)
        // MOV BX, 2   (BB 02 00)
        writeCode(
                0x3D, 0x00, 0x00,
                0x74, 0x03,
                0xBB, 0x01, 0x00,
                0xBB, 0x02, 0x00
        );
        regs.ax = 0;

        cpu.step(); // CMP AX, 0
        assertTrue(regs.isZf());

        cpu.step(); // JZ +3
        assertEquals(0x0008, regs.ip); // jumped over MOV BX, 1!

        cpu.step(); // MOV BX, 2
        assertEquals(0x0002, regs.bx);
    }

    @Test
    void testLoopInstruction() {
        // MOV CX, 3   (B9 03 00)
        // INC AX      (40)
        // LOOP -3     (E2 FD) -> target: 0003
        writeCode(
                0xB9, 0x03, 0x00,
                0x40,
                0xE2, 0xFD
        );
        regs.ax = 0;

        cpu.step(); // MOV CX, 3
        assertEquals(3, regs.cx);

        // Run loop 3 times
        while (regs.cx > 0) {
            cpu.step();
        }
        assertEquals(3, regs.ax);
        assertEquals(0, regs.cx);
    }

    @Test
    void testRepStosb() {
        // Fill 5 bytes at ES:DI with 'A' (0x41)
        regs.di = 0x0100;
        regs.setAl('A');
        regs.cx = 5;
        // CLD (FC)
        // REP STOSB (F3 AA)
        writeCode(0xFC, 0xF3, 0xAA);

        cpu.step(); // CLD
        assertFalse(regs.isDf());

        cpu.step(); // REP STOSB
        assertEquals(0, regs.cx);
        assertEquals(0x0105, regs.di);
        for (int i = 0; i < 5; i++) {
            assertEquals('A', memory.read8(regs.es, 0x0100 + i));
        }
    }
}
