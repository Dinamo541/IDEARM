package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.application.editor.QueryHover;
import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.CallFrame;
import io.github.dinamo541.idearm.domain.debug.DebugCapability;
import io.github.dinamo541.idearm.domain.debug.DisasmLine;
import io.github.dinamo541.idearm.domain.debug.MemoryView;
import io.github.dinamo541.idearm.domain.debug.RegisterState;
import io.github.dinamo541.idearm.domain.debug.StackFrame;
import io.github.dinamo541.idearm.domain.port.DebugSession;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * ViewModel managing the visual debugger state (registers, memory dump, stack, breakpoints).
 *
 * <p>A DOS program shows the 8086 registers and a {@code segment:offset} memory address; a 32/64-bit program
 * shows its own general-purpose registers and a flat address, which may also be a register name such as RSP.
 *
 * <p>Debug events arrive on the debugger's threads. Questions to the session (memory, call stack, watches) are
 * asked there or on a background thread, and the answers are applied on the JavaFX thread.
 */
public final class DebugViewModel {

    private static final List<String> DOS_REGISTERS = List.of(
            "AX", "BX", "CX", "DX", "SI", "DI", "BP", "SP", "CS", "DS", "ES", "SS", "IP", "FLAGS");
    private static final List<String> SEGMENT_REGISTERS = List.of("CS", "DS", "ES", "SS", "FS", "GS");
    private static final System.Logger LOG = System.getLogger(DebugViewModel.class.getName());
    private static final String DOS_SEGMENT = "0710";
    private static final String DOS_OFFSET = "0000";
    private static final int MEMORY_BYTES = 64;
    /** Enough instructions to see what the current source line became, and its neighbours. */
    private static final int DISASSEMBLY_LINES = 24;

    private final BooleanProperty active = new SimpleBooleanProperty(false);
    private final BooleanProperty paused = new SimpleBooleanProperty(false);
    private final BooleanProperty nativeSession = new SimpleBooleanProperty(false);
    private final LongProperty instructionsExecuted = new SimpleLongProperty(0);

    private final ObservableList<BreakpointItemViewModel> breakpoints = FXCollections.observableArrayList();

    private final ObservableList<RegisterItemViewModel> registerList = FXCollections.observableArrayList();
    private final Map<String, RegisterItemViewModel> registerMap = new LinkedHashMap<>();

    // Individual flag indicator properties
    private final BooleanProperty cf = new SimpleBooleanProperty(false);
    private final BooleanProperty zf = new SimpleBooleanProperty(false);
    private final BooleanProperty sf = new SimpleBooleanProperty(false);
    private final BooleanProperty of = new SimpleBooleanProperty(false);
    private final BooleanProperty pf = new SimpleBooleanProperty(false);
    private final BooleanProperty af = new SimpleBooleanProperty(false);
    private final BooleanProperty ifFlag = new SimpleBooleanProperty(true);
    private final BooleanProperty df = new SimpleBooleanProperty(false);

    // Flag changed indicators
    private final BooleanProperty cfChanged = new SimpleBooleanProperty(false);
    private final BooleanProperty zfChanged = new SimpleBooleanProperty(false);
    private final BooleanProperty sfChanged = new SimpleBooleanProperty(false);
    private final BooleanProperty ofChanged = new SimpleBooleanProperty(false);
    private final BooleanProperty pfChanged = new SimpleBooleanProperty(false);
    private final BooleanProperty afChanged = new SimpleBooleanProperty(false);
    private final BooleanProperty ifFlagChanged = new SimpleBooleanProperty(false);
    private final BooleanProperty dfChanged = new SimpleBooleanProperty(false);

    private RegisterState previousState = null;
    /** The latest registers, readable from any thread to resolve a register name in the memory address. */
    private volatile RegisterState latestState = null;

    // Memory view properties
    private final StringProperty memorySegment = new SimpleStringProperty(DOS_SEGMENT);
    private final StringProperty memoryOffset = new SimpleStringProperty(DOS_OFFSET);
    private final StringProperty memoryDumpText = new SimpleStringProperty("");

