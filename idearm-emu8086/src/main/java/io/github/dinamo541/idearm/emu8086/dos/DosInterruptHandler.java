package io.github.dinamo541.idearm.emu8086.dos;

import io.github.dinamo541.idearm.emu8086.cpu.Cpu8086;
import io.github.dinamo541.idearm.emu8086.cpu.CpuRegisters;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * High-level MS-DOS and PC-BIOS interrupt services emulation (INT 21h, INT 10h, INT 16h, INT 20h).
 */
public final class DosInterruptHandler implements Cpu8086.InterruptHandler {

    /** The key code of Enter (carriage return). */
    private static final char ENTER = 13;

    /** Receives the services a program used that the emulator does not perform. */
    @FunctionalInterface
    public interface ProblemListener {
        void report(String code, String message, List<String> arguments);
    }

    /** How much of the printed text {@link #capturedOutput()} keeps: a program printing forever must not fill the heap. */
    public static final int CAPTURED_OUTPUT_LIMIT = 64 * 1024;
    /** Printed characters are handed to the listener in chunks of at most this size, not one call per character. */
    public static final int OUTPUT_CHUNK = 4096;

    /** Key stroke model encapsulating standard PC keyboard scan code and ASCII code. */
    public record KeyStroke(int scanCode, char ascii) {
        public static final KeyStroke ENTER = new KeyStroke(0x1C, '\r');
        public static final KeyStroke ESCAPE = new KeyStroke(0x01, (char) 0x1B);
        public static final KeyStroke BACKSPACE = new KeyStroke(0x0E, '\b');
        public static final KeyStroke TAB = new KeyStroke(0x0F, '\t');
        public static final KeyStroke UP = new KeyStroke(0x48, (char) 0);
        public static final KeyStroke DOWN = new KeyStroke(0x50, (char) 0);
        public static final KeyStroke LEFT = new KeyStroke(0x4B, (char) 0);
        public static final KeyStroke RIGHT = new KeyStroke(0x4D, (char) 0);
    }

    private final StringBuilder outputBuffer = new StringBuilder();
    /** Printed and not yet given to the listener; only the emulator thread touches it. */
    private final StringBuilder pendingOutput = new StringBuilder();
    /** Keys typed and not read yet: the IDE thread adds them, the emulator thread takes them. */
    private final java.util.concurrent.BlockingQueue<KeyStroke> inputQueue = new java.util.concurrent.LinkedBlockingQueue<>();
    /** Text-mode cursor position per video page (DH=row 0..24, DL=column 0..79). */
    private final int[] cursorRow = new int[8];
    private final int[] cursorCol = new int[8];
    /** Called when the program waits for a key; {@code null} means reads never wait (tests, batch runs). */
    private volatile Runnable waitingForInput;
    private final Set<Integer> reportedFunctions = new HashSet<>();
    private Consumer<String> outputListener = text -> {};
    private ProblemListener problemListener = (code, message, arguments) -> {};

    public DosInterruptHandler() {}

    public DosInterruptHandler(Consumer<String> outputListener) {
        if (outputListener != null) {
            this.outputListener = outputListener;
        }
    }

    public void setOutputListener(Consumer<String> outputListener) {
        this.outputListener = (outputListener != null) ? outputListener : text -> {};
    }

    public void setProblemListener(ProblemListener listener) {
        this.problemListener = (listener != null) ? listener : (code, message, arguments) -> {};
    }

    /**
     * Makes keyboard reads wait for keys, as DOS does, instead of answering Enter at once. {@code onWaiting} runs on
     * the emulator thread each time the program waits with nothing typed, so the IDE can ask the user to type.
     */
    public void enableInteractiveInput(Runnable onWaiting) {
        this.waitingForInput = onWaiting;
    }

