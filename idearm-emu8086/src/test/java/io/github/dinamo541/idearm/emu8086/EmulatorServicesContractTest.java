package io.github.dinamo541.idearm.emu8086;

import io.github.dinamo541.idearm.emu8086.cpu.Cpu8086;
import io.github.dinamo541.idearm.emu8086.cpu.CpuRegisters;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;
import io.github.dinamo541.idearm.emu8086.dos.DosInterruptHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmulatorServicesContractTest {

    private CpuRegisters regs;
    private RealModeMemory memory;
    private Cpu8086 cpu;
    private DosInterruptHandler handler;
    private List<String> problems;

    @BeforeEach
    void setUp() {
        regs = new CpuRegisters();
        memory = new RealModeMemory();
        cpu = new Cpu8086(regs, memory);
        handler = new DosInterruptHandler();
        problems = new ArrayList<>();
        handler.setProblemListener((code, message, arguments) -> problems.add(code + ":" + String.join(",", arguments)));
        cpu.setInterruptHandler(handler);
        regs.cs = 0x1000;
        regs.ip = 0x0000;
        regs.ds = 0x2000;
        regs.es = 0x2000;
    }

    @Test
    void testUnsupportedInt10ReportsProblem() {
        // AH = 02h (Set cursor position - unsupported in built-in emu)
        regs.setAh(0x02);
        regs.setBh(0);
        regs.setDh(10);
        regs.setDl(20);
        cpu.triggerInterrupt(0x10);

        assertEquals(1, problems.size());
        assertEquals("emu.bios.unsupported:10h:02", problems.getFirst());
    }

    @Test
    void testUnsupportedInt16ReportsProblem() {
        // AH = 02h (Get shift flags - unsupported in built-in emu)
        regs.setAh(0x02);
        cpu.triggerInterrupt(0x16);

        assertEquals(1, problems.size());
        assertEquals("emu.bios.unsupported:16h:02", problems.getFirst());
    }

    @Test
    void testInt21h0AhBufferCapacityContract() {
        // Buffer at DS:0100 with max capacity = 5 (max chars = 5 - 1 = 4)
        memory.write8(0x2000, 0x0100, 5);
        regs.setAh(0x0A);
        regs.dx = 0x0100;

        // Provide 6 characters followed by Enter
        handler.provideInput("ABCDEF\r");
        cpu.triggerInterrupt(0x21);

        // Actual count should be 4 (maxLen - 1)
        int actualLen = memory.read8(0x2000, 0x0101);
        assertEquals(4, actualLen);
        assertEquals('A', memory.read8(0x2000, 0x0102));
        assertEquals('B', memory.read8(0x2000, 0x0103));
        assertEquals('C', memory.read8(0x2000, 0x0104));
        assertEquals('D', memory.read8(0x2000, 0x0105));
        assertEquals('\r', memory.read8(0x2000, 0x0106));
    }

    @Test
    void testSupportedInt10TeletypeOutput() {
        regs.setAh(0x0E);
        regs.setAl('Z');
        cpu.triggerInterrupt(0x10);

        assertEquals("Z", handler.capturedOutput());
        assertTrue(problems.isEmpty(), "Supported INT 10h should not report unsupported problem");
    }
}
