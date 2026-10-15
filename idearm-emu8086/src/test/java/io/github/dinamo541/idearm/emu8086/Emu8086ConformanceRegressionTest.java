package io.github.dinamo541.idearm.emu8086;

import static org.junit.jupiter.api.Assertions.*;

import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.CallFrame;
import io.github.dinamo541.idearm.domain.debug.DebugEvent;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;
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
import org.junit.jupiter.api.Timeout;

/**
 * Deep 8086 debugger conformance regression suite covering:
 * - Arithmetic and flag calculations (CF, ZF, SF, OF, AF, PF) at defined stops
 * - Nested CALL/RET, stack unwinding, step into/over/out, recursive routines
 * - Conditional branches and loop iterations
 * - Repeated breakpoints inside loops (continue does not immediately re-trap)
 * - Pause and stop of infinite loops with bounded execution
 * - Clean restart via fresh session construction
 * - Keyboard buffered input (pasted strings, backspace editing, stop while blocked)
 * - Actionable fault diagnostics (DIV 0, invalid opcodes, unsupported INTs)
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class Emu8086ConformanceRegressionTest {

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
        events = new CopyOnWriteArrayList<>();

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

    private LoadedProgram createProgram() {
        return new LoadedProgram(0x1000, 0x1000, 0x1000, 0x0000, 0x3000, 0xFFFE, 0x2000, 0x2000, true);
    }

    private Emu8086DebugSession createSession(List<Breakpoint> breakpoints) {
        return new Emu8086DebugSession(cpu, memory, createProgram(), sourceMap, breakpoints, dos, events::add);
    }

    private <T extends DebugEvent> T awaitEvent(int fromIndex, Class<T> type, Predicate<T> condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            for (int i = fromIndex; i < events.size(); i++) {
                DebugEvent event = events.get(i);
                if (type.isInstance(event) && condition.test(type.cast(event))) {
                    return type.cast(event);
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for " + type.getSimpleName()
                + " from index " + fromIndex + " in events: " + events);
    }

    private DebugEvent.Paused awaitNextPause(int fromIndex, int expectedLine) throws InterruptedException {
        return awaitEvent(fromIndex, DebugEvent.Paused.class, p -> p.line() == expectedLine);
    }

    // =========================================================================
    // 1. Arithmetic & Flag Conformance at Defined Stops
    // =========================================================================

    @Test
    void arithmeticAndFlagsAtDefinedStops() {
        // 1. ADD signed overflow: 7FFFh + 1 = 8000h (OF=1, SF=1, ZF=0, CF=0)
        regs.ax = 0x7FFF;
        writeCode(0x05, 0x01, 0x00); // ADD AX, 1
        cpu.step();
        assertEquals(0x8000, regs.ax);
        assertTrue(regs.isOf(), "7FFFh + 1 overflows signed 16-bit");
        assertTrue(regs.isSf(), "8000h is negative");
        assertFalse(regs.isZf());
        assertFalse(regs.isCf(), "7FFFh + 1 does not carry unsigned");

        // 2. ADD unsigned carry & zero: FFFFh + 1 = 0000h (CF=1, ZF=1, SF=0, OF=0, PF=1)
        regs.ax = 0xFFFF;
        regs.ip = 0x0000;
        writeCode(0x05, 0x01, 0x00); // ADD AX, 1
        cpu.step();
        assertEquals(0x0000, regs.ax);
        assertTrue(regs.isCf(), "FFFFh + 1 carries");
        assertTrue(regs.isZf(), "Result is zero");
        assertFalse(regs.isSf());
        assertFalse(regs.isOf());
        assertTrue(regs.isPf(), "0 parity is even");

        // 3. ADC with carry-in: 0 + 0 + CF(1) = 1 (OF=0, CF=0, ZF=0)
        regs.bx = 0x0000;
        regs.ip = 0x0000;
        writeCode(0x83, 0xD3, 0x00); // ADC BX, 0
        cpu.step();
        assertEquals(0x0001, regs.bx);
        assertFalse(regs.isCf(), "Carry consumed");
        assertFalse(regs.isZf());
        assertFalse(regs.isOf());

        // 4. ADC 8-bit overflow with carry across 7Fh boundary: 0 + 7Fh + CF(1) = 80h (OF=1)
        regs.setCf(true);
        regs.setAl(0x00);
        regs.ip = 0x0000;
        writeCode(0x14, 0x7F); // ADC AL, 7Fh
        cpu.step();
        assertEquals(0x80, regs.getAl());
        assertTrue(regs.isOf(), "0 + 7Fh + 1 = 80h overflows signed 8-bit");
        assertTrue(regs.isSf());

        // 5. SUB borrow: 5 - 10 = FFFBh (-5) (CF=1, SF=1, ZF=0, OF=0)
        regs.cx = 5;
        regs.ip = 0x0000;
        writeCode(0x83, 0xE9, 0x0A); // SUB CX, 10
        cpu.step();
        assertEquals(0xFFFB, regs.cx);
        assertTrue(regs.isCf(), "5 - 10 borrows unsigned");
        assertTrue(regs.isSf(), "FFFBh is negative");
        assertFalse(regs.isZf());
        assertFalse(regs.isOf());

        // 6. SBB borrow across 80h boundary: 80h - 0 - CF(1) = 7Fh (OF=1, CF=0, SF=0)
        regs.setCf(true);
        regs.setAl(0x80);
        regs.ip = 0x0000;
        writeCode(0x1C, 0x00); // SBB AL, 0
        cpu.step();
        assertEquals(0x7F, regs.getAl());
        assertTrue(regs.isOf(), "-128 - 1 = +127 underflows signed 8-bit");
        assertFalse(regs.isCf());
        assertFalse(regs.isSf());

        // 7. NEG edge cases: NEG 80h = 80h with OF=1, CF=1; NEG 0 = 0 with OF=0, CF=0
        regs.setAl(0x80);
        regs.ip = 0x0000;
        writeCode(0xF6, 0xD8); // NEG AL
        cpu.step();
        assertEquals(0x80, regs.getAl());
        assertTrue(regs.isOf(), "Negating -128 cannot be represented in signed byte");
        assertTrue(regs.isCf(), "NEG non-zero sets carry");

        regs.setAl(0x00);
        regs.ip = 0x0000;
        writeCode(0xF6, 0xD8); // NEG AL
        cpu.step();
        assertEquals(0x00, regs.getAl());
        assertFalse(regs.isOf());
        assertFalse(regs.isCf(), "NEG zero clears carry");
        assertTrue(regs.isZf());

        // 8. IDIV with negative 32-bit dividend in DX:AX:
        // DX:AX = FFFF:FFFE (-2), divide by BX=2 -> AX = FFFF (-1), DX = 0
        regs.dx = 0xFFFF;
        regs.ax = 0xFFFE;
        regs.bx = 2;
        regs.ip = 0x0000;
        writeCode(0xF7, 0xFB); // IDIV BX
        cpu.step();
        assertEquals(Cpu8086.State.RUNNING, cpu.state(), "IDIV of negative dividend should not fault");
        assertEquals(0xFFFF, regs.ax, "Quotient -1");
        assertEquals(0x0000, regs.dx, "Remainder 0");
    }

    @Test
    void adcAndSbb16BitBoundaryFlags() {
        // 1. 16-bit ADC signed overflow at 7FFFh boundary:
        // 7FFFh + 0000h + CF(1) = 8000h (OF=1, SF=1, ZF=0, CF=0, AF=1)
        regs.setCf(true);
        regs.ax = 0x7FFF;
        regs.ip = 0x0000;
        writeCode(0x15, 0x00, 0x00); // ADC AX, 0000h
        cpu.step();
        assertEquals(0x8000, regs.ax);
        assertTrue(regs.isOf(), "7FFFh + 0 + CF(1) overflows signed 16-bit to 8000h (-32768)");
        assertTrue(regs.isSf(), "8000h is negative in 16-bit");
        assertFalse(regs.isZf());
        assertFalse(regs.isCf(), "Does not carry out of 16-bit");
        assertTrue(regs.isAf(), "Carry occurred from low nibble Fh + 1");

        // 7FFEh + 0001h + CF(1) = 8000h via register-register ADC AX, BX (11 D8)
        regs.setCf(true);
        regs.ax = 0x7FFE;
        regs.bx = 0x0001;
        regs.ip = 0x0000;
        writeCode(0x11, 0xD8); // ADC AX, BX
        cpu.step();
        assertEquals(0x8000, regs.ax);
        assertTrue(regs.isOf(), "7FFEh + 1 + CF(1) overflows signed 16-bit");
        assertTrue(regs.isSf());
        assertFalse(regs.isCf());

        // 8000h + 8000h + CF(0) = 0000h (OF=1, CF=1, ZF=1, SF=0)
        regs.setCf(false);
        regs.ax = 0x8000;
        regs.bx = 0x8000;
        regs.ip = 0x0000;
        writeCode(0x11, 0xD8); // ADC AX, BX
        cpu.step();
        assertEquals(0x0000, regs.ax);
        assertTrue(regs.isOf(), "-32768 + -32768 overflows signed 16-bit");
        assertTrue(regs.isCf(), "8000h + 8000h carries out of 16-bit");
        assertTrue(regs.isZf());
        assertFalse(regs.isSf());

        // 2. 16-bit ADC unsigned carry & zero at FFFFh boundary:
        // FFFFh + 0000h + CF(1) = 0000h (CF=1, ZF=1, SF=0, OF=0, AF=1, PF=1)
        regs.setCf(true);
        regs.ax = 0xFFFF;
        regs.ip = 0x0000;
        writeCode(0x15, 0x00, 0x00); // ADC AX, 0000h
        cpu.step();
        assertEquals(0x0000, regs.ax);
        assertTrue(regs.isCf(), "FFFFh + 0 + CF(1) carries out of 16-bit");
        assertTrue(regs.isZf());
        assertFalse(regs.isSf());
        assertFalse(regs.isOf(), "Unsigned wrap does not overflow signed opposite signs");
        assertTrue(regs.isAf(), "Low nibble carries Fh + 1");
        assertTrue(regs.isPf(), "Low byte 00h has even parity");

        // 3. 16-bit SBB signed overflow at 8000h boundary:
        // 8000h - 0000h - CF(1) = 7FFFh (OF=1, SF=0, ZF=0, CF=0, AF=1)
        regs.setCf(true);
        regs.ax = 0x8000;
        regs.ip = 0x0000;
        writeCode(0x1D, 0x00, 0x00); // SBB AX, 0000h
        cpu.step();
        assertEquals(0x7FFF, regs.ax);
        assertTrue(regs.isOf(), "-32768 - 1 = +32767 underflows signed 16-bit (OF=1)");
        assertFalse(regs.isSf(), "7FFFh is positive");
        assertFalse(regs.isZf());
        assertFalse(regs.isCf(), "No unsigned borrow required for 8000h - 1");
        assertTrue(regs.isAf(), "Borrow occurred from low nibble 0 - 1");

        // 8000h - 0001h - CF(0) = 7FFFh via SBB AX, BX (19 D8)
        regs.setCf(false);
        regs.ax = 0x8000;
        regs.bx = 0x0001;
        regs.ip = 0x0000;
        writeCode(0x19, 0xD8); // SBB AX, BX
        cpu.step();
        assertEquals(0x7FFF, regs.ax);
        assertTrue(regs.isOf(), "8000h - 1 underflows signed 16-bit");
        assertFalse(regs.isSf());
        assertFalse(regs.isCf());

        // 4. 16-bit SBB unsigned borrow across 0000h boundary:
        // 0000h - 0000h - CF(1) = FFFFh (CF=1, SF=1, ZF=0, OF=0, AF=1, PF=1)
        regs.setCf(true);
        regs.ax = 0x0000;
        regs.ip = 0x0000;
        writeCode(0x1D, 0x00, 0x00); // SBB AX, 0000h
        cpu.step();
        assertEquals(0xFFFF, regs.ax);
        assertTrue(regs.isCf(), "0 - 1 borrows unsigned (CF=1)");
        assertTrue(regs.isSf(), "FFFFh is negative");
        assertFalse(regs.isZf());
        assertFalse(regs.isOf(), "0 - 1 does not overflow signed");
        assertTrue(regs.isAf(), "Low nibble borrows (AF=1)");
        assertTrue(regs.isPf(), "Low byte FFh has even parity (8 ones)");

        // 0000h - 7FFFh - CF(1) = 8000h (CF=1, SF=1, ZF=0, OF=0)
        regs.setCf(true);
        regs.ax = 0x0000;
        regs.bx = 0x7FFF;
        regs.ip = 0x0000;
        writeCode(0x19, 0xD8); // SBB AX, BX
        cpu.step();
        assertEquals(0x8000, regs.ax);
        assertTrue(regs.isCf(), "0 - 7FFFh - 1 borrows unsigned");
        assertTrue(regs.isSf());
        assertFalse(regs.isZf());
        assertFalse(regs.isOf());
    }

    // =========================================================================
    // 2. Nested CALL/RET, Call Stack & Step Into/Over/Out
    // =========================================================================

    @Test
    void nestedCallsAndStepIntoOverOut() throws Exception {
        // Layout:
        // 0000: E8 05 00       CALL 0008 (len 3 -> 0003, 0003 + 5 = 0008)
        // 0003: 8B D8          MOV BX, AX (len 2 -> 0005)
        // 0005: B8 00 4C       MOV AX, 4C00h (len 3 -> 0008)
        // 0008: 55             PUSH BP (len 1 -> 0009)
        // 0009: 8B EC          MOV BP, SP (len 2 -> 000B)
        // 000B: E8 04 00       CALL 0012 (len 3 -> 000E, 000E + 4 = 0012)
        // 000E: 5D             POP BP (len 1 -> 000F)
        // 000F: C3             RET (len 1 -> 0010)
        // 0010: 90             NOP (len 1 -> 0011)
        // 0011: 90             NOP (len 1 -> 0012)
        // 0012: B8 42 00       MOV AX, 42h (len 3 -> 0015)
        // 0015: C3             RET (len 1 -> 0016)
        writeCode(
                0xE8, 0x05, 0x00,       // 0000: CALL 0008
                0x8B, 0xD8,             // 0003: MOV BX, AX
                0xB8, 0x00, 0x4C,       // 0005: MOV AX, 4C00h
                0x55,                   // 0008: PUSH BP
                0x8B, 0xEC,             // 0009: MOV BP, SP
                0xE8, 0x04, 0x00,       // 000B: CALL 0012
                0x5D,                   // 000E: POP BP
                0xC3,                   // 000F: RET
                0x90,                   // 0010: NOP
                0x90,                   // 0011: NOP
                0xB8, 0x42, 0x00,       // 0012: MOV AX, 42h
                0xC3                    // 0015: RET
        );
        sourceMap.addMapping("MAIN.ASM", 1, 0x0000);
        sourceMap.addMapping("MAIN.ASM", 2, 0x0003);
        sourceMap.addMapping("MAIN.ASM", 3, 0x0005);
        sourceMap.addMapping("MAIN.ASM", 10, 0x0008);
        sourceMap.addMapping("MAIN.ASM", 11, 0x0009);
        sourceMap.addMapping("MAIN.ASM", 12, 0x000B);
        sourceMap.addMapping("MAIN.ASM", 13, 0x000E);
        sourceMap.addMapping("MAIN.ASM", 14, 0x000F);
        sourceMap.addMapping("MAIN.ASM", 20, 0x0012);
        sourceMap.addMapping("MAIN.ASM", 21, 0x0015);

        try (var session = createSession(List.of())) {
            // Initially paused at line 1 (0000)
            assertEquals(1, events.size());
            var p1 = (DebugEvent.Paused) events.getFirst();
            assertEquals(1, p1.line());

            // 1. Step into level1: should pause on line 10 (0008)
            int mark = events.size();
            session.stepInto();
            var p2 = awaitNextPause(mark, 10);
            assertEquals(0x0008, p2.registers().ip());
            assertEquals(1, cpu.getCallDepth());

            // Step through PUSH BP and MOV BP, SP
            mark = events.size();
            session.stepInto();
            awaitNextPause(mark, 11);

            mark = events.size();
            session.stepInto();
            awaitNextPause(mark, 12);
            assertEquals(0x000B, regs.ip);

            // 2. Step into leaf: should pause on line 20 (0012)
            mark = events.size();
            session.stepInto();
            var p3 = awaitNextPause(mark, 20);
            assertEquals(0x0012, p3.registers().ip());
            assertEquals(2, cpu.getCallDepth());

            // Verify callStack reports current frame at 0012 and caller frame chained via BP
            List<CallFrame> frames = session.callStack(5);
            assertFalse(frames.isEmpty());
            assertEquals(0, frames.getFirst().level());
            assertEquals("1000:0012", frames.getFirst().address());
            assertEquals(20, frames.getFirst().line());

            // Execute MOV AX, 42h inside leaf
            mark = events.size();
            session.stepInto();
            awaitNextPause(mark, 21);
            assertEquals(0x0042, regs.ax);

            // 3. Step out of leaf: returns to level1 at offset 000E (line 13)
            mark = events.size();
            session.stepOut();
            var p4 = awaitNextPause(mark, 13);
            assertEquals(0x000E, p4.registers().ip());
            assertEquals(1, cpu.getCallDepth());

            // 4. Step out of level1: returns to main at offset 0003 (line 2)
            mark = events.size();
            session.stepOut();
            var p5 = awaitNextPause(mark, 2);
            assertEquals(0x0003, p5.registers().ip());
            assertEquals(0, cpu.getCallDepth());
            assertEquals(0x0042, regs.ax);
        }
    }

    @Test
    void stepOverCallWithSegmentPrefix() throws Exception {
        // CS: CALL near rm16: 2E FF 16 0A 00 (CS: CALL [000Ah])
        // Target procedure at 0010h sets AX=99h and RET
        writeCode(
                0x2E, 0xFF, 0x16, 0x08, 0x00, // 0000: CS: CALL [0008h] (5 bytes)
                0x8B, 0xD8,                   // 0005: MOV BX, AX (len 2 -> 0007)
                0xF4,                         // 0007: HLT
                0x10, 0x00,                   // 0008: address vector pointing to 0010h
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00,
                0xB8, 0x99, 0x00,             // 0010: MOV AX, 99h
                0xC3                          // 0013: RET
        );
        sourceMap.addMapping("MAIN.ASM", 1, 0x0000);
        sourceMap.addMapping("MAIN.ASM", 2, 0x0005);
        sourceMap.addMapping("MAIN.ASM", 10, 0x0010);

        try (var session = createSession(List.of())) {
            // Step over prefixed CALL
            int mark = events.size();
            session.stepOver();
            var p = awaitNextPause(mark, 2);

            assertEquals(0x0005, p.registers().ip(), "Stepped over the 5-byte prefixed CALL instruction");
            assertEquals(0x0099, regs.ax, "Subroutine ran completely");
            assertEquals(0, cpu.getCallDepth());
        }
    }

    @Test
    void stepOverRecursiveRoutineDoesNotStopPrematurely() throws Exception {
        // countdown procedure:
        // 0000: B9 03 00       MOV CX, 3 (len 3 -> 0003)
        // 0003: E8 03 00       CALL 0009 (len 3 -> 0006, 0006 + 3 = 0009)
        // 0006: BB 01 00       MOV BX, 1 (len 3 -> 0009)
        // 0009: E3 06          JCXZ 0011 (len 2 -> 000B, 000B + 6 = 0011)
        // 000B: 49             DEC CX    (len 1 -> 000C)
        // 000C: E8 FA FF       CALL 0009 (len 3 -> 000F, 000F + (-6) = 0009)
        // 000F: 90             NOP       (len 1 -> 0010)
        // 0010: 90             NOP       (len 1 -> 0011)
        // 0011: C3             RET       (len 1 -> 0012)
        writeCode(
                0xB9, 0x03, 0x00,       // 0000: MOV CX, 3
                0xE8, 0x03, 0x00,       // 0003: CALL 0009
                0xBB, 0x01, 0x00,       // 0006: MOV BX, 1
                0xE3, 0x06,             // 0009: JCXZ 0011
                0x49,                   // 000B: DEC CX
                0xE8, 0xFA, 0xFF,       // 000C: CALL 0009
                0x90,                   // 000F: NOP
                0x90,                   // 0010: NOP
                0xC3                    // 0011: RET
        );
        sourceMap.addMapping("MAIN.ASM", 1, 0x0000);
        sourceMap.addMapping("MAIN.ASM", 2, 0x0003);
        sourceMap.addMapping("MAIN.ASM", 3, 0x0006);
        sourceMap.addMapping("MAIN.ASM", 5, 0x0009);

        try (var session = createSession(List.of())) {
            // Step into MOV CX, 3
            int mark = events.size();
            session.stepInto();
            awaitNextPause(mark, 2);
            assertEquals(3, regs.cx);
            assertEquals(0x0003, regs.ip);

            // Step OVER recursive call from main: must step over all recursions and stop at line 3 (0006)
            mark = events.size();
            session.stepOver();
            var paused = awaitNextPause(mark, 3);

            assertEquals(0x0006, paused.registers().ip(), "Returned to main without premature recursion pause");
            assertEquals(0, cpu.getCallDepth());
            assertEquals(0, regs.cx);
        }
    }

    @Test
    void callStackDepthLimitsAndCorruptOrCyclicBp() {
        sourceMap.addMapping("MAIN.ASM", 10, 0x0010);
        sourceMap.addMapping("MAIN.ASM", 20, 0x0020);
        sourceMap.addMapping("MAIN.ASM", 30, 0x0030);

        try (var session = createSession(List.of())) {
            // 1. Depth limit zero or negative returns empty list
            assertTrue(session.callStack(0).isEmpty());
            assertTrue(session.callStack(-3).isEmpty());

            // 2. Set up a valid 3-frame stack chain in SS
            regs.ip = 0x0010;
            regs.sp = 0xFFF0;
            regs.bp = 0xFFF4;

            // Frame 1: BP=FFF4h -> caller BP=FFFAh, retIp=0020h
            memory.write16(regs.ss, 0xFFF4, 0xFFFA);
            memory.write16(regs.ss, 0xFFF6, 0x0020);

            // Frame 2: BP=FFFAh -> caller BP=0000h (end of chain), retIp=0030h
            memory.write16(regs.ss, 0xFFFA, 0x0000);
            memory.write16(regs.ss, 0xFFFC, 0x0030);

            // Test depth = 1: only frame 0
            List<CallFrame> frames1 = session.callStack(1);
            assertEquals(1, frames1.size());
            assertEquals(0, frames1.get(0).level());
            assertEquals("1000:0010", frames1.get(0).address());
            assertEquals("??", frames1.get(0).function(), "Avoids fabricating symbol 'main' when symbol unknown");
            assertEquals(10, frames1.get(0).line());

            // Test depth = 2: frame 0 and frame 1
            List<CallFrame> frames2 = session.callStack(2);
            assertEquals(2, frames2.size());
            assertEquals(0, frames2.get(0).level());
            assertEquals(1, frames2.get(1).level());
            assertEquals("1000:0020", frames2.get(1).address());
            assertEquals(20, frames2.get(1).line());

            // Test depth = 5: all 3 frames
            List<CallFrame> framesAll = session.callStack(5);
            assertEquals(3, framesAll.size());
            assertEquals(0, framesAll.get(0).level());
            assertEquals(1, framesAll.get(1).level());
            assertEquals(2, framesAll.get(2).level());
            assertEquals("1000:0030", framesAll.get(2).address());
            assertEquals(30, framesAll.get(2).line());

            // 3. Cyclic BP protection: frame points to itself
            memory.write16(regs.ss, 0xFFF4, 0xFFF4);
            List<CallFrame> cyclicFrames = session.callStack(10);
            // Must terminate safely and return 2 frames (frame 0 + frame 1) without infinite loop
            assertEquals(2, cyclicFrames.size());

            // 4. Invalid / corrupt BP checks:
            // a) bp < sp (corrupted stack pointer where bp is below sp)
            regs.bp = 0xFFEE;
            regs.sp = 0xFFF0;
            List<CallFrame> belowSpFrames = session.callStack(5);
            assertEquals(1, belowSpFrames.size(), "Stops immediately if bp < sp");

            // b) bp >= 0xFFFE
            regs.bp = 0xFFFE;
            regs.sp = 0xFFE0;
            List<CallFrame> oobFrames = session.callStack(5);
            assertEquals(1, oobFrames.size(), "Stops immediately if bp >= 0xFFFE");

            // c) bp == 0
            regs.bp = 0x0000;
            List<CallFrame> zeroBpFrames = session.callStack(5);
            assertEquals(1, zeroBpFrames.size(), "Stops immediately if bp == 0");
        }
    }

    // =========================================================================
    // 3. Conditional Branches and Loops
    // =========================================================================

    @Test
    void conditionalBranchesAndLoopInstructions() throws Exception {
        // 0000: MOV AX, 10     (B8 0A 00)
        // 0003: CMP AX, 10     (3D 0A 00)
        // 0006: JZ 000B        (74 03) -> taken
        // 0008: MOV BX, 0BADh  (BB AD 0B) -> skipped
        // 000B: MOV BX, 1      (BB 01 00)
        // 000E: MOV CX, 3      (B9 03 00)
        // 0011: ADD DX, 2      (83 C2 02)
        // 0014: LOOP 0011      (E2 FB)
        // 0016: HLT            (F4)
        writeCode(
                0xB8, 0x0A, 0x00,
                0x3D, 0x0A, 0x00,
                0x74, 0x03,
                0xBB, 0xAD, 0x0B,
                0xBB, 0x01, 0x00,
                0xB9, 0x03, 0x00,
                0x83, 0xC2, 0x02,
                0xE2, 0xFB,
                0xF4
        );
        sourceMap.addMapping("MAIN.ASM", 1, 0x0000);
        sourceMap.addMapping("MAIN.ASM", 2, 0x0003);
        sourceMap.addMapping("MAIN.ASM", 3, 0x0006);
        sourceMap.addMapping("MAIN.ASM", 4, 0x0008);
        sourceMap.addMapping("MAIN.ASM", 5, 0x000B);
        sourceMap.addMapping("MAIN.ASM", 6, 0x000E);
        sourceMap.addMapping("MAIN.ASM", 7, 0x0011);
        sourceMap.addMapping("MAIN.ASM", 8, 0x0016);

        try (var session = createSession(List.of())) {
            // Step through branch evaluation
            int mark = events.size();
            session.stepInto(); // MOV AX, 10
            awaitNextPause(mark, 2);

            mark = events.size();
            session.stepInto(); // CMP AX, 10
            awaitNextPause(mark, 3);
            assertTrue(regs.isZf());

            mark = events.size();
            session.stepInto(); // JZ 000B (taken)
            var pBranch = awaitNextPause(mark, 5);
            assertEquals(0x000B, pBranch.registers().ip(), "Jump was taken directly to line 5");
            assertNotEquals(0x0BAD, regs.bx);

            // Resume to breakpoint at line 8 (after loop)
            mark = events.size();
            session.setBreakpoints(List.of(new Breakpoint("MAIN.ASM", 8, true)));
            session.resume();
            var pEnd = awaitNextPause(mark, 8);

            assertEquals(0x0016, pEnd.registers().ip());
            assertEquals(0, regs.cx, "LOOP decremented CX to 0");
            assertEquals(6, regs.dx, "Accumulated 3 iterations * 2 = 6");
        }
    }

    // =========================================================================
    // 4. Repeated Breakpoint Hits (Continue Must Not Immediately Re-Trap)
    // =========================================================================

    @Test
    void repeatedBreakpointInLoopAdvancesAndTrapsNextIteration() throws Exception {
        // 0000: MOV DX, 0      (BA 00 00)
        // 0003: MOV CX, 3      (B9 03 00)
        // 0006: ADD DX, 10     (83 C2 0A) <- Breakpoint here (line 3)
        // 0009: LOOP 0006      (E2 FB)
        // 000B: HLT            (F4)
        writeCode(
                0xBA, 0x00, 0x00,
                0xB9, 0x03, 0x00,
                0x83, 0xC2, 0x0A,
                0xE2, 0xFB,
                0xF4
        );
        sourceMap.addMapping("MAIN.ASM", 1, 0x0000);
        sourceMap.addMapping("MAIN.ASM", 2, 0x0003);
        sourceMap.addMapping("MAIN.ASM", 3, 0x0006);
        sourceMap.addMapping("MAIN.ASM", 4, 0x000B);

        var bp = new Breakpoint("MAIN.ASM", 3, true);
        try (var session = createSession(List.of(bp))) {
            // Resume to 1st iteration stop
            int mark = events.size();
            session.resume();
            var hit1 = awaitNextPause(mark, 3);
            assertEquals(0x0006, hit1.registers().ip());
            assertEquals(3, regs.cx);
            assertEquals(0, regs.dx, "First iteration has not executed ADD DX, 10 yet");

            // Resume from breakpoint: MUST NOT immediately re-trap on same instruction!
            // Must execute ADD DX, 10, execute LOOP, and stop on 2nd iteration.
            mark = events.size();
            session.resume();
            var hit2 = awaitNextPause(mark, 3);
            assertEquals(0x0006, hit2.registers().ip());
            assertEquals(2, regs.cx, "Advanced to iteration 2");
            assertEquals(10, regs.dx, "Iteration 1 added 10");

            // Resume to 3rd iteration
            mark = events.size();
            session.resume();
            var hit3 = awaitNextPause(mark, 3);
            assertEquals(0x0006, hit3.registers().ip());
            assertEquals(1, regs.cx, "Advanced to iteration 3");
            assertEquals(20, regs.dx, "Iteration 2 added 10");

            // Resume: 3rd iteration executes, CX becomes 0, loop terminates, program ends
            session.resume();
            ExitInfo exit = session.exit().get(3, TimeUnit.SECONDS);
            assertNotNull(exit);
            assertEquals(30, regs.dx, "All 3 iterations executed completely");
        }
    }

    // =========================================================================
    // 5. Pause and Stop of Infinite Loops
    // =========================================================================

    @Test
    void pauseAndStopInfiniteLoopUnderBoundedTimeout() throws Exception {
        // Infinite register increment loop:
        // 0000: INC AX (40)
        // 0001: JMP 0000 (EB FD)
        writeCode(0x40, 0xEB, 0xFD);
        sourceMap.addMapping("MAIN.ASM", 1, 0x0000);
        sourceMap.addMapping("MAIN.ASM", 2, 0x0001);

        try (var session = createSession(List.of())) {
            session.resume();
            Thread.sleep(50);

            // 1. Pause running loop
            long start = System.nanoTime();
            int mark = events.size();
            session.pause();
            var paused = awaitEvent(mark, DebugEvent.Paused.class, p -> p.line() == 1 || p.line() == 2);
            long pauseMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertNotNull(paused);
            assertTrue(pauseMillis < 1000, "Pause took " + pauseMillis + " ms");
            assertEquals(SessionState.RUNNING, session.state(), "State is paused, not terminated");
            assertTrue(session.instructionsExecuted() > 100, "Loop was actively running");

            // 2. Resume again
            session.resume();
            Thread.sleep(50);

            // 3. Stop running loop
            long stopStart = System.nanoTime();
            session.stop();
            ExitInfo exit = session.exit().get(1, TimeUnit.SECONDS);
            long stopMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStart);

            assertTrue(exit.wasStopped());
            assertEquals(SessionState.STOPPED, session.state());
            assertTrue(stopMillis < 1000, "Stop took " + stopMillis + " ms");
        }
    }

    // =========================================================================
    // 6. Restart Through Fresh Session Construction
    // =========================================================================

    @Test
    void restartThroughFreshSessionConstructionResetsState() throws Exception {
        // 0000: MOV AX, 1234h (B8 34 12)
        // 0003: MOV BX, 5678h (BB 78 56)
        // 0006: HLT           (F4)
        writeCode(0xB8, 0x34, 0x12, 0xBB, 0x78, 0x56, 0xF4);
        sourceMap.addMapping("MAIN.ASM", 1, 0x0000);
        sourceMap.addMapping("MAIN.ASM", 2, 0x0003);
        sourceMap.addMapping("MAIN.ASM", 3, 0x0006);

        var bp = new Breakpoint("MAIN.ASM", 2, true);

        // --- Run Session 1 ---
        try (var session1 = createSession(List.of(bp))) {
            int mark = events.size();
            session1.resume();
            awaitNextPause(mark, 2);
            assertEquals(0x1234, regs.ax);
            assertEquals(0x0003, regs.ip);
            session1.stop();
            assertTrue(session1.exit().get(1, TimeUnit.SECONDS).wasStopped());
        }

        // --- Construct Fresh Session 2 (simulating IDE Restart) ---
        var freshRegs = new CpuRegisters();
        freshRegs.cs = 0x1000;
        freshRegs.ip = 0x0000;
        freshRegs.ds = 0x2000;
        freshRegs.es = 0x2000;
        freshRegs.ss = 0x3000;
        freshRegs.sp = 0xFFFE;

        var freshMemory = new RealModeMemory();
        // Reload program bytes into fresh memory
        for (int i = 0; i < 7; i++) {
            freshMemory.write8(0x1000, i, memory.read8(0x1000, i));
        }

        var freshCpu = new Cpu8086(freshRegs, freshMemory);
        var freshDos = new DosInterruptHandler();
        freshCpu.setInterruptHandler(freshDos);

        var freshEvents = new CopyOnWriteArrayList<DebugEvent>();
        try (var session2 = new Emu8086DebugSession(freshCpu, freshMemory, createProgram(),
                sourceMap, List.of(bp), freshDos, freshEvents::add)) {

            // Session 2 initial state: registers zeroed, entry point paused at line 1
            assertEquals(1, freshEvents.size());
            var initPause = (DebugEvent.Paused) freshEvents.getFirst();
            assertEquals(1, initPause.line());
            assertEquals(0x0000, freshRegs.ax, "AX reset to 0 in fresh session");
            assertEquals(0x0000, freshRegs.bx);
            assertEquals(0, session2.instructionsExecuted());

            // Run session 2 to breakpoint
            session2.resume();
            Thread.sleep(100);

            var p = (DebugEvent.Paused) freshEvents.getLast();
            assertEquals(2, p.line());
            assertEquals(0x1234, freshRegs.ax);
            assertEquals(0x0003, freshRegs.ip);
        }
    }

    // =========================================================================
    // 7. Keyboard & Buffered Input (Backspace, Pasting, Stop While Blocked)
    // =========================================================================

    @Test
    void bufferedInputWithBackspaceAndLimitProtection() throws Exception {
        // Buffer at DS:0100h: maxLen = 6 (meaning maxChars = 5)
        // MOV AH, 0Ah; MOV DX, 0100h; INT 21h; MOV AX, 4C00h; INT 21h
        memory.write8(0x2000, 0x0100, 6);
        writeCode(0xB4, 0x0A, 0xBA, 0x00, 0x01, 0xCD, 0x21, 0xB8, 0x00, 0x4C, 0xCD, 0x21);

        try (var session = createSession(List.of())) {
            int mark = events.size();
            session.resume();
            awaitEvent(mark, DebugEvent.WaitingForInput.class, w -> true);

            // Typed sequence: 'T', 'e', 'x', '\b', 's', 't', '\r'
            // 'x' followed by backspace must erase 'x', yielding "Test"
            session.sendInput("Tex\bst\r");
            session.exit().get(3, TimeUnit.SECONDS);
        }

        int count = memory.read8(0x2000, 0x0101);
        assertEquals(4, count, "Count after backspace editing should be 4");
        assertEquals('T', memory.read8(0x2000, 0x0102));
        assertEquals('e', memory.read8(0x2000, 0x0103));
        assertEquals('s', memory.read8(0x2000, 0x0104));
        assertEquals('t', memory.read8(0x2000, 0x0105));
        assertEquals('\r', memory.read8(0x2000, 0x0106), "Terminating carriage return");
    }

    @Test
    void bufferedInputExcessCharactersDiscardedWithoutLeakingEnter() throws Exception {
        // Buffer at DS:0100h: maxLen = 4 (meaning maxChars = 3)
        // Pasting "ABCDEF\r" must store "ABC", discard "DEF", and consume '\r'
        memory.write8(0x2000, 0x0100, 4);
        // First read: AH=0Ah, Second read: AH=01h (read char), HLT
        writeCode(
                0xB4, 0x0A, 0xBA, 0x00, 0x01, 0xCD, 0x21, // INT 21h AH=0Ah
                0xB4, 0x01, 0xCD, 0x21,                   // INT 21h AH=01h
                0xF4                                      // HLT
        );

        try (var session = createSession(List.of())) {
            int mark = events.size();
            session.resume();
            awaitEvent(mark, DebugEvent.WaitingForInput.class, w -> true);

            // Provide excess string for first read
            session.sendInput("ABCDEF\r");
            // Provide 'Z' for the second read
            session.sendInput("Z");

            session.exit().get(3, TimeUnit.SECONDS);
        }

        assertEquals(3, memory.read8(0x2000, 0x0101), "Capped at maxChars=3");
        assertEquals('A', memory.read8(0x2000, 0x0102));
        assertEquals('B', memory.read8(0x2000, 0x0103));
        assertEquals('C', memory.read8(0x2000, 0x0104));
        assertEquals('Z', regs.getAl(), "Second read correctly obtained 'Z' without leaking leftover Enter");
    }

    @Test
    void bufferedInputZeroAndOneMaxLenContracts() throws Exception {
        // Ralf Brown's Interrupt List INT 21h AH=0Ah Contract:
        // 1. maxLen == 0: returns immediately without waiting for input or modifying memory.
        memory.write8(0x2000, 0x0100, 0); // maxLen = 0
        memory.write8(0x2000, 0x0101, 0x33); // sentinel count
        memory.write8(0x2000, 0x0102, 0x44); // sentinel buffer

        // Code: MOV AH, 0Ah; MOV DX, 0100h; INT 21h; MOV AX, 1111h; HLT
        writeCode(
                0xB4, 0x0A,
                0xBA, 0x00, 0x01,
                0xCD, 0x21,
                0xB8, 0x11, 0x11,
                0xF4
        );

        try (var session = createSession(List.of())) {
            // Must run to HLT without blocking or waiting for user input
            session.resume();
            session.exit().get(2, TimeUnit.SECONDS);
            assertEquals(0x1111, regs.ax, "Program reached instruction after INT 21h");
            assertEquals(0, memory.read8(0x2000, 0x0100), "maxLen unchanged");
            assertEquals(0x33, memory.read8(0x2000, 0x0101), "Count byte untouched on maxLen=0");
            assertEquals(0x44, memory.read8(0x2000, 0x0102), "Buffer byte untouched on maxLen=0");
        }

        // 2. maxLen == 1: maxChars = 0 (only Enter accepted; count=0, CR stored at offset 2)
        setUp(); // re-initialize cpu/registers/memory
        memory.write8(0x2000, 0x0200, 1); // maxLen = 1
        memory.write8(0x2000, 0x0201, 0x99); // sentinel
        memory.write8(0x2000, 0x0202, 0x99); // sentinel
        memory.write8(0x2000, 0x0203, 0x55); // sentinel outside buffer

        // Code: MOV AH, 0Ah; MOV DX, 0200h; INT 21h; MOV AX, 2222h; HLT
        writeCode(
                0xB4, 0x0A,
                0xBA, 0x00, 0x02,
                0xCD, 0x21,
                0xB8, 0x22, 0x22,
                0xF4
        );

        try (var session = createSession(List.of())) {
            int mark = events.size();
            session.resume();
            awaitEvent(mark, DebugEvent.WaitingForInput.class, w -> true);

            // Send characters which should be discarded since maxChars=0, then Enter
            session.sendInput("ignored\r");
            session.exit().get(2, TimeUnit.SECONDS);

            assertEquals(0x2222, regs.ax);
            assertEquals(1, memory.read8(0x2000, 0x0200));
            assertEquals(0, memory.read8(0x2000, 0x0201), "Actual length must be 0 for maxLen=1");
            assertEquals('\r', (char) memory.read8(0x2000, 0x0202), "Carriage return written at offset 2");
            assertEquals(0x55, memory.read8(0x2000, 0x0203), "Byte beyond buffer untouched");
        }
    }

    // =========================================================================
    // 8. Actionable Fault Diagnostics
    // =========================================================================

    @Test
    void invalidOpcodeModRmSurfacesProblemDiagnostic() throws Exception {
        // Opcode FFh with sub-function 7 (invalid on 8086)
        // FF 38 (mod=0, reg=7, rm=0)
        writeCode(0xFF, 0x38);
        sourceMap.addMapping("MAIN.ASM", 12, 0x0000);

        try (var session = createSession(List.of())) {
            int mark = events.size();
            session.resume();

            var problem = awaitEvent(mark, DebugEvent.Problem.class, p -> "emu.invalid-opcode".equals(p.diagnostic().code()));
            assertEquals(Severity.ERROR, problem.diagnostic().severity());
            assertNotNull(problem.diagnostic().location());
            assertEquals(12, problem.diagnostic().location().line());
            assertEquals(0x0000, regs.ip, "Points directly at invalid instruction");

            // Resuming past fault exits with FAULT_EXIT_CODE
            session.resume();
            ExitInfo exit = session.exit().get(2, TimeUnit.SECONDS);
            assertEquals(255, exit.exitCode());
        }
    }

    @Test
    void softwareBreakpointInt3PausesAndResumesSmoothly() throws Exception {
        // 0000: MOV AX, 1111h (B8 11 11)
        // 0003: INT 3          (CC)
        // 0004: MOV BX, 2222h (BB 22 22)
        // 0007: HLT           (F4)
        writeCode(0xB8, 0x11, 0x11, 0xCC, 0xBB, 0x22, 0x22, 0xF4);
        sourceMap.addMapping("MAIN.ASM", 1, 0x0000);
        sourceMap.addMapping("MAIN.ASM", 2, 0x0003);
        sourceMap.addMapping("MAIN.ASM", 3, 0x0004);

        try (var session = createSession(List.of())) {
            int mark = events.size();
            session.resume();

            // INT 3 pauses debugger at the instruction following INT 3 (offset 0004, line 3)
            var paused = awaitNextPause(mark, 3);
            assertEquals(0x0004, paused.registers().ip());
            assertEquals(0x1111, regs.ax);
            assertEquals(0x0000, regs.bx);

            // Resuming continues execution normally
            session.resume();
            session.exit().get(2, TimeUnit.SECONDS);
            assertEquals(0x2222, regs.bx);
        }
    }
}