    public void provideInput(String input) {
        if (input != null) {
            for (int i = 0; i < input.length(); i++) {
                if (input.charAt(i) == '\033' && i + 2 < input.length() && input.charAt(i + 1) == '[') {
                    char code = input.charAt(i + 2);
                    KeyStroke arrow = switch (code) {
                        case 'A' -> KeyStroke.UP;
                        case 'B' -> KeyStroke.DOWN;
                        case 'C' -> KeyStroke.RIGHT;
                        case 'D' -> KeyStroke.LEFT;
                        default -> null;
                    };
                    if (arrow != null) {
                        inputQueue.offer(arrow);
                        i += 2;
                        continue;
                    }
                }
                char c = input.charAt(i);
                inputQueue.offer(keyStrokeFor(c));
            }
        }
    }

    public void provideKey(KeyStroke key) {
        if (key != null) {
            inputQueue.offer(key);
        }
    }

    public void provideKey(int scanCode, char ascii) {
        inputQueue.offer(new KeyStroke(scanCode, ascii));
    }

    public int getCursorRow(int page) {
        return cursorRow[page & 7];
    }

    public int getCursorCol(int page) {
        return cursorCol[page & 7];
    }

    public static KeyStroke keyStrokeFor(char c) {
        return switch (c) {
            case '\r', '\n' -> KeyStroke.ENTER;
            case 27 -> KeyStroke.ESCAPE;
            case '\b' -> KeyStroke.BACKSPACE;
            case '\t' -> KeyStroke.TAB;
            case '\u2191' -> KeyStroke.UP;
            case '\u2193' -> KeyStroke.DOWN;
            case '\u2190' -> KeyStroke.LEFT;
            case '\u2192' -> KeyStroke.RIGHT;
            case ' ' -> new KeyStroke(0x39, ' ');
            default -> new KeyStroke(scanCodeForAscii(c), c);
        };
    }

    public static int scanCodeForAscii(char c) {
        char lower = Character.toLowerCase(c);
        return switch (lower) {
            case '1' -> 0x02;
            case '2' -> 0x03;
            case '3' -> 0x04;
            case '4' -> 0x05;
            case '5' -> 0x06;
            case '6' -> 0x07;
            case '7' -> 0x08;
            case '8' -> 0x09;
            case '9' -> 0x0A;
            case '0' -> 0x0B;
            case 'q' -> 0x10;
            case 'w' -> 0x11;
            case 'e' -> 0x12;
            case 'r' -> 0x13;
            case 't' -> 0x14;
            case 'y' -> 0x15;
            case 'u' -> 0x16;
            case 'i' -> 0x17;
            case 'o' -> 0x18;
            case 'p' -> 0x19;
            case 'a' -> 0x1E;
            case 's' -> 0x1F;
            case 'd' -> 0x20;
            case 'f' -> 0x21;
            case 'g' -> 0x22;
            case 'h' -> 0x23;
            case 'j' -> 0x24;
            case 'k' -> 0x25;
            case 'l' -> 0x26;
            case 'z' -> 0x2C;
            case 'x' -> 0x2D;
            case 'c' -> 0x2E;
            case 'v' -> 0x2F;
            case 'b' -> 0x30;
            case 'n' -> 0x31;
            case 'm' -> 0x32;
            default -> 0x00;
        };
    }

    /** The last {@link #CAPTURED_OUTPUT_LIMIT} characters the program printed. */
    public String capturedOutput() {
        return outputBuffer.toString();
    }

    /**
     * Gives the listener what the program printed since the last flush. The emulator calls it before it pauses,
     * waits for a key or ends, and regularly while it runs, so output appears promptly without one event per
     * character.
     */
    public void flushOutput() {
        if (pendingOutput.isEmpty()) {
            return;
        }
        String text = pendingOutput.toString();
        pendingOutput.setLength(0);
        outputListener.accept(text);
    }

