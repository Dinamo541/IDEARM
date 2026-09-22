package io.github.dinamo541.idearm.emu8086;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.debug.DebugEvent;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.emu8086.cpu.Cpu8086;
import io.github.dinamo541.idearm.emu8086.cpu.CpuRegisters;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;
import io.github.dinamo541.idearm.emu8086.debug.Emu8086DebugSession;
import io.github.dinamo541.idearm.emu8086.debug.SourceMap;
import io.github.dinamo541.idearm.emu8086.dos.DosInterruptHandler;
import io.github.dinamo541.idearm.emu8086.loader.LoadedProgram;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Mistakes students make (division by zero, a missing '$') and programs that read the keyboard. */
class Emu8086FaultsAndInputTest {

    private CpuRegisters regs;
    private RealModeMemory memory;
    private Cpu8086 cpu;
    private DosInterruptHandler dos;
    private SourceMap sourceMap;
    private final List<DebugEvent> events = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        regs = new CpuRegisters();
        memory = new RealModeMemory();
        cpu = new Cpu8086(regs, memory);
        dos = new DosInterruptHandler();
        cpu.setInterruptHandler(dos);
        sourceMap = new SourceMap();
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

    private Emu8086DebugSession session() {
        var program = new LoadedProgram(0x1000, 0x1000, 0x1000, 0x0000, 0x3000, 0xFFFE, 0x2000, 0x2000, true);
        return new Emu8086DebugSession(cpu, memory, program, sourceMap, List.of(), dos, events::add);
    }