    private final ObservableList<CallFrameItemViewModel> stackLines = FXCollections.observableArrayList();
    private final ObservableList<DisasmLine> disassembly = FXCollections.observableArrayList();
    private final ObservableList<WatchItemViewModel> watches = FXCollections.observableArrayList();

    // Copies of the memory address and the watch list for the background refresh: JavaFX properties and lists may
    // only be read on the JavaFX thread, where they are changed, and copying the list elsewhere could fail mid-edit.
    private volatile String memorySegmentValue = DOS_SEGMENT;
    private volatile String memoryOffsetValue = DOS_OFFSET;
    private volatile List<WatchItemViewModel> watchSnapshot = List.of();
    /** Counts memory refreshes, so a slow one that finishes after a newer one does not overwrite it. */
    private final java.util.concurrent.atomic.AtomicLong memoryRefreshes = new java.util.concurrent.atomic.AtomicLong();
    private final Map<String, Optional<String>> registerHelp = new java.util.concurrent.ConcurrentHashMap<>();

    {
        memorySegment.addListener((observable, before, now) -> memorySegmentValue = now);
        memoryOffset.addListener((observable, before, now) -> memoryOffsetValue = now);
        watches.addListener((javafx.collections.ListChangeListener<WatchItemViewModel>) change ->
                watchSnapshot = List.copyOf(watches));
    }

    // What the attached session can do. Everything a launch-only debugger cannot do stays switched off, so the
    // workbench hides those controls instead of offering buttons that would silently do nothing.
    private final BooleanProperty canStep = new SimpleBooleanProperty(false);
    private final BooleanProperty canPause = new SimpleBooleanProperty(false);
    private final BooleanProperty canInspect = new SimpleBooleanProperty(false);
    private final BooleanProperty canReadMemory = new SimpleBooleanProperty(false);
    private final BooleanProperty canWatch = new SimpleBooleanProperty(false);
    private final BooleanProperty canShowCallStack = new SimpleBooleanProperty(false);
    private final BooleanProperty canSendInput = new SimpleBooleanProperty(false);
    /** True while the attached session drives the program itself, which is what the panels need. */
    private final BooleanProperty integrated = new SimpleBooleanProperty(false);

    private volatile DebugSession activeSession;
    private volatile BiConsumer<Breakpoint, Boolean> onBreakpointEnabledChanged = (breakpoint, enabled) -> {};

    public DebugViewModel() {
        showRegisters(DOS_REGISTERS);
    }

    private void showRegisters(List<String> names) {
        registerMap.clear();
        var rows = new ArrayList<RegisterItemViewModel>();
        for (String name : names) {
            var row = new RegisterItemViewModel(name);
            registerMap.put(name, row);
            rows.add(row);
        }
        registerList.setAll(rows);
    }

    public void updateRegisters(RegisterState state) {
        if (state == null) return;
        latestState = state;
        FxDispatch.run(() -> applyRegisters(state));
    }