    @Override
    public boolean handleInterrupt(int intNumber, Cpu8086 cpu) {
        CpuRegisters regs = cpu.registers();
        RealModeMemory memory = cpu.memory();

        switch (intNumber) {
            case 0x20 -> { // INT 20h: Terminate program
                cpu.setExitCode(0);
                cpu.setState(Cpu8086.State.TERMINATED);
                return true;
            }
            case 0x21 -> { // INT 21h: DOS services
                return handleInt21(cpu, regs, memory);
            }
            case 0x10 -> { // INT 10h: BIOS Video services
                return handleInt10(cpu, regs, memory);
            }
            case 0x16 -> { // INT 16h: BIOS Keyboard services
                return handleInt16(cpu, regs, memory);
            }
            case 0x1A -> { // INT 1Ah: BIOS System Timer services
                return handleInt1A(cpu, regs, memory);
            }
            default -> {
                // Not handled by high-level DOS/BIOS
                return false;
            }
        }
    }

    private boolean handleInt21(Cpu8086 cpu, CpuRegisters regs, RealModeMemory memory) {
        int ah = regs.getAh();

        switch (ah) {
            case 0x00 -> { // Terminate program
                cpu.setExitCode(0);
                cpu.setState(Cpu8086.State.TERMINATED);
                return true;
            }
            case 0x01 -> { // Read character with echo
                char c = nextChar();
                if (c == 0x08) {
                    // DOS echoes Backspace as a cursor move; the console shows it by removing the last character.
                    emitChar(c);
                    regs.setAl(c);
                    return true;
                }
                emitChar(c);
                regs.setAl(c);
                return true;
            }
            case 0x02 -> { // Display character in DL
                char c = (char) regs.getDl();
                emitChar(c);
                regs.setAl(c);
                return true;
            }
            case 0x06 -> { // Direct console I/O
                if (regs.getDl() == 0xFF) {
                    if (inputQueue.isEmpty()) {
                        regs.setZf(true);
                        regs.setAl(0);
                    } else {
                        regs.setZf(false);
                        regs.setAl(nextChar());
                    }
                } else {
                    emitChar((char) regs.getDl());
                }
                return true;
            }
            case 0x07, 0x08 -> { // Character input without echo
                regs.setAl(nextChar());
                return true;
            }
            case 0x09 -> { // Print '$'-terminated string at DS:DX
                int seg = regs.ds;
                int off = regs.dx;
                StringBuilder sb = new StringBuilder();
                // A string without its '$' is a classic mistake; DOS prints until it happens to find one. The
                // segment holds 64 KiB, so a string longer than that never ends: stop and explain instead of hanging.
                while (true) {
                    int b = memory.read8(seg, off);
                    if (b == '$') break;
                    if (sb.length() >= 0x10000) {
                        cpu.fail("emu.string.unterminated",
                                "INT 21h function 09h printed 64 KiB without finding the '$' that ends the string at "
                                        + String.format("%04X:%04X", regs.ds, regs.dx) + ".",
                                String.format("%04X:%04X", regs.ds, regs.dx));
                        return true;
                    }
                    sb.append((char) b);
                    off = (off + 1) & 0xFFFF;
                }
                emit(sb.toString());
                regs.setAl('$');
                return true;
            }
            case 0x0A -> { // Buffered input at DS:DX
                int seg = regs.ds;
                int off = regs.dx;
                int maxLen = memory.read8(seg, off);
                // Ralf Brown's Interrupt List INT 21h AH=0Ah:
                // If the first byte (maximum buffer length) is 0, this function returns immediately
                // without waiting for input or modifying memory beyond byte 0.
                if (maxLen <= 0) {
                    return true;
                }
                StringBuilder sb = new StringBuilder();
                int maxChars = Math.max(0, maxLen - 1);
                while (true) {
                    char c = nextChar();
                    if (c == '\r' || c == '\n') {
                        // DOS echoes Enter as a carriage return only; the program prints its own line feed.
                        emitChar(ENTER);
                        break;
                    }
                    if (c == '\b') {
                        if (!sb.isEmpty()) {
                            sb.deleteCharAt(sb.length() - 1);
                            emitChar('\b');
                        }
                        continue;
                    }
                    if (sb.length() < maxChars) {
                        emitChar(c);
                        sb.append(c);
                    }
                }
                memory.write8(seg, (off + 1) & 0xFFFF, sb.length());
                for (int i = 0; i < sb.length(); i++) {
                    memory.write8(seg, (off + 2 + i) & 0xFFFF, sb.charAt(i));
                }
                memory.write8(seg, (off + 2 + sb.length()) & 0xFFFF, '\r');
                return true;
            }
            case 0x0B -> { // Check input status
                regs.setAl(inputQueue.isEmpty() ? 0x00 : 0xFF);
                return true;
            }
            case 0x25 -> { // Set interrupt vector AL to DS:DX
                int intNum = regs.getAl();
                memory.writePhysical16(intNum * 4, regs.dx);
                memory.writePhysical16(intNum * 4 + 2, regs.ds);
                return true;
            }
            case 0x2A -> { // Get system date
                java.time.LocalDate now = java.time.LocalDate.now();
                int dow = now.getDayOfWeek().getValue() % 7; // Sunday=0, Monday=1, ..., Saturday=6
                regs.setAl(dow);
                regs.cx = now.getYear();
                regs.setDh(now.getMonthValue());
                regs.setDl(now.getDayOfMonth());
                return true;
            }
            case 0x2C -> { // Get system time
                java.time.LocalTime now = java.time.LocalTime.now();
                regs.setCh(now.getHour());
                regs.setCl(now.getMinute());
                regs.setDh(now.getSecond());
                regs.setDl(now.getNano() / 10_000_000);
                return true;
            }
            case 0x30 -> { // Get DOS version (DOS 5.0)
                regs.setAl(5);
                regs.setAh(0);
                regs.bx = 0xFF00;
                regs.cx = 0;
                return true;
            }
            case 0x35 -> { // Get interrupt vector AL into ES:BX
                int intNum = regs.getAl();
                regs.bx = memory.readPhysical16(intNum * 4);
                regs.es = memory.readPhysical16(intNum * 4 + 2);
                return true;
            }
            case 0x3F -> { // Read from file or device
                int handle = regs.bx;
                if (handle == 0) { // Stdin (console)
                    int count = regs.cx;
                    int seg = regs.ds;
                    int off = regs.dx;
                    int read = 0;
                    while (read < count) {
                        char c = nextChar();
                        if (c == '\r' || c == '\n') {
                            emit("\r\n");
                            memory.write8(seg, (off + read) & 0xFFFF, '\r');
                            read++;
                            if (read < count) {
                                memory.write8(seg, (off + read) & 0xFFFF, '\n');
                                read++;
                            }
                            break;
                        }
                        emitChar(c);
                        memory.write8(seg, (off + read) & 0xFFFF, c);
                        read++;
                    }
                    regs.ax = read;
                    regs.setCf(false);
                    return true;
                }
                if (reportedFunctions.add(0x2100 | ah)) {
                    problem("emu.dos.unsupported", String.format(
                            "The built-in emulator does not provide INT 21h function 3Fh for file handle %d; it returned without doing anything.", handle),
                            String.format("3Fh:%d", handle));
                }
                regs.setCf(true);
                regs.ax = 6; // Invalid handle
                return true;
            }
            case 0x40 -> { // Write to file or device
                int handle = regs.bx;
                if (handle == 1 || handle == 2) { // Stdout or Stderr
                    int count = regs.cx;
                    int seg = regs.ds;
                    int off = regs.dx;
                    if (count > 0) {
                        StringBuilder sb = new StringBuilder(count);
                        for (int i = 0; i < count; i++) {
                            int b = memory.read8(seg, (off + i) & 0xFFFF);
                            sb.append((char) b);
                        }
                        emit(sb.toString());
                    }
                    regs.ax = count;
                    regs.setCf(false);
                    return true;
                }
                if (reportedFunctions.add(0x2100 | ah)) {
                    problem("emu.dos.unsupported", String.format(
                            "The built-in emulator does not provide INT 21h function 40h for file handle %d; it returned without doing anything.", handle),
                            String.format("40h:%d", handle));
                }
                regs.setCf(true);
                regs.ax = 6; // Invalid handle
                return true;
            }
            case 0x4C -> { // Terminate with exit code in AL
                cpu.setExitCode(regs.getAl());
                cpu.setState(Cpu8086.State.TERMINATED);
                return true;
            }
            default -> {
                // The program keeps running as before, but the student learns why a file or clock call did nothing.
                if (reportedFunctions.add(ah)) {
                    problem("emu.dos.unsupported", String.format(
                            "The built-in emulator does not provide INT 21h function %02Xh; it returned without doing"
                                    + " anything. Debug programs that use it with the external debugger.", ah),
                            String.format("%02X", ah));
                }
                regs.setAl(0);
                return true;
            }
        }
    }

