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

    private final StringBuilder outputBuffer = new StringBuilder();
    /** Keys typed and not read yet: the IDE thread adds them, the emulator thread takes them. */
    private final java.util.concurrent.BlockingQueue<Character> inputQueue = new java.util.concurrent.LinkedBlockingQueue<>();
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
            for (char c : input.toCharArray()) {
                inputQueue.offer(c);
            }
        }
    }

    public String capturedOutput() {
        return outputBuffer.toString();
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
                String str = sb.toString();
                outputBuffer.append(str);
                outputListener.accept(str);
                regs.setAl('$');
                return true;
            }
            case 0x0A -> { // Buffered input at DS:DX
                int seg = regs.ds;
                int off = regs.dx;
                int maxLen = memory.read8(seg, off);
                StringBuilder sb = new StringBuilder();
                while (sb.length() < maxLen) {
                    char c = nextChar();
                    if (c == '\r' || c == '\n') {
                        // DOS echoes Enter as a carriage return only; the program prints its own line feed.
                        emitChar(ENTER);
                        break;
                    }
                    emitChar(c);
                    sb.append(c);
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
            case 0x0E -> { // Teletype output character in AL
                emitChar((char) regs.getAl());
                return true;
            }
            case 0x00 -> { // Set video mode
                return true;
            }
            case 0x0F -> { // Get current video mode
                regs.setAl(3); // 80x25 color text
                regs.setAh(80); // columns
                regs.setBh(0); // page 0
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    private boolean handleInt16(Cpu8086 cpu, CpuRegisters regs, RealModeMemory memory) {
        int ah = regs.getAh();
        switch (ah) {
            case 0x00 -> { // Read keystroke
                char c = nextChar();
                regs.setAl(c);
                regs.setAh(0); // scan code
                return true;
            }
            case 0x01 -> { // Check keystroke
                if (inputQueue.isEmpty()) {
                    regs.setZf(true);
                } else {
                    regs.setZf(false);
                    char c = inputQueue.peek();
                    regs.setAl(c);
                    regs.setAh(0);
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    /**
     * The next key. With interactive input the emulator thread waits for the user to type; Stop interrupts the
     * wait. Without it, an empty keyboard answers Enter, which keeps scripted runs from blocking.
     */
    private char nextChar() {
        Character typed = inputQueue.poll();
        if (typed != null) {
            return typed;
        }
        Runnable onWaiting = waitingForInput;
        if (onWaiting == null) {
            return ENTER;
        }
        onWaiting.run();
        try {
            return inputQueue.take();
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return ENTER;
        }
    }

    private void emitChar(char c) {
        outputBuffer.append(c);
        outputListener.accept(String.valueOf(c));
    }
}