    private void applyRegisters(RegisterState state) {
        Map<String, Long> extended = state.extended();
        if (!extended.isEmpty()) {
            if (!nativeSession.get() || !registerMap.keySet().equals(extended.keySet())) {
                boolean firstNativeStop = !nativeSession.get();
                showRegisters(List.copyOf(extended.keySet()));
                nativeSession.set(true);
                if (firstNativeStop) {
                    // A flat address space: the stack is the useful first view.
                    memorySegment.set("");
                    memoryOffset.set(extended.containsKey("RSP") ? "RSP" : "ESP");
                }
            }
            for (var entry : extended.entrySet()) {
                registerMap.get(entry.getKey()).setValue(entry.getValue(), bitWidth(entry.getKey()));
            }
        } else {
            if (nativeSession.get()) {
                showRegisters(DOS_REGISTERS);
                nativeSession.set(false);
                memorySegment.set(DOS_SEGMENT);
                memoryOffset.set(DOS_OFFSET);
            }
            if (previousState == null && DOS_SEGMENT.equals(memorySegment.get())) {
                // The program's data segment is where its variables are.
                memorySegment.set(String.format("%04X", state.ds()));
            }
            setReg("AX", state.ax());
            setReg("BX", state.bx());
            setReg("CX", state.cx());
            setReg("DX", state.dx());
            setReg("SI", state.si());
            setReg("DI", state.di());
            setReg("BP", state.bp());
            setReg("SP", state.sp());
            setReg("CS", state.cs());
            setReg("DS", state.ds());
            setReg("ES", state.es());
            setReg("SS", state.ss());
            setReg("IP", state.ip());
            setReg("FLAGS", state.flags());
        }

        boolean compare = previousState != null;
        cfChanged.set(compare && state.cf() != previousState.cf());
        zfChanged.set(compare && state.zf() != previousState.zf());
        sfChanged.set(compare && state.sf() != previousState.sf());
        ofChanged.set(compare && state.of() != previousState.of());
        pfChanged.set(compare && state.pf() != previousState.pf());
        afChanged.set(compare && state.af() != previousState.af());
        ifFlagChanged.set(compare && state.ifFlag() != previousState.ifFlag());
        dfChanged.set(compare && state.df() != previousState.df());

        cf.set(state.cf());
        zf.set(state.zf());
        sf.set(state.sf());
        of.set(state.of());
        pf.set(state.pf());
        af.set(state.af());
        ifFlag.set(state.ifFlag());
        df.set(state.df());

        previousState = state;
    }

    /** RAX..R15 and RIP are 64-bit, segment registers 16-bit, the rest (EAX..., EFLAGS) 32-bit. */
    private static int bitWidth(String register) {
        if (SEGMENT_REGISTERS.contains(register)) {
            return 16;
        }
        return register.startsWith("R") ? 64 : 32;
    }

    private void setReg(String name, int value) {
        var item = registerMap.get(name);
        if (item != null) {
            item.setValue(value);
        }
    }

    public void setBreakpoints(List<Breakpoint> list) {
        FxDispatch.run(() -> {
            var rows = new ArrayList<BreakpointItemViewModel>();
            if (list != null) {
                for (Breakpoint bp : list) {
                    var row = new BreakpointItemViewModel(bp);
                    // Ticking the box in the panel is a real change, not only a mark: it is carried on so the
                    // breakpoint is stored and a running session is told about it.
                    row.enabledProperty().addListener((observable, was, now) -> {
                        if (!Objects.equals(was, now)) {
                            onBreakpointEnabledChanged.accept(bp, Boolean.TRUE.equals(now));
                        }
                    });
                    rows.add(row);
                }
            }
            breakpoints.setAll(rows);
        });
    }

    /** Receives a breakpoint the user enabled or disabled in the panel. */
    public void setOnBreakpointEnabledChanged(BiConsumer<Breakpoint, Boolean> handler) {
        this.onBreakpointEnabledChanged = (handler != null) ? handler : (breakpoint, enabled) -> {};
    }

    public void reset() {
        latestState = null;
        FxDispatch.run(() -> {
            active.set(false);
            paused.set(false);
            previousState = null;
            if (nativeSession.get()) {
                showRegisters(DOS_REGISTERS);
                nativeSession.set(false);
                memorySegment.set(DOS_SEGMENT);
                memoryOffset.set(DOS_OFFSET);
            }
            for (var reg : registerList) {
                reg.setValue(0);
                reg.changedProperty().set(false);
            }
            cf.set(false);
            zf.set(false);
            sf.set(false);
            of.set(false);
            pf.set(false);
            af.set(false);
            ifFlag.set(true);
            df.set(false);
            cfChanged.set(false);
            zfChanged.set(false);
            sfChanged.set(false);
            ofChanged.set(false);
            pfChanged.set(false);
            afChanged.set(false);
            ifFlagChanged.set(false);
            dfChanged.set(false);
            memoryDumpText.set("");
            stackLines.clear();
            disassembly.clear();
            for (var w : watches) {
                w.setValue("");
            }
        });
    }