    /** Reports a service the program used that the emulator only pretends to perform. */
    private void problem(String code, String message, String... arguments) {
        problemListener.report(code, message, List.of(arguments));
    }

    private boolean handleInt10(Cpu8086 cpu, CpuRegisters regs, RealModeMemory memory) {
        int ah = regs.getAh();
        switch (ah) {
            case 0x00 -> { // Set video mode
                return true;
            }
            case 0x02 -> { // Set cursor position (DH=row, DL=column, BH=page)
                int page = regs.getBh() & 7;
                cursorRow[page] = Math.min(24, Math.max(0, regs.getDh()));
                cursorCol[page] = Math.min(79, Math.max(0, regs.getDl()));
                return true;
            }
            case 0x03 -> { // Get cursor position (DH=row, DL=column, CH/CL=cursor shape)
                int page = regs.getBh() & 7;
                regs.setCh(6); // cursor start scanline
                regs.setCl(7); // cursor end scanline
                regs.setDh(cursorRow[page]);
                regs.setDl(cursorCol[page]);
                return true;
            }
            case 0x09, 0x0A -> { // Write character (and attribute) CX times at cursor position
                int count = regs.cx;
                if (count > 0) {
                    char c = (char) (regs.getAl() & 0xFF);
                    StringBuilder sb = new StringBuilder(count);
                    for (int i = 0; i < count; i++) {
                        sb.append(c);
                    }
                    emit(sb.toString());
                }
                return true;
            }
            case 0x0E -> { // Teletype output character in AL
                char c = (char) regs.getAl();
                emitChar(c);
                int page = regs.getBh() & 7;
                if (c == '\r') {
                    cursorCol[page] = 0;
                } else if (c == '\n') {
                    cursorRow[page] = Math.min(24, cursorRow[page] + 1);
                } else if (c == '\b') {
                    cursorCol[page] = Math.max(0, cursorCol[page] - 1);
                } else {
                    cursorCol[page]++;
                    if (cursorCol[page] >= 80) {
                        cursorCol[page] = 0;
                        cursorRow[page] = Math.min(24, cursorRow[page] + 1);
                    }
                }
                return true;
            }
            case 0x0F -> { // Get current video mode
                regs.setAl(3); // 80x25 color text
                regs.setAh(80); // columns
                regs.setBh(0); // page 0
                return true;
            }
            case 0x13 -> { // Write string (ES:BP, CX=length, DH=row, DL=col, AL=mode)
                int mode = regs.getAl();
                int page = regs.getBh() & 7;
                int count = regs.cx;
                int seg = regs.es;
                int off = regs.bp;
                boolean hasAttributes = (mode & 2) != 0;
                boolean updateCursor = (mode & 1) != 0;

                cursorRow[page] = Math.min(24, Math.max(0, regs.getDh()));
                cursorCol[page] = Math.min(79, Math.max(0, regs.getDl()));

                if (count > 0) {
                    StringBuilder sb = new StringBuilder(count);
                    int step = hasAttributes ? 2 : 1;
                    for (int i = 0; i < count; i++) {
                        int b = memory.read8(seg, (off + i * step) & 0xFFFF);
                        sb.append((char) b);
                    }
                    emit(sb.toString());

                    if (updateCursor) {
                        int totalCols = cursorCol[page] + count;
                        cursorRow[page] = Math.min(24, cursorRow[page] + totalCols / 80);
                        cursorCol[page] = totalCols % 80;
                    }
                }
                return true;
            }
            default -> {
                if (reportedFunctions.add(0x1000 | ah)) {
                    problem("emu.bios.unsupported", String.format(
                            "The built-in emulator does not provide INT 10h function %02Xh; it returned without doing anything.", ah),
                            String.format("10h:%02X", ah));
                }
                return true;
            }
        }
    }

