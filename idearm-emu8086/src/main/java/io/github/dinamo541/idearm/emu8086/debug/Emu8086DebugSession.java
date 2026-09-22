package io.github.dinamo541.idearm.emu8086.debug;

import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.DebugEvent;
import io.github.dinamo541.idearm.domain.debug.MemoryView;
import io.github.dinamo541.idearm.domain.debug.RegisterState;
import io.github.dinamo541.idearm.domain.debug.StackFrame;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;
import io.github.dinamo541.idearm.domain.port.DebugSession;
import io.github.dinamo541.idearm.emu8086.cpu.Cpu8086;
import io.github.dinamo541.idearm.emu8086.cpu.ModRmDecoder;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;
import io.github.dinamo541.idearm.emu8086.dos.DosInterruptHandler;
import io.github.dinamo541.idearm.emu8086.loader.LoadedProgram;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * In-process 8086 interactive debugging session implementing domain port DebugSession.
 */
public final class Emu8086DebugSession implements DebugSession {

    /** The exit code reported when the program ends on a fault (division by zero, unsupported instruction). */
    static final int FAULT_EXIT_CODE = 255;

    private final Cpu8086 cpu;
    private final RealModeMemory memory;
    private final LoadedProgram program;
    private final SourceMap sourceMap;
    private final List<Breakpoint> breakpoints;
    private final Set<Integer> breakpointOffsets = new HashSet<>();
    private final Consumer<DebugEvent> events;
    private final DosInterruptHandler dosHandler;
    private final long startTimeNanos;