    public BooleanProperty activeProperty() { return active; }
    public boolean isActive() { return active.get(); }
    public void setActive(boolean val) { FxDispatch.run(() -> active.set(val)); }

    public BooleanProperty pausedProperty() { return paused; }
    public boolean isPaused() { return paused.get(); }
    public void setPaused(boolean val) { FxDispatch.run(() -> paused.set(val)); }

    /** True while a 32/64-bit program is debugged: memory is addressed without a segment. */
    public BooleanProperty nativeSessionProperty() { return nativeSession; }

    public LongProperty instructionsExecutedProperty() { return instructionsExecuted; }
    public void setInstructionsExecuted(long count) { FxDispatch.run(() -> instructionsExecuted.set(count)); }

    public ObservableList<BreakpointItemViewModel> getBreakpoints() { return breakpoints; }
    public ObservableList<RegisterItemViewModel> getRegisterList() { return registerList; }

    public BooleanProperty cfProperty() { return cf; }
    public BooleanProperty zfProperty() { return zf; }
    public BooleanProperty sfProperty() { return sf; }
    public BooleanProperty ofProperty() { return of; }
    public BooleanProperty pfProperty() { return pf; }
    public BooleanProperty afProperty() { return af; }
    public BooleanProperty ifFlagProperty() { return ifFlag; }
    public BooleanProperty dfProperty() { return df; }

    public BooleanProperty cfChangedProperty() { return cfChanged; }
    public BooleanProperty zfChangedProperty() { return zfChanged; }
    public BooleanProperty sfChangedProperty() { return sfChanged; }
    public BooleanProperty ofChangedProperty() { return ofChanged; }
    public BooleanProperty pfChangedProperty() { return pfChanged; }
    public BooleanProperty afChangedProperty() { return afChanged; }
    public BooleanProperty ifFlagChangedProperty() { return ifFlagChanged; }
    public BooleanProperty dfChangedProperty() { return dfChanged; }

    public StringProperty memorySegmentProperty() { return memorySegment; }
    public StringProperty memoryOffsetProperty() { return memoryOffset; }
    public StringProperty memoryDumpTextProperty() { return memoryDumpText; }
    public ObservableList<CallFrameItemViewModel> getStackLines() { return stackLines; }
    public ObservableList<DisasmLine> getDisassembly() { return disassembly; }
    public ObservableList<WatchItemViewModel> getWatches() { return watches; }

    public BooleanProperty canStepProperty() { return canStep; }
    public BooleanProperty canPauseProperty() { return canPause; }
    public BooleanProperty canInspectProperty() { return canInspect; }
    public BooleanProperty canReadMemoryProperty() { return canReadMemory; }
    public BooleanProperty canWatchProperty() { return canWatch; }
    public BooleanProperty canShowCallStackProperty() { return canShowCallStack; }
    public BooleanProperty canSendInputProperty() { return canSendInput; }

    /**
     * True while the IDE itself drives the program. False for a launch-only debugger such as Turbo Debugger in a
     * DOSBox window, where the panels have nothing to show and say so instead.
     */
    public BooleanProperty integratedProperty() { return integrated; }

    public void addWatch(String expression) {
        if (expression == null || expression.isBlank()) return;
        String trimmed = expression.trim();
        for (var w : watches) {
            if (w.getExpression().equalsIgnoreCase(trimmed)) {
                evaluateWatchInBackground(w);
                return;
            }
        }
        var item = new WatchItemViewModel(trimmed, "");
        watches.add(item);
        evaluateWatchInBackground(item);
    }

    /** A debugger may take a while to answer (GDB waits up to half a second), which must not freeze the window. */
    private void evaluateWatchInBackground(WatchItemViewModel item) {
        if (javafx.application.Platform.isFxApplicationThread()) {
            CompletableFuture.runAsync(() -> evaluateWatch(item));
        } else {
            evaluateWatch(item);
        }
    }

