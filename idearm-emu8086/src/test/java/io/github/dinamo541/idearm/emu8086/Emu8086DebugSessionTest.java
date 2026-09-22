package io.github.dinamo541.idearm.emu8086;

import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.DebugEvent;
import io.github.dinamo541.idearm.domain.debug.MemoryView;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;
import io.github.dinamo541.idearm.emu8086.cpu.Cpu8086;
import io.github.dinamo541.idearm.emu8086.cpu.CpuRegisters;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;
import io.github.dinamo541.idearm.emu8086.debug.Emu8086DebugSession;
import io.github.dinamo541.idearm.emu8086.debug.SourceMap;
import io.github.dinamo541.idearm.emu8086.dos.DosInterruptHandler;
import io.github.dinamo541.idearm.emu8086.loader.LoadedProgram;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class Emu8086DebugSessionTest {

    private CpuRegisters regs;
    private RealModeMemory memory;
    private Cpu8086 cpu;
    private DosInterruptHandler dos;
    private SourceMap sourceMap;
    private List<DebugEvent> events;

    @BeforeEach
    void setUp() {
        regs = new CpuRegisters();
        memory = new RealModeMemory();
        cpu = new Cpu8086(regs, memory);
        dos = new DosInterruptHandler();
        cpu.setInterruptHandler(dos);
        sourceMap = new SourceMap();
        events = new ArrayList<>();

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
    void testInitialPauseAndStepInto() throws Exception {
        // MOV AX, 1234h (B8 34 12)
        // MOV BX, 5678h (BB 78 56)
        writeCode(0xB8, 0x34, 0x12, 0xBB, 0x78, 0x56);
        sourceMap.addMapping("MAIN.ASM", 1, 0x0000);
        sourceMap.addMapping("MAIN.ASM", 2, 0x0003);

        LoadedProgram prog = new LoadedProgram(0x1000, 0x1000, 0x1000, 0x0000, 0x3000, 0xFFFE, 0x2000, 0x2000, true);
        try (var session = new Emu8086DebugSession(cpu, memory, prog, sourceMap, List.of(), dos, events::add)) {
            // Initial event should be Paused at 0x0000 (line 1)
            assertEquals(1, events.size());
            assertInstanceOf(DebugEvent.Paused.class, events.getFirst());
            var p1 = (DebugEvent.Paused) events.getFirst();
            assertEquals(1, p1.line());
            assertEquals(0x0000, p1.registers().ax());

            // Step into
            session.stepInto();
            Thread.sleep(100);

            var p2 = (DebugEvent.Paused) events.getLast();
            assertEquals(2, p2.line());
            assertEquals(0x1234, p2.registers().ax());
            assertEquals(0x0003, p2.registers().ip());
        }
    }

    @Test
    void testStepOverCall() throws Exception {
        // 0000: CALL 0005 (E8 02 00)
        // 0003: NOP (90)
        // 0004: HLT (F4)
        // 0005: MOV AX, 42h (B8 42 00)
        // 0008: RET (C3)
        writeCode(
                0xE8, 0x02, 0x00,
                0x90,
                0xF4,
                0xB8, 0x42, 0x00,
                0xC3
        );
        sourceMap.addMapping("MAIN.ASM", 1, 0x0000);
        sourceMap.addMapping("MAIN.ASM", 2, 0x0003);
        sourceMap.addMapping("MAIN.ASM", 5, 0x0005);

        LoadedProgram prog = new LoadedProgram(0x1000, 0x1000, 0x1000, 0x0000, 0x3000, 0xFFFE, 0x2000, 0x2000, true);
        try (var session = new Emu8086DebugSession(cpu, memory, prog, sourceMap, List.of(), dos, events::add)) {
            // Step Over the CALL
            session.stepOver();
            Thread.sleep(150);

            // Subroutine executed and we are paused at offset 0x0003 (line 2)
            assertEquals(0x0042, regs.ax);
            assertEquals(0x0003, regs.ip);
        }
    }

    @Test
    void testResumeToBreakpoint() throws Exception {
        // 0000: MOV AX, 1   (B8 01 00)
        // 0003: MOV BX, 2   (BB 02 00)  <- Breakpoint here (line 2)
        // 0006: MOV CX, 3   (B9 03 00)
        writeCode(
                0xB8, 0x01, 0x00,
                0xBB, 0x02, 0x00,
                0xB9, 0x03, 0x00
        );
        sourceMap.addMapping("MAIN.ASM", 1, 0x0000);
        sourceMap.addMapping("MAIN.ASM", 2, 0x0003);
        sourceMap.addMapping("MAIN.ASM", 3, 0x0006);

        var bp = new Breakpoint("MAIN.ASM", 2, true);
        LoadedProgram prog = new LoadedProgram(0x1000, 0x1000, 0x1000, 0x0000, 0x3000, 0xFFFE, 0x2000, 0x2000, true);

        try (var session = new Emu8086DebugSession(cpu, memory, prog, sourceMap, List.of(bp), dos, events::add)) {
            session.resume();
            Thread.sleep(100);

            // Stopped at line 2 (offset 0x0003)
            assertEquals(0x0001, regs.ax);
            assertEquals(0x0000, regs.bx); // Not executed yet
            assertEquals(0x0003, regs.ip);
        }
    }

    @Test
    void testReadMemoryAndStack() {
        memory.write8(0x2000, 0x0010, 0xAA);
        memory.write8(0x2000, 0x0011, 0xBB);
        memory.push(regs, 0x1234);

        LoadedProgram prog = new LoadedProgram(0x1000, 0x1000, 0x1000, 0x0000, 0x3000, 0xFFFE, 0x2000, 0x2000, true);
        try (var session = new Emu8086DebugSession(cpu, memory, prog, sourceMap, List.of(), dos, events::add)) {
            MemoryView view = session.readMemory(0x2000, 0x0010, 2);
            assertEquals(2, view.length());
            assertEquals((byte) 0xAA, view.getByte(0));
            assertEquals((byte) 0xBB, view.getByte(1));

            var stack = session.stack(1);
            assertEquals(1, stack.size());
            assertEquals(0x1234, stack.getFirst().value());
        }
    }

    @Test
    void evaluatesWatchesWrittenAsInTheSource() {
        memory.write8(0x2000, 0x0012, 0x34);
        memory.write8(0x2000, 0x0013, 0x12);
        memory.write8(0x3000, 0x0004, 0x7F);
        regs.bx = 0x0010;
        regs.bp = 0x0002;
        regs.ax = 0x0A41;

        LoadedProgram prog = new LoadedProgram(0x1000, 0x1000, 0x1000, 0x0000, 0x3000, 0xFFFE, 0x2000, 0x2000, true);
        try (var session = new Emu8086DebugSession(cpu, memory, prog, sourceMap, List.of(), dos, events::add)) {
            assertEquals("2625 (0x0A41)", session.evaluateExpression("ax").orElseThrow());
            assertEquals("65 (0x41)", session.evaluateExpression("AL").orElseThrow());
            assertEquals("4660 (0x1234)", session.evaluateExpression("[BX+2]").orElseThrow());
            assertEquals("52 (0x34)", session.evaluateExpression("byte [bx + 2]").orElseThrow());
            assertEquals("4660 (0x1234)", session.evaluateExpression("[DS:12h]").orElseThrow());
            // BP addresses the stack segment, as on a real 8086.
            assertEquals("127 (0x7F)", session.evaluateExpression("byte [bp+2]").orElseThrow());
            assertEquals("255 (0x00FF)", session.evaluateExpression("100h - 1").orElseThrow());
            assertTrue(session.evaluateExpression("message").isEmpty(), "Labels are not known to the emulator");
        }
    }

    @Test
    void testStopCompletesExitFuture() throws Exception {
        LoadedProgram prog = new LoadedProgram(0x1000, 0x1000, 0x1000, 0x0000, 0x3000, 0xFFFE, 0x2000, 0x2000, true);
        try (var session = new Emu8086DebugSession(cpu, memory, prog, sourceMap, List.of(), dos, events::add)) {
            session.stop();
            ExitInfo info = session.exit().get(1, TimeUnit.SECONDS);
            assertTrue(info.wasStopped());
            assertEquals(SessionState.STOPPED, session.state());
        }
    }
}