    private final CompletableFuture<ExitInfo> exitFuture = new CompletableFuture<>();
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private volatile SessionState sessionState = SessionState.RUNNING;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "emu8086-debugger-worker");
        t.setDaemon(true);
        return t;
    });

    public Emu8086DebugSession(Cpu8086 cpu,
                               RealModeMemory memory,
                               LoadedProgram program,
                               SourceMap sourceMap,
                               List<Breakpoint> breakpoints,
                               DosInterruptHandler dosHandler,
                               Consumer<DebugEvent> events) {
        this.cpu = Objects.requireNonNull(cpu, "cpu cannot be null");
        this.memory = Objects.requireNonNull(memory, "memory cannot be null");
        this.program = Objects.requireNonNull(program, "program cannot be null");
        this.sourceMap = (sourceMap != null) ? sourceMap : SourceMap.empty();
        this.breakpoints = (breakpoints != null) ? List.copyOf(breakpoints) : List.of();
        this.dosHandler = (dosHandler != null) ? dosHandler : new DosInterruptHandler();
        this.events = (events != null) ? events : e -> {};
        this.startTimeNanos = System.nanoTime();

        // Wire console output from DOS calls into DebugEvent.Output
        this.dosHandler.setOutputListener(text -> this.events.accept(new DebugEvent.Output(text)));
        // Keyboard reads wait for the user, who types into the IDE's program console (sendInput).
        this.dosHandler.enableInteractiveInput(() -> this.events.accept(new DebugEvent.WaitingForInput()));
        // Services the emulator only pretends to perform are explained once, where the program asked for them.
        this.dosHandler.setProblemListener((code, message, arguments) -> this.events.accept(new DebugEvent.Problem(
                diagnostic(Severity.WARNING, code, message, arguments, cpu.instructionIp()))));

        // Index active breakpoint offsets
        for (Breakpoint bp : this.breakpoints) {
            if (bp.enabled()) {
                this.sourceMap.findOffset(bp.path(), bp.line()).ifPresent(breakpointOffsets::add);
            }
        }

        // Initially pause at entry point
        this.paused.set(true);
        notifyPaused();
    }

    @Override
    public CompletableFuture<ExitInfo> exit() {
        return exitFuture;
    }

    @Override
    public SessionState state() {
        return sessionState;
    }

    @Override
    public void resume() {
        if (stopped.get() || exitFuture.isDone()) return;
        paused.set(false);
        events.accept(new DebugEvent.Resumed());

        executor.submit(() -> {
            if (endIfFaulted()) return;
            boolean firstInstruction = true;

            while (!paused.get() && !stopped.get() && cpu.state() == Cpu8086.State.RUNNING) {
                if (!firstInstruction && isBreakpointHit(cpu.registers().ip)) {
                    paused.set(true);
                    notifyPaused();
                    return;
                }
                firstInstruction = false;

                boolean ok = cpu.step();
                if (!ok) break;

                if (isBreakpointHit(cpu.registers().ip)) {
                    paused.set(true);
                    notifyPaused();
                    return;
                }
            }

            finishRun();
        });
    }

    @Override
    public void stepInto() {
        if (stopped.get() || exitFuture.isDone()) return;
        executor.submit(() -> {
            if (endIfFaulted()) return;
            paused.set(true);
            boolean ok = cpu.step();
            if (ok && cpu.state() == Cpu8086.State.RUNNING) {
                notifyPaused();
            } else {
                finishRun();
            }
        });
    }

    @Override
    public void stepOver() {
        if (stopped.get() || exitFuture.isDone()) return;
        executor.submit(() -> {
            if (endIfFaulted()) return;
            int cs = cpu.registers().cs;
            int ip = cpu.registers().ip;
            int instrLen = calculateCallOrIntLength(cs, ip);

            if (instrLen <= 0) {
                // Not a CALL or INT -> regular step
                boolean ok = cpu.step();
                if (ok && cpu.state() == Cpu8086.State.RUNNING) {
                    notifyPaused();
                } else {
                    finishRun();
                }
                return;
            }

            // Target to resume until
            int targetIp = (ip + instrLen) & 0xFFFF;
            paused.set(false);
            events.accept(new DebugEvent.Resumed());

            boolean first = true;
            while (!paused.get() && !stopped.get() && cpu.state() == Cpu8086.State.RUNNING) {
                if (!first && isBreakpointHit(cpu.registers().ip)) {
                    paused.set(true);
                    notifyPaused();
                    return;
                }
                first = false;

                boolean ok = cpu.step();
                if (!ok) break;

                if (cpu.registers().cs == cs && cpu.registers().ip == targetIp) {
                    paused.set(true);
                    notifyPaused();
                    return;
                }

                if (isBreakpointHit(cpu.registers().ip)) {
                    paused.set(true);
                    notifyPaused();
                    return;
                }
            }

            finishRun();
        });
    }

    @Override
    public void stepOut() {
        if (stopped.get() || exitFuture.isDone()) return;
        executor.submit(() -> {
            if (endIfFaulted()) return;
            int targetDepth = cpu.getCallDepth() - 1;
            paused.set(false);
            events.accept(new DebugEvent.Resumed());

            boolean first = true;
            while (!paused.get() && !stopped.get() && cpu.state() == Cpu8086.State.RUNNING) {
                if (!first && isBreakpointHit(cpu.registers().ip)) {
                    paused.set(true);
                    notifyPaused();
                    return;
                }
                first = false;

                boolean ok = cpu.step();
                if (!ok) break;

                if (cpu.getCallDepth() <= targetDepth) {
                    paused.set(true);
                    notifyPaused();
                    return;
                }

                if (isBreakpointHit(cpu.registers().ip)) {
                    paused.set(true);
                    notifyPaused();
                    return;
                }
            }

            finishRun();
        });
    }

    /** Keys typed in the IDE's program console: each character is one key, {@code } is Enter. */
    @Override
    public void sendInput(String text) {
        dosHandler.provideInput(text);
    }

    @Override
    public long instructionsExecuted() {
        return cpu.getInstructionsExecuted();
    }

    @Override
    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            paused.set(false);
            sessionState = SessionState.STOPPED;
            cpu.setState(Cpu8086.State.TERMINATED);
            Duration duration = Duration.ofNanos(System.nanoTime() - startTimeNanos);
            exitFuture.complete(ExitInfo.stopped(duration));
            events.accept(new DebugEvent.Stopped());
            executor.shutdownNow();
        }
    }

    @Override
    public RegisterState registers() {
        return cpu.registers().toDomainRegisterState();
    }

    @Override
    public MemoryView readMemory(int segment, int offset, int length) {
        byte[] bytes = memory.readBlock(segment, offset, length);
        return new MemoryView(segment, offset, bytes);
    }

    /**
     * Watches as written in the source: a register ({@code AX}, {@code DL}, {@code FLAGS}), a number, or memory such
     * as {@code [BX+2]}, {@code byte [SI]} or {@code [ES:DI]}. Memory is read through DS, or SS when BP is used.
     * Labels are not known to the emulator, so they cannot be watched.
     */
    @Override
    public Optional<String> evaluateExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            return Optional.empty();
        }
        RegisterState state = registers();
        String text = expression.strip().toUpperCase(Locale.ROOT);
        try {
            var memory = MEMORY_OPERAND.matcher(text);
            if (memory.matches()) {
                boolean bytes = "BYTE".equals(memory.group(1));
                String inner = memory.group(2).strip();
                int segment;
                int colon = inner.indexOf(':');
                if (colon >= 0) {
                    segment = (int) value(inner.substring(0, colon).strip(), state);
                    inner = inner.substring(colon + 1);
                } else {
                    segment = inner.matches(".*\\bBP\\b.*") ? state.ss() : state.ds();
                }
                int offset = (int) sum(inner, state) & 0xFFFF;
                long value = bytes ? this.memory.read8(segment, offset) : this.memory.read16(segment, offset);
                return Optional.of(format(value, bytes ? 2 : 4));
            }
            long value = sum(text, state);
            return Optional.of(format(value, isByteRegister(text) ? 2 : 4));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    private static final java.util.regex.Pattern MEMORY_OPERAND =
            java.util.regex.Pattern.compile("^(?:(BYTE|WORD)\\s*(?:PTR\\s*)?)?\\[(.+)]$");

    private static String format(long value, int hexDigits) {
        return value + " (0x" + String.format("%0" + hexDigits + "X", value) + ")";
    }

    private static boolean isByteRegister(String name) {
        return name.matches("[ABCD][HL]");
    }

    /** Terms joined by + and -, such as {@code BX+SI+2} or {@code 100h-2}. */
    private static long sum(String expression, RegisterState state) {
        long total = 0;
        int sign = 1;
        var term = new StringBuilder();
        for (char c : (expression + "+").toCharArray()) {
            if (c == '+' || c == '-') {
                String token = term.toString().strip();
                if (!token.isEmpty()) {
                    total += sign * value(token, state);
                } else if (c == '+') {
                    continue;
                }
                sign = c == '-' ? -1 : 1;
                term.setLength(0);
            } else {
                term.append(c);
            }
        }
        return total;
    }

    private static long value(String token, RegisterState state) {
        return switch (token) {
            case "AX" -> state.ax();
            case "BX" -> state.bx();
            case "CX" -> state.cx();
            case "DX" -> state.dx();
            case "SI" -> state.si();
            case "DI" -> state.di();
            case "BP" -> state.bp();
            case "SP" -> state.sp();
            case "IP" -> state.ip();
            case "CS" -> state.cs();
            case "DS" -> state.ds();
            case "ES" -> state.es();
            case "SS" -> state.ss();
            case "FLAGS" -> state.flags();
            case "AH" -> state.ah();
            case "AL" -> state.al();
            case "BH" -> state.bh();
            case "BL" -> state.bl();
            case "CH" -> state.ch();
            case "CL" -> state.cl();
            case "DH" -> state.dh();
            case "DL" -> state.dl();
            default -> number(token);
        };
    }

    private static long number(String token) {
        if (token.startsWith("0X")) {
            return Long.parseLong(token.substring(2), 16);
        }
        if (token.endsWith("H") && Character.isDigit(token.charAt(0))) {
            return Long.parseLong(token.substring(0, token.length() - 1), 16);
        }
        if (token.endsWith("B") && token.chars().limit(token.length() - 1).allMatch(c -> c == '0' || c == '1')) {
            return Long.parseLong(token.substring(0, token.length() - 1), 2);
        }
        return Long.parseLong(token);
    }

    @Override
    public Optional<Long> evaluateVariable(String filePath, int line, int byteSize) {
        return sourceMap.findDataOffset(filePath, line).map(offset -> {
            int segment = cpu.registers().ds; // Assuming variables are accessed via DS
            if (byteSize == 1) {
                return (long) memory.read8(segment, offset);
            } else if (byteSize == 2) {
                return (long) memory.read16(segment, offset);
            }
            return 0L;
        });
    }

    @Override
    public List<StackFrame> stack(int depth) {
        int maxFrames = Math.max(0, depth);
        List<StackFrame> frames = new ArrayList<>(maxFrames);
        int sp = cpu.registers().sp;
        int ss = cpu.registers().ss;

        for (int i = 0; i < maxFrames; i++) {
            int offset = (sp + (i * 2)) & 0xFFFF;
            int val = memory.read16(ss, offset);
            frames.add(new StackFrame(i * 2, ss, offset, val));
        }
        return Collections.unmodifiableList(frames);
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * A breakpoint stops only on the first instruction of its line. Matching the nearest mapped line instead would
     * also stop inside code the listing does not describe, such as another module's procedures.
     */
    private boolean isBreakpointHit(int ip) {
        return breakpointOffsets.contains(ip);
    }

    private void notifyPaused() {
        RegisterState regState = registers();
        var locOpt = sourceMap.findLocation(cpu.registers().ip);
        // Without a mapped line the registers still update, but no editor line is marked.
        String file = locOpt.map(SourceLocation::file).orElse("");
        int line = locOpt.map(SourceLocation::line).orElse(0);

        events.accept(new DebugEvent.Paused(file, line, regState));
    }

    /**
     * After the CPU stopped running: a fault pauses on the instruction that caused it, with an explanation, and the
     * next resume or step ends the program; an {@code INT 3} pauses like a breakpoint; anything else ends the program.
     */
    private void finishRun() {
        Cpu8086.Fault fault = cpu.fault();
        if (cpu.state() != Cpu8086.State.FAULTED || fault == null) {
            checkTermination();
            return;
        }
        if (Cpu8086.BREAKPOINT.equals(fault.code())) {
            cpu.continueAfterBreakpoint();
            paused.set(true);
            notifyPaused();
            return;
        }
        events.accept(new DebugEvent.Problem(
                diagnostic(Severity.ERROR, fault.code(), fault.message(), fault.arguments(), fault.ip())));
        paused.set(true);
        notifyPaused();
    }

    /** The program cannot continue past a fault it was paused on: resuming ends it, as DOS would. */
    private boolean endIfFaulted() {
        Cpu8086.Fault fault = cpu.fault();
        if (cpu.state() != Cpu8086.State.FAULTED || fault == null) {
            return false;
        }
        sessionState = SessionState.EXITED;
        Duration duration = Duration.ofNanos(System.nanoTime() - startTimeNanos);
        ExitInfo exitInfo = ExitInfo.error(FAULT_EXIT_CODE, duration, "The program ended: " + fault.message());
        exitFuture.complete(exitInfo);
        events.accept(new DebugEvent.Exited(exitInfo));
        executor.shutdown();
        return true;
    }

    private Diagnostic diagnostic(Severity severity, String code, String message, List<String> arguments, int ip) {
        Location location = sourceMap.findLocation(ip)
                .filter(found -> found.line() > 0)
                .map(found -> new Location(found.file(), found.line(), null))
                .orElse(null);
        return new Diagnostic(severity, code, message, location, "emu8086", "", arguments);
    }

    private void checkTermination() {
        if (cpu.state() == Cpu8086.State.TERMINATED || cpu.state() == Cpu8086.State.HALTED) {
            sessionState = SessionState.EXITED;
            Duration duration = Duration.ofNanos(System.nanoTime() - startTimeNanos);
            int code = cpu.exitCode();
            ExitInfo exitInfo = new ExitInfo(code, duration, false, "Debug session terminated with exit code " + code);
            exitFuture.complete(exitInfo);
            events.accept(new DebugEvent.Exited(exitInfo));
            executor.shutdown();
        }
    }

    private int calculateCallOrIntLength(int cs, int ip) {
        int op = memory.read8(cs, ip);
        if (op == 0xCD) return 2; // INT imm8
        if (op == 0xCC || op == 0xCE) return 1; // INT 3, INTO
        if (op == 0xE8) return 3; // CALL near disp16
        if (op == 0x9A) return 5; // CALL far ptr16:16
        if (op == 0xFF) {
            int modrm = memory.read8(cs, (ip + 1) & 0xFFFF);
            int reg = (modrm >> 3) & 7;
            if (reg == 2 || reg == 3) { // CALL rm16 or CALL m16:16
                int mod = (modrm >> 6) & 3;
                int rm = modrm & 7;
                int dispBytes = (mod == 1) ? 1 : ((mod == 2 || (mod == 0 && rm == 6)) ? 2 : 0);
                return 2 + dispBytes; // 0xFF + modrm + disp
            }
        }
        return 0; // Not a CALL or INT
    }
}