    /**
     * What a register is for, shown when the pointer rests on its name: the hover card's title and syntax. The
     * table repaints its cells often, so each answer is looked up once per register and language.
     */
    public Optional<String> registerHelp(String register, String language) {
        if (register == null || register.isBlank()) {
            return Optional.empty();
        }
        String key = language + ':' + register.toUpperCase(Locale.ROOT);
        return registerHelp.computeIfAbsent(key, ignored -> new QueryHover().execute(register, language, null)
                .map(hover -> hover.syntax() == null || hover.syntax().isBlank()
                        ? hover.title()
                        : hover.title() + "\n" + hover.syntax()));
    }

    public void removeWatch(WatchItemViewModel item) {
        watches.remove(item);
    }

    public void refreshWatches() {
        if (activeSession == null) return;
        for (var w : watchSnapshot) {
            evaluateWatch(w);
        }
    }

    private void evaluateWatch(WatchItemViewModel item) {
        DebugSession session = activeSession;
        if (session == null) return;
        Optional<String> value = session.evaluateExpression(item.getExpression());
        FxDispatch.run(() -> value.ifPresentOrElse(item::setValue, item::setUnevaluated));
    }

    /** Keys the user typed for the debugged program; sessions whose program has its own window ignore them. */
    public void sendProgramInput(String text) {
        DebugSession session = activeSession;
        if (session != null && text != null && !text.isEmpty()) {
            session.sendInput(text);
        }
    }

    public void attachSession(DebugSession session) {
        this.activeSession = session;
        setActive(true);
        applyCapabilities(session == null ? Set.of() : session.capabilities());
        if (latestState != null) {
            // The emulator stops at the entry point before the session is handed over; fill the panels now.
            requestMemoryRefresh();
        }
    }

    /**
     * Switches the debugger controls to what this session actually supports. Turbo Debugger and CodeView run the
     * program in their own DOSBox window and report nothing back, so they declare nothing and the panels say so.
     */
    private void applyCapabilities(Set<DebugCapability> capabilities) {
        FxDispatch.run(() -> {
            canStep.set(capabilities.contains(DebugCapability.STEP));
            canPause.set(capabilities.contains(DebugCapability.PAUSE));
            canInspect.set(capabilities.contains(DebugCapability.REGISTERS));
            canReadMemory.set(capabilities.contains(DebugCapability.MEMORY));
            canWatch.set(capabilities.contains(DebugCapability.WATCHES));
            canShowCallStack.set(capabilities.contains(DebugCapability.CALL_STACK));
            canSendInput.set(capabilities.contains(DebugCapability.PROGRAM_INPUT));
            integrated.set(!capabilities.isEmpty());
        });
    }

    public void detachSession() {
        this.activeSession = null;
        FxDispatch.run(() -> {
            active.set(false);
            paused.set(false);
        });
        applyCapabilities(Set.of());
    }

    public void stepInto() {
        DebugSession session = activeSession;
        if (session != null) {
            session.stepInto();
        }
    }

    public void stepOver() {
        DebugSession session = activeSession;
        if (session != null) {
            session.stepOver();
        }
    }

    public void stepOut() {
        DebugSession session = activeSession;
        if (session != null) {
            session.stepOut();
        }
    }

    public void resume() {
        DebugSession session = activeSession;
        if (session != null) {
            session.resume();
        }
    }

    /** Interrupts a running program, so an endless loop can be inspected instead of only killed. */
    public void pause() {
        DebugSession session = activeSession;
        if (session != null) {
            session.pause();
        }
    }

    /**
     * Hands the current breakpoints to a live session, so one set or cleared during a pause takes effect on the
     * next resume instead of only on the next launch.
     */
    public void pushBreakpoints(List<Breakpoint> current) {
        DebugSession session = activeSession;
        if (session != null && current != null) {
            session.setBreakpoints(current);
        }
    }

    public Optional<Long> evaluateVariable(String filePath, int line, int byteSize) {
        DebugSession session = activeSession;
        if (session != null) {
            return session.evaluateVariable(filePath, line, byteSize);
        }
        return Optional.empty();
    }

