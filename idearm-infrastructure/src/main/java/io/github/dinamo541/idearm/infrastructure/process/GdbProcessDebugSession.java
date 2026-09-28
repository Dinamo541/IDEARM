package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.debug.*;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;
import io.github.dinamo541.idearm.domain.port.DebugSession;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GDB-RSP / GDB-MI backed interactive debugging session for native 32-bit and 64-bit processes.
 * Communicates with {@code gdb.exe} via standard I/O using the GDB/MI protocol (mi3).
 */
public final class GdbProcessDebugSession implements DebugSession {

    private final Process process;
    private final WindowsJob job;
    private final BufferedWriter writer;
    private final BufferedReader reader;
    private final Consumer<DebugEvent> events;
    private final long startNanos;

    private final CompletableFuture<ExitInfo> exitFuture = new CompletableFuture<>();
    private final AtomicLong tokenSequence = new AtomicLong(1);
    private final Map<Long, CompletableFuture<GdbMiRecord>> pendingCommands = new ConcurrentHashMap<>();
    private final Map<Integer, String> registerNames = new ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.AtomicBoolean paused = new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile SessionState state = SessionState.RUNNING;
    private volatile RegisterState currentRegisters = RegisterState.empty();
    private final AtomicLong instructionsExecuted = new AtomicLong(0);
    private volatile boolean closed = false;
    /** Set by {@link #stop()}: the end of GDB's output that follows is expected. */
    private volatile boolean stopping = false;
    /** Set when GDB's output ended: no command can be answered any more. */
    private volatile boolean gdbEnded = false;
    /** Set while a pause is in flight, so the stop it causes is recognised and the program's thread selected. */
    private final AtomicBoolean interruptRequested = new AtomicBoolean(false);
    /** The numbers GDB gave the breakpoints this session inserted, so they can be replaced. */
    private final Set<String> breakpointNumbers = ConcurrentHashMap.newKeySet();

