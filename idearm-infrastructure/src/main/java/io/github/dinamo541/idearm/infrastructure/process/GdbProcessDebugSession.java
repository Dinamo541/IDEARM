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

    private final Thread readerThread;
    private final ExecutorService eventThread = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "GdbMi-Events");
        thread.setDaemon(true);
        return thread;
    });
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
                    GdbMiRecord inserted = sendCommand("-break-insert " + miString(target)).get(2, TimeUnit.SECONDS);
                    if (inserted == null || !inserted.isDone()) {
                        emit(new DebugEvent.Output("Breakpoint " + target
                                + " could not be set: the line has no code, or the program was built without debug information.\n"));
                    }
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
        } catch (Exception e) {
            emit(new DebugEvent.Output("Failed initializing GDB session: " + e.getMessage() + "\n"));
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
            if (!exitFuture.isDone()) {
                exitFuture.complete(ExitInfo.stopped(Duration.ofNanos(System.nanoTime() - startNanos)));
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

    @SuppressWarnings("unchecked")
    private void refreshRegistersAndNotify(GdbMiRecord stopRecord) {
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

            Map<String, Object> frame = stopRecord.getMap("frame");
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

    public boolean isPaused() {
        return paused.get();
    }

    @Override
    public void stop() {
        if (closed) return;
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
        try {
            writer.close();
        } catch (IOException ignored) {}
        try {
            reader.close();
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
        if (!exitFuture.isDone()) {
            exitFuture.complete(ExitInfo.stopped(Duration.ofNanos(System.nanoTime() - startNanos)));
        }
        // Events already emitted, such as the exit, are still delivered.
        eventThread.shutdown();
    }
}