    private <T extends DebugEvent> T await(Class<T> type, Predicate<T> condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            for (DebugEvent event : events) {
                if (type.isInstance(event) && condition.test(type.cast(event))) {
                    return type.cast(event);
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("No " + type.getSimpleName() + " in " + events);
    }

    @Test
    void cbwKeepsAxSixteenBitsSoALaterCarryIsRight() {
        // MOV AL, 0FFh / CBW / ADD AX, 1
        writeCode(0xB0, 0xFF, 0x98, 0x05, 0x01, 0x00);
        cpu.step();
        cpu.step();
        assertEquals(0xFFFF, regs.ax);
        cpu.step();
        assertEquals(0x0000, regs.ax);
        assertTrue(regs.isCf(), "0FFFFh + 1 carries");
        assertTrue(regs.isZf());
    }

    @Test
    void aamSetsTheZeroFlagFromAl() {
        // MOV AL, 0Ah / AAM  ->  AH = 1, AL = 0
        writeCode(0xB0, 0x0A, 0xD4, 0x0A);
        cpu.step();
        cpu.step();
        assertEquals(0x0100, regs.ax);
        assertTrue(regs.isZf(), "ZF describes AL, which is zero");
    }

    @Test
    void divisionByZeroPausesOnTheDivLineWithAnExplanation() throws Exception {
        // 0000: MOV AX, 10 / 0003: MOV BL, 0 / 0005: DIV BL / 0007: MOV AX, 4C00h / 000A: INT 21h
        writeCode(0xB8, 0x0A, 0x00, 0xB3, 0x00, 0xF6, 0xF3, 0xB8, 0x00, 0x4C, 0xCD, 0x21);
        sourceMap.addMapping("src/main.asm", 7, 0x0000);
        sourceMap.addMapping("src/main.asm", 8, 0x0003);
        sourceMap.addMapping("src/main.asm", 9, 0x0005);

        try (var session = session()) {
            session.resume();

            var problem = await(DebugEvent.Problem.class, p -> true);
            assertEquals("emu.divide-error", problem.diagnostic().code());
            assertEquals(Severity.ERROR, problem.diagnostic().severity());
            assertNotNull(problem.diagnostic().location());
            assertEquals(9, problem.diagnostic().location().line());
            var paused = await(DebugEvent.Paused.class, p -> p.line() == 9);
            assertEquals(0x0005, paused.registers().ip(), "IP points at the DIV itself");

            session.resume();
            ExitInfo exit = session.exit().get(3, TimeUnit.SECONDS);
            assertEquals(255, exit.exitCode());
            assertTrue(exit.message().contains("Division by zero"), exit.message());
        }
    }

    @Test
    void aProgramThatInstalledItsOwnDivideHandlerGetsItCalled() {
        // Vector 0 -> 1000:0010, where the handler sets BX and halts.
        memory.writePhysical16(0, 0x0010);
        memory.writePhysical16(2, 0x1000);
        writeCode(0xB3, 0x00, 0xF6, 0xF3);
        memory.write8(0x1000, 0x0010, 0xBB);
        memory.write8(0x1000, 0x0011, 0x34);
        memory.write8(0x1000, 0x0012, 0x12);
        cpu.step();
        cpu.step();
        cpu.step();
        assertEquals(0x1234, regs.bx);
        assertEquals(Cpu8086.State.RUNNING, cpu.state());
    }

    @Test
    void anUnsupportedOpcodeIsExplainedInsteadOfEndingQuietly() {
        writeCode(0x0F);
        cpu.step();
        assertEquals(Cpu8086.State.FAULTED, cpu.state());
        assertEquals("emu.invalid-opcode", cpu.fault().code());
        assertEquals(List.of("0F"), cpu.fault().arguments());
    }

    @Test
    void anUnsetInterruptVectorIsExplainedInsteadOfRunningGarbage() {
        writeCode(0xCD, 0x1A); // INT 1Ah
        cpu.step();
        assertEquals(Cpu8086.State.FAULTED, cpu.state());
        assertEquals("emu.interrupt.unsupported", cpu.fault().code());
        assertEquals(0x0000, regs.ip, "IP points at the INT");
    }

    @Test
    void aStringWithoutItsDollarStopsInsteadOfHanging() {
        // MOV AH, 09h / MOV DX, 0 / INT 21h, with no '$' anywhere in the data segment
        writeCode(0xB4, 0x09, 0xBA, 0x00, 0x00, 0xCD, 0x21);
        for (int i = 0; i < 0x10000; i++) {
            memory.write8(0x2000, i, 'A');
        }
        cpu.step();
        cpu.step();
        cpu.step();
        assertEquals(Cpu8086.State.FAULTED, cpu.state());
        assertEquals("emu.string.unterminated", cpu.fault().code());
    }

    @Test
    void anUnsupportedDosFunctionIsReportedOnceAndTheProgramGoesOn() throws Exception {
        // MOV AH, 2Ch (get time) / INT 21h / INT 21h / HLT
        writeCode(0xB4, 0x2C, 0xCD, 0x21, 0xCD, 0x21, 0xF4);
        try (var session = session()) {
            session.resume();
            session.exit().get(3, TimeUnit.SECONDS);
        }
        long reports = events.stream().filter(DebugEvent.Problem.class::isInstance).count();
        assertEquals(1, reports);
        var problem = (DebugEvent.Problem) events.stream().filter(DebugEvent.Problem.class::isInstance).findFirst().orElseThrow();
        assertEquals("emu.dos.unsupported", problem.diagnostic().code());
        assertEquals(Severity.WARNING, problem.diagnostic().severity());
    }

    @Test
    void aProgramReadingALineWaitsForTheUserToTypeIt() throws Exception {
        // Buffer at DS:0100 (max 10) / MOV AH, 0Ah / MOV DX, 0100h / INT 21h / MOV AX, 4C00h / INT 21h
        memory.write8(0x2000, 0x0100, 10);
        writeCode(0xB4, 0x0A, 0xBA, 0x00, 0x01, 0xCD, 0x21, 0xB8, 0x00, 0x4C, 0xCD, 0x21);

        try (var session = session()) {
            session.resume();
            await(DebugEvent.WaitingForInput.class, w -> true);
            assertFalse(session.exit().isDone(), "The program waits for the keyboard instead of reading Enter");

            session.sendInput("Ana\r");
            session.exit().get(3, TimeUnit.SECONDS);
        }
        assertEquals(3, memory.read8(0x2000, 0x0101), "Characters read");
        assertEquals('A', memory.read8(0x2000, 0x0102));
        assertEquals('n', memory.read8(0x2000, 0x0103));
        assertEquals('a', memory.read8(0x2000, 0x0104));
        assertTrue(dos.capturedOutput().startsWith("Ana"), "The typed line is echoed: " + dos.capturedOutput());
    }

    @Test
    void readCharacterReturnsTheTypedKey() throws Exception {
        // MOV AH, 01h / INT 21h / HLT
        writeCode(0xB4, 0x01, 0xCD, 0x21, 0xF4);
        try (var session = session()) {
            session.sendInput("x");
            session.resume();
            session.exit().get(3, TimeUnit.SECONDS);
        }
        assertEquals('x', regs.getAl());
    }

    @Test
    void stoppingWhileTheProgramWaitsForAKeyEndsTheSession() throws Exception {
        // MOV AH, 07h / INT 21h (read a key without echo) / HLT
        writeCode(0xB4, 0x07, 0xCD, 0x21, 0xF4);
        var session = session();
        session.resume();
        await(DebugEvent.WaitingForInput.class, w -> true);

        long started = System.nanoTime();
        session.stop();
        ExitInfo exit = session.exit().get(1, TimeUnit.SECONDS);

        assertTrue(exit.wasStopped());
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000);
        assertInstanceOf(DebugEvent.Stopped.class, events.getLast());
    }
}
