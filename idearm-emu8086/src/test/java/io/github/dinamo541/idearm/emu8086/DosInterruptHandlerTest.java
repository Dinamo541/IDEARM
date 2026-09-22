package io.github.dinamo541.idearm.emu8086;

import io.github.dinamo541.idearm.emu8086.cpu.Cpu8086;
import io.github.dinamo541.idearm.emu8086.cpu.CpuRegisters;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;
import io.github.dinamo541.idearm.emu8086.dos.DosInterruptHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DosInterruptHandlerTest {

    private CpuRegisters regs;
    private RealModeMemory memory;
    private Cpu8086 cpu;
    private DosInterruptHandler handler;

    @BeforeEach
    void setUp() {
        regs = new CpuRegisters();
        memory = new RealModeMemory();
        cpu = new Cpu8086(regs, memory);
        handler = new DosInterruptHandler();
        cpu.setInterruptHandler(handler);
        regs.cs = 0x1000;
        regs.ip = 0x0000;
        regs.ds = 0x2000;
    }

    @Test
    void testInt21hCharOutputAndStringOutput() {
        AtomicReference<String> lastOutput = new AtomicReference<>("");
        handler.setOutputListener(lastOutput::set);

        // AH = 02h: Print char 'X'
        regs.setAh(0x02);
        regs.setDl('X');
        cpu.triggerInterrupt(0x21);

        assertEquals("X", handler.capturedOutput());
        assertEquals("X", lastOutput.get());

        // AH = 09h: Print "$"-terminated string at DS:DX
        byte[] str = "Hello World!$".getBytes();
        for (int i = 0; i < str.length; i++) {
            memory.write8(0x2000, 0x0050 + i, str[i]);
        }
        regs.setAh(0x09);
        regs.dx = 0x0050;
        cpu.triggerInterrupt(0x21);

        assertEquals("XHello World!", handler.capturedOutput());
        assertEquals("Hello World!", lastOutput.get());
    }

    @Test
    void testInt21hExitProcess() {
        // AH = 4Ch: Terminate with exit code 42
        regs.setAh(0x4C);
        regs.setAl(42);
        cpu.triggerInterrupt(0x21);

        assertEquals(Cpu8086.State.TERMINATED, cpu.state());
        assertEquals(42, cpu.exitCode());
    }

    @Test
    void testInt20hTerminateProgram() {
        cpu.triggerInterrupt(0x20);
        assertEquals(Cpu8086.State.TERMINATED, cpu.state());
        assertEquals(0, cpu.exitCode());
    }

    @Test
    void testInt21hInterruptVectors() {
        // Set vector 0x60 to 1234:5678
        regs.setAh(0x25);
        regs.setAl(0x60);
        regs.ds = 0x1234;
        regs.dx = 0x5678;
        cpu.triggerInterrupt(0x21);

        // Get vector 0x60
        regs.setAh(0x35);
        regs.setAl(0x60);
        regs.es = 0;
        regs.bx = 0;
        cpu.triggerInterrupt(0x21);

        assertEquals(0x1234, regs.es);
        assertEquals(0x5678, regs.bx);
    }
}
