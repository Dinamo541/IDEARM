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
        assertEquals("", lastOutput.get(), "output reaches the listener in chunks, on flush");
        handler.flushOutput();
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
        handler.flushOutput();
        assertEquals("Hello World!", lastOutput.get());
    }

    /** A program printing in an endless loop sent one event per character and kept every character forever. */
    @Test
    void endlessOutputIsChunkedAndTheCaptureIsBounded() {
        var chunks = new java.util.ArrayList<String>();
        handler.setOutputListener(chunks::add);

        int printed = 10 * DosInterruptHandler.CAPTURED_OUTPUT_LIMIT;
        for (int i = 0; i < printed; i++) {
            regs.setAh(0x02);
            regs.setDl('A' + i % 26);
            cpu.triggerInterrupt(0x21);
        }
        handler.flushOutput();

        assertTrue(handler.capturedOutput().length() <= 2 * DosInterruptHandler.CAPTURED_OUTPUT_LIMIT);
        assertTrue(chunks.size() <= printed / DosInterruptHandler.OUTPUT_CHUNK + 1, "chunks: " + chunks.size());
        assertEquals(printed, chunks.stream().mapToInt(String::length).sum(), "no character is lost");
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

    @Test
    void testInt21hWriteHandleStdoutAndStderr() {
        byte[] msg1 = "Output to stdout".getBytes();
        for (int i = 0; i < msg1.length; i++) {
            memory.write8(0x2000, 0x0100 + i, msg1[i]);
        }

        // AH = 40h, BX = 1 (stdout)
        regs.setAh(0x40);
        regs.bx = 1;
        regs.cx = msg1.length;
        regs.dx = 0x0100;
        cpu.triggerInterrupt(0x21);

        assertFalse(regs.isCf(), "CF should be cleared on success");
        assertEquals(msg1.length, regs.ax, "AX should return bytes written");
        assertEquals("Output to stdout", handler.capturedOutput());

        byte[] msg2 = " [err]".getBytes();
        for (int i = 0; i < msg2.length; i++) {
            memory.write8(0x2000, 0x0200 + i, msg2[i]);
        }

        // AH = 40h, BX = 2 (stderr)
        regs.setAh(0x40);
        regs.bx = 2;
        regs.cx = msg2.length;
        regs.dx = 0x0200;
        cpu.triggerInterrupt(0x21);

        assertFalse(regs.isCf());
        assertEquals(msg2.length, regs.ax);
        assertEquals("Output to stdout [err]", handler.capturedOutput());

        // AH = 40h with invalid handle BX = 3
        regs.setAh(0x40);
        regs.bx = 3;
        regs.cx = 5;
        cpu.triggerInterrupt(0x21);

        assertTrue(regs.isCf(), "CF should be set for unsupported handle");
        assertEquals(6, regs.ax, "AX should return invalid handle error code 6");
    }

    @Test
    void testInt21hReadHandleStdin() {
        handler.provideInput("INPUT_DATA\r");

        // AH = 3Fh, BX = 0 (stdin), CX = 20
        regs.setAh(0x3F);
        regs.bx = 0;
        regs.cx = 20;
        regs.dx = 0x0300;
        cpu.triggerInterrupt(0x21);

        assertFalse(regs.isCf());
        int readCount = regs.ax;
        assertTrue(readCount > 0, "AX should return positive count of read bytes");

        // Verify buffer content in memory
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < readCount; i++) {
            sb.append((char) memory.read8(0x2000, 0x0300 + i));
        }
        assertEquals("INPUT_DATA\r\n", sb.toString());

        // AH = 3Fh with invalid handle BX = 4
        regs.setAh(0x3F);
        regs.bx = 4;
        regs.cx = 10;
        cpu.triggerInterrupt(0x21);

        assertTrue(regs.isCf());
        assertEquals(6, regs.ax);
    }

    @Test
    void testInt21hGetSystemDate() {
        regs.setAh(0x2A);
        cpu.triggerInterrupt(0x21);

        assertTrue(regs.cx >= 2026, "Year in CX should be >= 2026");
        assertTrue(regs.getDh() >= 1 && regs.getDh() <= 12, "Month in DH should be 1..12");
        assertTrue(regs.getDl() >= 1 && regs.getDl() <= 31, "Day in DL should be 1..31");
        assertTrue(regs.getAl() >= 0 && regs.getAl() <= 6, "Day of week in AL should be 0..6 (Sunday=0)");
    }

    @Test
    void testInt21hGetSystemTime() {
        regs.setAh(0x2C);
        cpu.triggerInterrupt(0x21);

        assertTrue(regs.getCh() >= 0 && regs.getCh() <= 23, "Hours in CH should be 0..23");
        assertTrue(regs.getCl() >= 0 && regs.getCl() <= 59, "Minutes in CL should be 0..59");
        assertTrue(regs.getDh() >= 0 && regs.getDh() <= 59, "Seconds in DH should be 0..59");
        assertTrue(regs.getDl() >= 0 && regs.getDl() <= 99, "Hundredths in DL should be 0..99");
    }

    @Test
    void testInt10hCursorPositionTracking() {
        // Set cursor position on page 0: row 15, column 40
        regs.setAh(0x02);
        regs.setBh(0);
        regs.setDh(15);
        regs.setDl(40);
        cpu.triggerInterrupt(0x10);

        assertEquals(15, handler.getCursorRow(0));
        assertEquals(40, handler.getCursorCol(0));

        // Get cursor position on page 0
        regs.setAh(0x03);
        regs.setBh(0);
        regs.setDh(0);
        regs.setDl(0);
        cpu.triggerInterrupt(0x10);

        assertEquals(15, regs.getDh());
        assertEquals(40, regs.getDl());
        assertEquals(6, regs.getCh());
        assertEquals(7, regs.getCl());

        // Page 1 is tracked independently
        regs.setAh(0x02);
        regs.setBh(1);
        regs.setDh(5);
        regs.setDl(10);
        cpu.triggerInterrupt(0x10);

        assertEquals(5, handler.getCursorRow(1));
        assertEquals(10, handler.getCursorCol(1));
        assertEquals(15, handler.getCursorRow(0), "Page 0 remains untouched");
    }

    @Test
    void testInt10hWriteCharacterRepeat() {
        // AH = 09h: write '*' 5 times
        regs.setAh(0x09);
        regs.setAl('*');
        regs.setBh(0);
        regs.setBl(0x0F);
        regs.cx = 5;
        cpu.triggerInterrupt(0x10);

        assertEquals("*****", handler.capturedOutput());

        // AH = 0Ah: write '#' 3 times
        regs.setAh(0x0A);
        regs.setAl('#');
        regs.setBh(0);
        regs.cx = 3;
        cpu.triggerInterrupt(0x10);

        assertEquals("*****###", handler.capturedOutput());
    }

    @Test
    void testInt10hWriteString() {
        byte[] text = "HELLO DOS".getBytes();
        for (int i = 0; i < text.length; i++) {
            memory.write8(0x2000, 0x0500 + i, text[i]);
        }

        // Mode 0: characters only, cursor not updated
        regs.setAh(0x13);
        regs.setAl(0x00);
        regs.setBh(0);
        regs.setBl(0x07);
        regs.cx = text.length;
        regs.setDh(2);
        regs.setDl(5);
        regs.es = 0x2000;
        regs.bp = 0x0500;
        cpu.triggerInterrupt(0x10);

        assertEquals("HELLO DOS", handler.capturedOutput());
        assertEquals(2, handler.getCursorRow(0));
        assertEquals(5, handler.getCursorCol(0), "Mode 0 should not update cursor position");

        // Mode 1: characters only, cursor updated
        regs.setAh(0x13);
        regs.setAl(0x01);
        regs.setBh(0);
        regs.setBl(0x07);
        regs.cx = 4;
        regs.setDh(3);
        regs.setDl(10);
        regs.es = 0x2000;
        regs.bp = 0x0500; // "HELL"
        cpu.triggerInterrupt(0x10);

        assertEquals("HELLO DOSHELL", handler.capturedOutput());
        assertEquals(3, handler.getCursorRow(0));
        assertEquals(14, handler.getCursorCol(0), "Mode 1 should advance cursor column by length");

        // Mode 2: interleaved characters and attributes
        byte[] interleaved = {'A', 0x07, 'B', 0x04, 'C', 0x02};
        for (int i = 0; i < interleaved.length; i++) {
            memory.write8(0x2000, 0x0600 + i, interleaved[i]);
        }
        regs.setAh(0x13);
        regs.setAl(0x02);
        regs.setBh(0);
        regs.cx = 3;
        regs.setDh(10);
        regs.setDl(20);
        regs.es = 0x2000;
        regs.bp = 0x0600;
        cpu.triggerInterrupt(0x10);

        assertEquals("HELLO DOSHELLABC", handler.capturedOutput());
    }

    @Test
    void testInt1AhSystemTimerTicks() {
        regs.setAh(0x00);
        cpu.triggerInterrupt(0x1A);

        int ticks = ((regs.cx & 0xFFFF) << 16) | (regs.dx & 0xFFFF);
        assertTrue(ticks >= 0, "Timer ticks should be non-negative");
        assertEquals(0, regs.getAl(), "Midnight rollover flag in AL should be 0");
    }

    @Test
    void testInt16hScanCodesForSpecialAndNormalKeys() {
        // Test Enter (scan code 0x1C, ASCII 0x0D)
        handler.provideKey(DosInterruptHandler.KeyStroke.ENTER);
        // Peek with AH=01h
        regs.setAh(0x01);
        cpu.triggerInterrupt(0x16);
        assertFalse(regs.isZf(), "ZF should be 0 when keystroke is available");
        assertEquals(0x1C, regs.getAh(), "Enter scan code should be 0x1C");
        assertEquals('\r', (char) regs.getAl());

        // Read with AH=00h
        regs.setAh(0x00);
        cpu.triggerInterrupt(0x16);
        assertEquals(0x1C, regs.getAh());
        assertEquals('\r', (char) regs.getAl());

        // Test Escape (scan code 0x01, ASCII 0x1B)
        handler.provideKey(DosInterruptHandler.KeyStroke.ESCAPE);
        regs.setAh(0x00);
        cpu.triggerInterrupt(0x16);
        assertEquals(0x01, regs.getAh());
        assertEquals(0x1B, regs.getAl());

        // Test Arrow Keys: Up (0x48), Down (0x50), Left (0x4B), Right (0x4D)
        handler.provideKey(DosInterruptHandler.KeyStroke.UP);
        regs.setAh(0x00);
        cpu.triggerInterrupt(0x16);
        assertEquals(0x48, regs.getAh(), "Up arrow scan code");
        assertEquals(0x00, regs.getAl(), "Extended key ASCII code should be 0");

        handler.provideKey(DosInterruptHandler.KeyStroke.DOWN);
        regs.setAh(0x00);
        cpu.triggerInterrupt(0x16);
        assertEquals(0x50, regs.getAh(), "Down arrow scan code");
        assertEquals(0x00, regs.getAl());

        handler.provideKey(DosInterruptHandler.KeyStroke.LEFT);
        regs.setAh(0x00);
        cpu.triggerInterrupt(0x16);
        assertEquals(0x4B, regs.getAh(), "Left arrow scan code");
        assertEquals(0x00, regs.getAl());

        handler.provideKey(DosInterruptHandler.KeyStroke.RIGHT);
        regs.setAh(0x00);
        cpu.triggerInterrupt(0x16);
        assertEquals(0x4D, regs.getAh(), "Right arrow scan code");
        assertEquals(0x00, regs.getAl());

        // Test ANSI escape sequence for arrow key "\033[A" -> UP
        handler.provideInput("\033[A");
        regs.setAh(0x00);
        cpu.triggerInterrupt(0x16);
        assertEquals(0x48, regs.getAh());
        assertEquals(0x00, regs.getAl());

        // Test Unicode arrow char '↑' -> UP
        handler.provideInput("↑");
        regs.setAh(0x00);
        cpu.triggerInterrupt(0x16);
        assertEquals(0x48, regs.getAh());
        assertEquals(0x00, regs.getAl());

        // Test regular alphanumeric char 'A' (scan code 0x1E)
        handler.provideInput("A");
        regs.setAh(0x00);
        cpu.triggerInterrupt(0x16);
        assertEquals(0x1E, regs.getAh(), "'A' scan code should be 0x1E");
        assertEquals('A', (char) regs.getAl());
    }

    @Test
    void testInt21hBufferedInputZeroAndOneMaxLen() {
        // Ralf Brown's Interrupt List INT 21h AH=0Ah contract:
        // 1. maxLen == 0: returns immediately without blocking, memory unmodified.
        memory.write8(0x2000, 0x0100, 0); // maxLen = 0
        memory.write8(0x2000, 0x0101, 0x55); // sentinel actualCount
        memory.write8(0x2000, 0x0102, 0xAA); // sentinel buffer
        regs.setAh(0x0A);
        regs.dx = 0x0100;
        cpu.triggerInterrupt(0x21);

        assertEquals(0, memory.read8(0x2000, 0x0100));
        assertEquals(0x55, memory.read8(0x2000, 0x0101), "Byte 1 unmodified when maxLen=0");
        assertEquals(0xAA, memory.read8(0x2000, 0x0102), "Byte 2 unmodified when maxLen=0");

        // 2. maxLen == 1: maxChars = 0, ignores typed chars, terminates on Enter with count=0 and CR at off+2.
        memory.write8(0x2000, 0x0200, 1); // maxLen = 1
        memory.write8(0x2000, 0x0201, 0xFF);
        memory.write8(0x2000, 0x0202, 0xFF);
        memory.write8(0x2000, 0x0203, 0x77); // sentinel byte beyond buffer

        handler.provideInput("hello\r");
        regs.setAh(0x0A);
        regs.dx = 0x0200;
        cpu.triggerInterrupt(0x21);

        assertEquals(1, memory.read8(0x2000, 0x0200));
        assertEquals(0, memory.read8(0x2000, 0x0201), "Count should be 0 since maxChars=0");
        assertEquals('\r', (char) memory.read8(0x2000, 0x0202), "CR written at off+2");
        assertEquals(0x77, memory.read8(0x2000, 0x0203), "Byte beyond buffer untouched");
    }
}