    private boolean handleInt16(Cpu8086 cpu, CpuRegisters regs, RealModeMemory memory) {
        int ah = regs.getAh();
        switch (ah) {
            case 0x00, 0x10 -> { // Read keystroke (AH=scan code, AL=ASCII code)
                KeyStroke key = nextKey();
                regs.setAl(key.ascii());
                regs.setAh(key.scanCode());
                return true;
            }
            case 0x01, 0x11 -> { // Check keystroke
                if (inputQueue.isEmpty()) {
                    regs.setZf(true);
                } else {
                    regs.setZf(false);
                    KeyStroke key = inputQueue.peek();
                    regs.setAl(key.ascii());
                    regs.setAh(key.scanCode());
                }
                return true;
            }
            default -> {
                if (reportedFunctions.add(0x1600 | ah)) {
                    problem("emu.bios.unsupported", String.format(
                            "The built-in emulator does not provide INT 16h function %02Xh; it returned without doing anything.", ah),
                            String.format("16h:%02X", ah));
                }
                return true;
            }
        }
    }

    private boolean handleInt1A(Cpu8086 cpu, CpuRegisters regs, RealModeMemory memory) {
        int ah = regs.getAh();
        switch (ah) {
            case 0x00 -> { // Read system timer counter ticks (~18.2 Hz)
                java.time.LocalTime now = java.time.LocalTime.now();
                double seconds = now.toSecondOfDay() + now.getNano() / 1_000_000_000.0;
                long ticks = (long) (seconds * (1193182.0 / 65536.0));
                regs.cx = (int) ((ticks >> 16) & 0xFFFF);
                regs.dx = (int) (ticks & 0xFFFF);
                regs.setAl(0); // midnight rollover flag (0 = not passed midnight)
                return true;
            }
            default -> {
                if (reportedFunctions.add(0x1A00 | ah)) {
                    problem("emu.bios.unsupported", String.format(
                            "The built-in emulator does not provide INT 1Ah function %02Xh; it returned without doing anything.", ah),
                            String.format("1Ah:%02X", ah));
                }
                return true;
            }
        }
    }

    /**
     * The next key. With interactive input the emulator thread waits for the user to type; Stop interrupts the
     * wait. Without it, an empty keyboard answers Enter, which keeps scripted runs from blocking.
     */
    private KeyStroke nextKey() {
        KeyStroke typed = inputQueue.poll();
        if (typed != null) {
            return typed;
        }
        Runnable onWaiting = waitingForInput;
        if (onWaiting == null) {
            return KeyStroke.ENTER;
        }
        // The prompt the program printed must be visible before the user is asked to type.
        flushOutput();
        onWaiting.run();
        try {
            return inputQueue.take();
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return KeyStroke.ENTER;
        }
    }

    private char nextChar() {
        return nextKey().ascii();
    }

    private void emitChar(char c) {
        emit(String.valueOf(c));
    }

    private void emit(String text) {
        outputBuffer.append(text);
        if (outputBuffer.length() > 2 * CAPTURED_OUTPUT_LIMIT) {
            outputBuffer.delete(0, outputBuffer.length() - CAPTURED_OUTPUT_LIMIT);
        }
        pendingOutput.append(text);
        if (pendingOutput.length() >= OUTPUT_CHUNK) {
            flushOutput();
        }
    }
}