    public void stop() {
        DebugSession session = activeSession;
        if (session != null) {
            session.stop();
        }
    }

    /** Reads memory, the call stack and the watches without holding up the user interface. */
    public CompletableFuture<Void> requestMemoryRefresh() {
        return CompletableFuture.runAsync(this::refreshMemoryDump);
    }

    /**
     * Reads memory, the call stack and the watches from the session. It asks the debugger and waits for the
     * answer, so it is called from a debugger event or through {@link #requestMemoryRefresh()}.
     */
    public void refreshMemoryDump() {
        DebugSession session = activeSession;
        if (session == null) return;
        long refresh = memoryRefreshes.incrementAndGet();
        try {
            RegisterState registers = latestState;
            boolean flat = registers != null && !registers.extended().isEmpty();
            String address = memoryOffsetValue;
            if (flat && (address == null || address.isBlank() || DOS_OFFSET.equals(address))) {
                // The first stop of a native program arrives before the field switches to the stack pointer.
                address = registers.extended().containsKey("RSP") ? "RSP" : "ESP";
            }
            String segmentText = memorySegmentValue;
            if (!flat && registers != null && DOS_SEGMENT.equals(segmentText)) {
                segmentText = String.format("%04X", registers.ds());
            }
            MemoryView view = flat
                    ? session.readMemory(flatAddress(address, registers), MEMORY_BYTES)
                    : session.readMemory(segment(segmentText), segmentOffset(memoryOffsetValue), MEMORY_BYTES);
            String dump = view.length() == 0 ? "" : view.toHexDump();

            List<CallFrameItemViewModel> stack = new ArrayList<>();
            List<CallFrame> callFrames = session.callStack(16);
            if (!callFrames.isEmpty()) {
                for (var frame : callFrames) {
                    stack.add(CallFrameItemViewModel.of(frame));
                }
            } else {
                for (StackFrame word : session.stack(8)) {
                    stack.add(CallFrameItemViewModel.of(word));
                }
            }

            // What the source line actually became: one line of assembly is often several instructions.
            List<DisasmLine> instructions = session.disassemble(DISASSEMBLY_LINES);

            FxDispatch.run(() -> {
                if (refresh != memoryRefreshes.get()) {
                    return; // A newer refresh started after this one; its result is the current one.
                }
                memoryDumpText.set(dump);
                stackLines.setAll(stack);
                disassembly.setAll(instructions);
            });
            refreshWatches();
        } catch (RuntimeException unreadable) {
            // An address the user typed that does not parse, or memory the debugger cannot read, shows as empty.
            LOG.log(System.Logger.Level.DEBUG, "Memory refresh failed", unreadable);
            FxDispatch.run(() -> {
                if (refresh == memoryRefreshes.get()) {
                    memoryDumpText.set("");
                }
            });
        }
    }

    private static int segment(String text) {
        String value = text == null ? "" : text.strip();
        return value.isEmpty() ? 0 : (int) Long.parseUnsignedLong(stripHexPrefix(value), 16);
    }

    private static int segmentOffset(String text) {
        String value = text == null ? "" : text.strip();
        return value.isEmpty() ? 0 : (int) Long.parseUnsignedLong(stripHexPrefix(value), 16);
    }

    /** A hex address (0x401000, 401000h, 401000) or a register name (RSP, rip). */
    static long flatAddress(String text, RegisterState registers) {
        String value = text == null ? "" : text.strip();
        if (value.isEmpty()) {
            return 0;
        }
        Long register = registers == null ? null : registers.extended().get(value.toUpperCase(Locale.ROOT));
        if (register != null) {
            return register;
        }
        return Long.parseUnsignedLong(stripHexPrefix(value), 16);
    }

    private static String stripHexPrefix(String value) {
        String text = value;
        if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
        }
        if (text.endsWith("h") || text.endsWith("H")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    public void refreshInstructionsExecuted() {
        DebugSession session = activeSession;
        if (session != null) {
            setInstructionsExecuted(session.instructionsExecuted());
        }
    }
}