    private final Thread readerThread;
    private final ExecutorService eventThread = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "GdbMi-Events");
        thread.setDaemon(true);
        return thread;
    });
    /** Sends breakpoint changes, which wait for GDB's answers, off the caller's thread. */
    private final ExecutorService breakpointThread = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "GdbMi-Breakpoints");
        thread.setDaemon(true);
        return thread;
    });
    /** The breakpoints the user wants and GDB has not been given yet; {@code null} when there are none. */
    private final java.util.concurrent.atomic.AtomicReference<List<Breakpoint>> pendingBreakpoints =
            new java.util.concurrent.atomic.AtomicReference<>();
    /** The general-purpose registers the panel shows, as "-data-list-register-values" register numbers. */
    private volatile String generalRegisterNumbers = "";
    private volatile boolean wide = true;

    private static final List<String> GENERAL_64 = List.of("RAX", "RBX", "RCX", "RDX", "RSI", "RDI", "RBP", "RSP",
            "R8", "R9", "R10", "R11", "R12", "R13", "R14", "R15", "RIP", "EFLAGS", "CS", "SS", "DS", "ES", "FS", "GS");
    private static final List<String> GENERAL_32 = List.of("EAX", "EBX", "ECX", "EDX", "ESI", "EDI", "EBP", "ESP",
            "EIP", "EFLAGS", "CS", "SS", "DS", "ES", "FS", "GS");
    private static final Pattern MEMORY_OPERAND =
            Pattern.compile("(?i)^(?:(byte|word|dword|qword)\\s*(?:ptr\\s*)?)?\\[(.+)]$");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_.?@][A-Za-z0-9_.?@$]*");
    private static final Pattern NASM_HEX = Pattern.compile("\\b([0-9][0-9A-Fa-f]*)[hH]\\b");
    private static final String EXEC_ARGUMENTS = "-exec-arguments";
    static final String NO_INPUT = "< /dev/null";
    /** The longest an x86 instruction can be, so a byte range is wide enough for the instructions asked for. */
    private static final int MAX_INSTRUCTION_BYTES = 15;

    public GdbProcessDebugSession(Process process, WindowsJob job, Consumer<DebugEvent> events, long startNanos) {
        this(process.getOutputStream(), process.getInputStream(), process, job, events, startNanos);
    }

    GdbProcessDebugSession(OutputStream out, InputStream in, Process process, WindowsJob job, Consumer<DebugEvent> events, long startNanos) {
        this.process = process;
        this.job = job;
        this.events = events != null ? events : e -> {};
        this.startNanos = startNanos;

        this.writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

        this.readerThread = new Thread(this::readLoop, "GdbMi-Reader");
        this.readerThread.setDaemon(true);
        this.readerThread.start();
    }

    public void initialize(DebugLaunchSpec spec) {
        try {
            // Set pagination off
            sendCommand("-gdb-set pagination off").get(2, TimeUnit.SECONDS);
            // Asynchronous mode is what makes Pause possible: in GDB's default synchronous mode "-exec-interrupt"
            // gets no reply at all while the program runs, because GDB waits for the target and never reads the
            // command (spike S9, GDB 17.2 on Windows, with and without its own console).
            sendCommand("-gdb-set mi-async on").get(2, TimeUnit.SECONDS);
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
            if (windows) {
                // The program gets its own console window, as in Run: GDB's pipes carry MI, not the program's text.
                sendCommand("-gdb-set new-console on").get(2, TimeUnit.SECONDS);
            }

            // Load executable and symbols
            Path exe = spec.executable().toAbsolutePath().normalize();
            String exeEscaped = exe.toString().replace('\\', '/');
            sendCommand("-file-exec-and-symbols \"" + exeEscaped + "\"").get(5, TimeUnit.SECONDS);

            // Set arguments if specified. Each one is a quoted MI string, so an argument with spaces stays one
            // argument; a line break would start a new MI command, so such arguments are refused before this point
            // (RunSettings) and never sent.
            var arguments = new StringBuilder(EXEC_ARGUMENTS);
            if (spec.args().stream().noneMatch(GdbProcessDebugSession::hasLineBreak)) {
                for (String argument : spec.args()) {
                    arguments.append(' ').append(miString(argument.contains(" ") ? "\"" + argument + "\"" : argument));
                }
            }
            if (!windows) {
                // Outside Windows the program would share GDB's input, which carries the IDE's commands: a program
                // reading the keyboard would swallow them and the session would hang. It reads an empty input
                // instead; GDB starts it through a shell, which applies the redirection.
                arguments.append(' ').append(NO_INPUT);
            }
            if (arguments.length() > EXEC_ARGUMENTS.length()) {
                sendCommand(arguments.toString()).get(2, TimeUnit.SECONDS);
            }

            // Query register names to build index map
            GdbMiRecord regNamesRecord = sendCommand("-data-list-register-names").get(3, TimeUnit.SECONDS);
            if (regNamesRecord != null && regNamesRecord.isDone()) {
                List<Object> namesList = regNamesRecord.getList("register-names");
                if (namesList != null) {
                    for (int i = 0; i < namesList.size(); i++) {
                        Object obj = namesList.get(i);
                        if (obj instanceof String s && !s.isBlank()) {
                            registerNames.put(i, s.toUpperCase(Locale.ROOT));
                        }
                    }
                }
            }
            selectGeneralRegisters();

            // Set breakpoints
            boolean hasEnabledBreakpoints = false;
            for (Breakpoint bp : spec.breakpoints()) {
                if (bp.enabled()) {
                    hasEnabledBreakpoints = true;
                    String target = bp.path() + ":" + bp.line();
                    if (hasLineBreak(target)) {
                        continue;
                    }
                    // Quoted as one MI string: "src/my file.asm:12" (verified with GDB 17.2); unquoted, a space
                    // split the location and the breakpoint was never set.
                    insertBreakpoint(target);
                }
            }

            // Without breakpoints, stop at the entry label; 32-bit Windows programs spell it _main.
            if (!hasEnabledBreakpoints) {
                GdbMiRecord entry = sendCommand("-break-insert -t main").get(2, TimeUnit.SECONDS);
                if (entry == null || !entry.isDone()) {
                    sendCommand("-break-insert -t _main").get(2, TimeUnit.SECONDS);
                }
            }

            // Start program execution
            sendCommand("-exec-run");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failToStart("interrupted");
        } catch (Exception e) {
            failToStart(e.getMessage());
        }
    }

    /**
     * Ends a session GDB could not start: without it the program never runs, the session never ends, and the IDE
     * would wait on it until the user pressed Stop.
     */
    private void failToStart(String reason) {
        emit(new DebugEvent.Output("Failed initializing GDB session: " + reason + "\n"));
        state = SessionState.EXITED;
        exitFuture.complete(ExitInfo.error(1, Duration.ofNanos(System.nanoTime() - startNanos),
                "GDB could not start the program: " + reason));
        close();
    }

    /**
     * Inserts one breakpoint and remembers its number, so {@link #setBreakpoints(List)} can replace it later.
     * Quoted as one MI string: "src/my file.asm:12" (verified with GDB 17.2); unquoted, a space split the
     * location and the breakpoint was never set.
     */
    private void insertBreakpoint(String target) {
        try {
            GdbMiRecord inserted = sendCommand("-break-insert " + miString(target)).get(2, TimeUnit.SECONDS);
            if (inserted == null || !inserted.isDone()) {
                emit(new DebugEvent.Output("Breakpoint " + target
                        + " could not be set: the line has no code, or the program was built without debug information.\n"));
                return;
            }
            Map<String, Object> bkpt = inserted.getMap("bkpt");
            Object number = (bkpt == null) ? null : bkpt.get("number");
            if (number != null) {
                breakpointNumbers.add(number.toString());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException failed) {
            emit(new DebugEvent.Output("Breakpoint " + target + " could not be set: " + failed.getMessage() + "\n"));
        }
    }

    /**
     * Replaces the breakpoints of a running session. GDB only accepts this while the program is stopped, so a set
     * changed while the program runs is kept and applied at the next stop. Each breakpoint waits for GDB's answer,
     * which is why this happens on the session's own thread and never on the caller's (the JavaFX thread).
     */
    @Override
    public void setBreakpoints(List<Breakpoint> updated) {
        if (closed) return;
        pendingBreakpoints.set(updated == null ? List.of() : List.copyOf(updated));
        if (paused.get()) {
            applyPendingBreakpoints();
        }
    }

    private void applyPendingBreakpoints() {
        try {
            breakpointThread.execute(() -> {
                if (closed || !paused.get()) {
                    return; // Still pending; the next stop applies it.
                }
                List<Breakpoint> wanted = pendingBreakpoints.getAndSet(null);
                if (wanted == null) {
                    return;
                }
                if (!breakpointNumbers.isEmpty()) {
                    sendCommand("-break-delete " + String.join(" ", breakpointNumbers));
                    breakpointNumbers.clear();
                }
                for (Breakpoint bp : wanted) {
                    if (!bp.enabled()) continue;
                    String target = bp.path() + ":" + bp.line();
                    if (!hasLineBreak(target)) {
                        insertBreakpoint(target);
                    }
                }
            });
        } catch (RejectedExecutionException closedSession) {
            // The session is closed; there is nothing to apply the breakpoints to.
        }
    }

    /** A GDB/MI c-string: quoted, with backslashes and quotes escaped. */
    static String miString(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean hasLineBreak(String text) {
        return text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
    }

    public CompletableFuture<GdbMiRecord> sendCommand(String command) {
        long token = tokenSequence.getAndIncrement();
        CompletableFuture<GdbMiRecord> future = new CompletableFuture<>();
        pendingCommands.put(token, future);
        try {
            synchronized (writer) {
                writer.write(token + command + "\n");
                writer.flush();
            }
        } catch (IOException e) {
            pendingCommands.remove(token);
            future.completeExceptionally(e);
        }
        // Checked after the command is registered, so a command sent as GDB ends is failed here or by readLoop.
        if (gdbEnded && pendingCommands.remove(token) != null) {
            future.completeExceptionally(new IOException("GDB has ended"));
        }
        return future;
    }

    private void emit(DebugEvent event) {
        try {
            eventThread.execute(() -> events.accept(event));
        } catch (RejectedExecutionException closedSession) {
            // The session is closed; nobody listens any more.
        }
    }

    /** Waits until every event emitted so far has been delivered. */
    void awaitEvents() {
        try {
            eventThread.submit(() -> { }).get(5, TimeUnit.SECONDS);
        } catch (Exception closedOrInterrupted) {
            if (closedOrInterrupted instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void readLoop() {
        try {
            String line;
            while (!closed && (line = reader.readLine()) != null) {
                GdbMiRecord record = GdbMiParser.parse(line);
                if (record == null) continue;
                handleRecord(record);
            }
        } catch (IOException ignored) {
        } finally {
            // GDB's output ended. Commands still waiting for an answer will never get one.
            gdbEnded = true;
            var ended = new IOException("GDB ended before answering");
            for (Long token : List.copyOf(pendingCommands.keySet())) {
                CompletableFuture<GdbMiRecord> pending = pendingCommands.remove(token);
                if (pending != null) {
                    pending.completeExceptionally(ended);
                }
            }
            Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
            if (closed || stopping) {
                exitFuture.complete(ExitInfo.stopped(duration));
            } else if (!exitFuture.isDone()) {
                // Nobody closed the session: GDB itself ended or crashed while the program was being debugged.
                state = SessionState.EXITED;
                paused.set(false);
                exitFuture.complete(ExitInfo.error(1, duration, "GDB ended unexpectedly."));
            }
        }
    }

    void handleRecord(GdbMiRecord record) {
        // Complete any pending command with matching token
        if (record.token() != null) {
            CompletableFuture<GdbMiRecord> pending = pendingCommands.remove(record.token());
            if (pending != null) {
                pending.complete(record);
            }
        }

        // A line that is not MI is the program's own output: outside Windows the program has no console window of
        // its own (new-console exists only there) and writes to the terminal GDB shares with it.
        if (record.type() == GdbMiRecord.Type.UNKNOWN && record.token() == null && !record.raw().isBlank()) {
            emit(new DebugEvent.Output(record.raw() + "\n"));
            return;
        }

        // Stream output
        if (record.type() == GdbMiRecord.Type.CONSOLE_STREAM || record.type() == GdbMiRecord.Type.TARGET_STREAM) {
            if (!record.streamMessage().isEmpty()) {
                emit(new DebugEvent.Output(record.streamMessage()));
            }
            return;
        }

        // Execution status changes
        if (record.type() == GdbMiRecord.Type.EXEC_ASYNC) {
            if (record.isStopped()) {
                String reason = record.getString("reason");
                if ("exited-normally".equalsIgnoreCase(reason) || "exited".equalsIgnoreCase(reason) || "exited-signalled".equalsIgnoreCase(reason)) {
                    paused.set(false);
                    int code = 0;
                    String exitCode = record.getString("exit-code");
                    if (exitCode != null && !exitCode.isBlank()) {
                        // GDB/MI prints the exit code in octal.
                        try {
                            code = Integer.parseInt(exitCode.strip(), 8);
                        } catch (NumberFormatException notOctal) {
                            code = 1;
                        }
                    }
                    // A program that ends with a non-zero code still ended by itself; only Stop means stopped.
                    state = SessionState.EXITED;
                    Duration dur = Duration.ofNanos(System.nanoTime() - startNanos);
                    ExitInfo info = (code == 0) ? ExitInfo.success(dur) : ExitInfo.error(code, dur, "Process exited with code " + code);
                    exitFuture.complete(info);
                    emit(new DebugEvent.Exited(info));
                    emit(new DebugEvent.Stopped());
                } else {
                    paused.set(true);
                    instructionsExecuted.incrementAndGet();
                    if (pendingBreakpoints.get() != null) {
                        applyPendingBreakpoints();
                    }
                    refreshRegistersAndNotify(record);
                }
            } else if (record.isRunning()) {
                paused.set(false);
                state = SessionState.RUNNING;
                emit(new DebugEvent.Resumed());
            }
        }
    }

    /**
     * GDB also lists FPU, vector and pseudo registers (AL, R10D...); the panel shows the general-purpose ones,
     * in the order a student reads them.
     */
    private void selectGeneralRegisters() {
        wide = registerNames.containsValue("RAX");
        var numbers = new ArrayList<String>();
        for (String name : wide ? GENERAL_64 : GENERAL_32) {
            registerNames.forEach((number, registered) -> {
                if (registered.equals(name)) {
                    numbers.add(String.valueOf(number));
                }
            });
        }
        generalRegisterNumbers = String.join(" ", numbers);
    }

    /**
     * After a stop, reports where the program is and what its registers hold.
     *
     * <p>An interrupt is served on Windows by a thread the operating system injects, which stands in
     * {@code ntdll!DbgBreakPoint} with no source line (spike S9). Reporting that frame would mark no editor line
     * and show a foreign thread's registers, so the program's own thread is selected first.
     */
    private void refreshRegistersAndNotify(GdbMiRecord stopRecord) {
        if (interruptRequested.compareAndSet(true, false) && !hasSourceLine(stopRecord.getMap("frame"))) {
            selectProgramThread().whenComplete((programFrame, failed) -> reportStop(stopRecord, programFrame));
            return;
        }
        reportStop(stopRecord, null);
    }

    private static boolean hasSourceLine(Map<String, Object> frame) {
        return frame != null && (frame.get("file") != null || frame.get("fullname") != null);
    }

    /**
     * Selects the first thread that stands in the program's own code and answers with its frame, or with
     * {@code null} when no such thread is listed.
     */
    @SuppressWarnings("unchecked")
    private CompletableFuture<Map<String, Object>> selectProgramThread() {
        return sendCommand("-thread-info").thenCompose(info -> {
            List<Object> threads = (info == null || !info.isDone()) ? null : info.getList("threads");
            if (threads != null) {
                for (Object item : threads) {
                    if (item instanceof Map<?, ?> thread
                            && thread.get("id") != null
                            && thread.get("frame") instanceof Map<?, ?> raw
                            && hasSourceLine((Map<String, Object>) raw)) {
                        return sendCommand("-thread-select " + thread.get("id"))
                                .thenApply(selected -> (Map<String, Object>) raw);
                    }
                }
            }
            return CompletableFuture.completedFuture((Map<String, Object>) null);
        }).exceptionally(error -> null);
    }

    @SuppressWarnings("unchecked")
    private void reportStop(GdbMiRecord stopRecord, Map<String, Object> overrideFrame) {
        String numbers = generalRegisterNumbers;
        sendCommand("-data-list-register-values x" + (numbers.isEmpty() ? "" : " " + numbers)).thenAccept(result -> {
            if (result != null && result.isDone()) {
                List<Object> regVals = result.getList("register-values");
                if (regVals != null) {
                    Map<String, Long> extended = new LinkedHashMap<>();
                    for (Object item : regVals) {
                        if (item instanceof Map<?, ?> m) {
                            Object numObj = m.get("number");
                            Object valObj = m.get("value");
                            if (numObj != null && valObj != null) {
                                try {
                                    int num = Integer.parseInt(numObj.toString());
                                    String name = registerNames.get(num);
                                    if (name != null) {
                                        String valStr = valObj.toString().trim();
                                        long val;
                                        if (valStr.startsWith("0x") || valStr.startsWith("0X")) {
                                            val = Long.parseUnsignedLong(valStr.substring(2), 16);
                                        } else {
                                            val = Long.parseLong(valStr);
                                        }
                                        extended.put(name, val);
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                    Map<String, Long> ordered = new LinkedHashMap<>();
                    for (String name : wide ? GENERAL_64 : GENERAL_32) {
                        if (extended.containsKey(name)) {
                            ordered.put(name, extended.get(name));
                        }
                    }
                    currentRegisters = RegisterState.fromExtended(ordered.isEmpty() ? extended : ordered);
                }
            }

            Map<String, Object> frame = (overrideFrame != null) ? overrideFrame : stopRecord.getMap("frame");
            // Without line information the registers still update, but no editor line is marked.
            String file = "";
            int line = 0;
            if (frame != null) {
                if (frame.get("fullname") != null) {
                    file = frame.get("fullname").toString();
                } else if (frame.get("file") != null) {
                    file = frame.get("file").toString();
                }
                if (frame.get("line") != null) {
                    try {
                        line = Integer.parseInt(frame.get("line").toString());
                    } catch (NumberFormatException ignored) {}
                }
            }
            emit(new DebugEvent.Paused(file, line, currentRegisters));
        }).exceptionally(err -> null);
    }

    @Override
    public CompletableFuture<ExitInfo> exit() {
        return exitFuture;
    }

    @Override
    public SessionState state() {
        return state;
    }

    /**
     * GDB drives the program itself, so the workbench panels all work. Pause is included because spike S9 proved
     * {@code -exec-interrupt} works once {@code mi-async} is on. Keystrokes are not: on Windows the program owns
     * its console and elsewhere its input is redirected away from GDB's pipes.
     */
    @Override
    public Set<DebugCapability> capabilities() {
        return EnumSet.of(DebugCapability.STEP, DebugCapability.PAUSE, DebugCapability.BREAKPOINTS,
                DebugCapability.REGISTERS, DebugCapability.MEMORY, DebugCapability.WATCHES,
                DebugCapability.CALL_STACK, DebugCapability.DISASSEMBLY);
    }

    /**
     * The instructions from the program counter onwards. The range is asked for in bytes, so it is generous enough
     * for {@code count} instructions of any length and then trimmed.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<DisasmLine> disassemble(int count) {
        if (count <= 0 || !paused.get()) {
            return List.of();
        }
        try {
            GdbMiRecord result = sendCommand("-data-disassemble -s $pc -e $pc+" + (count * MAX_INSTRUCTION_BYTES)
                    + " -- 0").get(500, TimeUnit.MILLISECONDS);
            List<Object> instructions = (result == null || !result.isDone()) ? null : result.getList("asm_insns");
            if (instructions == null) {
                return List.of();
            }
            var lines = new ArrayList<DisasmLine>();
            for (Object item : instructions) {
                if (lines.size() >= count || !(item instanceof Map<?, ?> instruction)) {
                    break;
                }
                Object address = instruction.get("address");
                Object text = instruction.get("inst");
                if (address == null || text == null) {
                    continue;
                }
                Object opcodes = instruction.get("opcodes");
                lines.add(new DisasmLine(address.toString(), opcodes == null ? "" : opcodes.toString(),
                        text.toString(), lines.isEmpty()));
            }
            return lines;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (ExecutionException | TimeoutException unavailable) {
            return List.of();
        }
    }

    public boolean isPaused() {
        return paused.get();
    }

    /** Interrupts a running program so an endless loop can be inspected. */
    @Override
    public void pause() {
        if (closed || paused.get()) return;
        interruptRequested.set(true);
        sendCommand("-exec-interrupt");
    }

    @Override
    public void stop() {
        if (closed) return;
        // Set first: GDB may end its output after -gdb-exit before close() runs, and that end is not a crash.
        stopping = true;
        state = SessionState.STOPPED;
        try {
            if (!paused.get()) {
                sendCommand("-exec-interrupt");
            }
            sendCommand("-gdb-exit");
        } catch (Exception ignored) {}
        close();
    }

    @Override
    public void resume() {
        if (paused.get()) {
            paused.set(false);
            state = SessionState.RUNNING;
            sendCommand("-exec-continue");
            emit(new DebugEvent.Resumed());
        }
    }

    @Override
    public void stepOver() {
        if (paused.get()) {
            paused.set(false);
            state = SessionState.RUNNING;
            sendCommand("-exec-next");
            emit(new DebugEvent.Resumed());
        }
    }

    @Override
    public void stepInto() {
        if (paused.get()) {
            paused.set(false);
            state = SessionState.RUNNING;
            sendCommand("-exec-step");
            emit(new DebugEvent.Resumed());
        }
    }

    @Override
    public void stepOut() {
        if (paused.get()) {
            paused.set(false);
            state = SessionState.RUNNING;
            sendCommand("-exec-finish");
            emit(new DebugEvent.Resumed());
        }
    }

    @Override
    public long instructionsExecuted() {
        return instructionsExecuted.get();
    }

    @Override
    public RegisterState registers() {
        return currentRegisters;
    }

    @Override
    public MemoryView readMemory(int segment, int offset, int length) {
        long address = (segment == 0) ? (offset & 0xFFFFFFFFL) : (((long) segment << 32) | (offset & 0xFFFFFFFFL));
        return readMemory(address, length);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MemoryView readMemory(long address, int length) {
        String cmd = String.format("-data-read-memory-bytes 0x%x %d", address, Math.max(1, length));
        try {
            GdbMiRecord res = sendCommand(cmd).get(500, TimeUnit.MILLISECONDS);
            if (res != null && res.isDone()) {
                List<Object> memList = res.getList("memory");
                if (memList != null && !memList.isEmpty() && memList.get(0) instanceof Map<?, ?> map) {
                    Object contents = map.get("contents");
                    if (contents instanceof String hex) {
                        return MemoryView.flat(address, parseHexBytes(hex));
                    }
                }
            }
        } catch (Exception ignored) {}
        // Unreadable memory (an unmapped address) shows as empty rather than as zeros.
        return MemoryView.flat(address, new byte[0]);
    }

    private static byte[] parseHexBytes(String hex) {
        int len = hex.length() / 2;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    @Override
    public List<StackFrame> stack(int depth) {
        return List.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CallFrame> callStack(int depth) {
        int max = Math.max(1, depth);
        String cmd = String.format("-stack-list-frames 0 %d", max - 1);
        try {
            GdbMiRecord res = sendCommand(cmd).get(500, TimeUnit.MILLISECONDS);
            if (res != null && res.isDone()) {
                List<Object> rawList = res.getList("stack");
                if (rawList != null) {
                    List<CallFrame> frames = new ArrayList<>();
                    for (Object item : rawList) {
                        if (item instanceof Map<?, ?> m) {
                            Map<?, ?> frameMap = (m.containsKey("frame") && m.get("frame") instanceof Map<?, ?> fm)
                                    ? fm : m;
                            int level = 0;
                            if (frameMap.get("level") != null) {
                                try {
                                    level = Integer.parseInt(frameMap.get("level").toString());
                                } catch (Exception ignored) {}
                            }
                            String addr = frameMap.get("addr") != null ? frameMap.get("addr").toString() : "";
                            String func = frameMap.get("func") != null ? frameMap.get("func").toString() : "??";
                            String file = "";
                            if (frameMap.get("fullname") != null) {
                                file = frameMap.get("fullname").toString();
                            } else if (frameMap.get("file") != null) {
                                file = frameMap.get("file").toString();
                            }
                            int line = 0;
                            if (frameMap.get("line") != null) {
                                try {
                                    line = Integer.parseInt(frameMap.get("line").toString());
                                } catch (Exception ignored) {}
                            }
                            frames.add(new CallFrame(level, addr, func, file, line));
                        }
                    }
                    return Collections.unmodifiableList(frames);
                }
            }
        } catch (Exception ignored) {}
        return List.of();
    }

    @Override
    public Optional<String> evaluateExpression(String expr) {
        if (expr == null || expr.isBlank()) return Optional.empty();
        String gdbExpression = toGdbExpression(expr, Set.copyOf(registerNames.values()), wide);
        String cleanExpr = gdbExpression.replace("\\", "\\\\").replace("\"", "\\\"");
        try {
            GdbMiRecord res = sendCommand("-data-evaluate-expression \"" + cleanExpr + "\"").get(500, TimeUnit.MILLISECONDS);
            if (res != null && res.isDone()) {
                String val = res.getString("value");
                if (val != null) {
                    return Optional.of(withHex(val));
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    /**
     * Watches are written as in the Assembly source: {@code RAX}, {@code [RSP+8]}, {@code dword [count]} or a label.
     * GDB wants {@code $rax}, a typed dereference, and an address for a label, since NASM labels carry no type.
     * Anything else is passed to GDB unchanged.
     */
    static String toGdbExpression(String expression, Set<String> registers, boolean wide) {
        String text = expression.strip();
        Matcher memory = MEMORY_OPERAND.matcher(text);
        if (memory.matches()) {
            String size = memory.group(1) == null ? (wide ? "qword" : "dword") : memory.group(1).toLowerCase(Locale.ROOT);
            String type = switch (size) {
                case "byte" -> "unsigned char";
                case "word" -> "unsigned short";
                case "dword" -> "unsigned int";
                default -> "unsigned long long";
            };
            return "*(" + type + " *)(" + operand(memory.group(2), registers) + ")";
        }
        // Parentheses, casts, $ and quotes mean the user already wrote GDB syntax.
        if (text.chars().anyMatch(c -> "()$&*\"'".indexOf(c) >= 0)) {
            return text;
        }
        return operand(text, registers);
    }

    private static String operand(String text, Set<String> registers) {
        String hex = NASM_HEX.matcher(text).replaceAll("0x$1");
        Matcher name = NAME.matcher(hex);
        var result = new StringBuilder();
        while (name.find()) {
            String word = name.group();
            char before = name.start() > 0 ? hex.charAt(name.start() - 1) : ' ';
            String replacement;
            if (Character.isDigit(before) || before == '$') {
                // The x of 0x10, or a register already written as $rax.
                replacement = word;
            } else if (registers.contains(word.toUpperCase(Locale.ROOT))) {
                replacement = "$" + word.toLowerCase(Locale.ROOT);
            } else {
                // A label stands for its address, as in NASM.
                replacement = "((unsigned long long)&" + word + ")";
            }
            name.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        name.appendTail(result);
        return result.toString();
    }

    /** Whole numbers are also shown in hex, the base Assembly programs are read in. */
    private static String withHex(String value) {
        String text = value.strip();
        if (text.matches("\\d+")) {
            try {
                return text + " (0x" + Long.toHexString(Long.parseUnsignedLong(text)).toUpperCase(Locale.ROOT) + ")";
            } catch (NumberFormatException tooLarge) {
                return text;
            }
        }
        return text;
    }

    @Override
    public Optional<Long> evaluateVariable(String filePath, int line, int byteSize) {
        return Optional.empty();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        breakpointThread.shutdownNow();
        try {
            writer.close();
        } catch (IOException ignored) {}
        if (job != null) {
            job.close();
        }
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ie) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        // The reader thread holds the reader's lock inside readLine() until GDB's output ends, so closing the reader
        // here could wait for it; it is closed on a thread of its own and close() never blocks.
        Thread.ofPlatform().daemon().name("GdbMi-Close").start(() -> {
            try {
                reader.close();
            } catch (IOException ignored) {}
        });
        if (!exitFuture.isDone()) {
            exitFuture.complete(ExitInfo.stopped(Duration.ofNanos(System.nanoTime() - startNanos)));
        }
        // Events already emitted, such as the exit, are still delivered.
        eventThread.shutdown();
    }
}
