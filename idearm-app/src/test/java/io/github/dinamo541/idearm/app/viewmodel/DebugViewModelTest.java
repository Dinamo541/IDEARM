package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.CallFrame;
import io.github.dinamo541.idearm.domain.debug.DebugCapability;
import io.github.dinamo541.idearm.domain.debug.MemoryView;
import io.github.dinamo541.idearm.domain.debug.RegisterState;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;
import io.github.dinamo541.idearm.domain.port.DebugSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DebugViewModelTest {

    /** A session that records what it was asked to do and declares the capabilities it is given. */
    private static final class RecordingSession implements DebugSession {
        private final Set<DebugCapability> capabilities;
        private final List<String> calls = new ArrayList<>();
        private List<Breakpoint> breakpoints = List.of();

        RecordingSession(Set<DebugCapability> capabilities) {
            this.capabilities = capabilities;
        }

        @Override public CompletableFuture<ExitInfo> exit() { return new CompletableFuture<>(); }
        @Override public SessionState state() { return SessionState.RUNNING; }
        @Override public void stop() { calls.add("stop"); }
        @Override public Set<DebugCapability> capabilities() { return capabilities; }
        @Override public void resume() { calls.add("resume"); }
        @Override public void stepOver() { calls.add("stepOver"); }
        @Override public void stepInto() { calls.add("stepInto"); }
        @Override public void stepOut() { calls.add("stepOut"); }
        @Override public void pause() { calls.add("pause"); }
        @Override public void setBreakpoints(List<Breakpoint> updated) { this.breakpoints = updated; }
        @Override public void close() { }
    }

    @Test
    void everyDebugControlReachesTheSession() {
        DebugViewModel vm = new DebugViewModel();
        var session = new RecordingSession(EnumSet.allOf(DebugCapability.class));
        vm.attachSession(session);

        vm.resume();
        vm.stepOver();
        vm.stepInto();
        vm.stepOut();
        vm.pause();
        vm.stop();

        assertEquals(List.of("resume", "stepOver", "stepInto", "stepOut", "pause", "stop"), session.calls);
    }

    @Test
    void aLaunchOnlyDebuggerSwitchesTheControlsOff() {
        DebugViewModel vm = new DebugViewModel();

        // Turbo Debugger in a DOSBox window: it runs the program and reports nothing back.
        vm.attachSession(new RecordingSession(Set.of()));
        assertFalse(vm.integratedProperty().get(), "The panels have nothing to show");
        assertFalse(vm.canStepProperty().get());
        assertFalse(vm.canPauseProperty().get());
        assertFalse(vm.canInspectProperty().get());

        // The built-in emulator drives the program itself, so the panels work.
        vm.attachSession(new RecordingSession(EnumSet.of(DebugCapability.STEP, DebugCapability.REGISTERS,
                DebugCapability.PAUSE)));
        assertTrue(vm.integratedProperty().get());
        assertTrue(vm.canStepProperty().get());
        assertTrue(vm.canPauseProperty().get());
        assertTrue(vm.canInspectProperty().get());
        assertFalse(vm.canWatchProperty().get(), "This session did not declare watches");

        vm.detachSession();
        assertFalse(vm.canStepProperty().get(), "Nothing is offered once the session is gone");
    }

    @Test
    void breakpointsChangedDuringAPauseReachTheRunningSession() {
        DebugViewModel vm = new DebugViewModel();
        var session = new RecordingSession(EnumSet.of(DebugCapability.BREAKPOINTS));
        vm.attachSession(session);

        var current = List.of(new Breakpoint("src/main.asm", 12, true));
        vm.pushBreakpoints(current);

        assertEquals(current, session.breakpoints);
    }

    @Test
    void anUnreadableWatchIsMarkedForTheViewToExplain() {
        DebugViewModel vm = new DebugViewModel();
        vm.attachSession(new RecordingSession(EnumSet.of(DebugCapability.WATCHES)));

        // evaluateExpression is the port default here: this debugger cannot evaluate anything.
        vm.addWatch("message");

        var watch = vm.getWatches().get(0);
        assertTrue(watch.isError(), "The view shows the reason in the user's language, not a made-up marker");
        assertEquals("", watch.getValue());
    }

    @Test
    void aDosRegisterReadsInHexDecimalAndBinary() {
        DebugViewModel vm = new DebugViewModel();
        var state = new RegisterState(0x0A41, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of());

        vm.updateRegisters(state);

        var ax = vm.getRegisterList().stream()
                .filter(register -> register.getName().equals("AX"))
                .findFirst()
                .orElseThrow();
        assertEquals("0A41", ax.getHexValue());
        assertEquals("2625", ax.getDecValue());
        // Grouped in nibbles, so each group reads against its hex digit: 0=0000, A=1010, 4=0100, 1=0001.
        assertEquals("0000 1010 0100 0001", ax.getBinValue());
        assertTrue(ax.isChanged(), "The value moved away from zero at this stop");
    }

    @Test
    void managesWatchExpressions() {
        DebugViewModel vm = new DebugViewModel();
        assertTrue(vm.getWatches().isEmpty());

        vm.addWatch("RAX");
        assertEquals(1, vm.getWatches().size());
        assertEquals("RAX", vm.getWatches().get(0).getExpression());

        // Duplicate watch is ignored
        vm.addWatch("RAX");
        assertEquals(1, vm.getWatches().size());

        vm.addWatch("RBX + 8");
        assertEquals(2, vm.getWatches().size());

        vm.removeWatch(vm.getWatches().get(0));
        assertEquals(1, vm.getWatches().size());
        assertEquals("RBX + 8", vm.getWatches().get(0).getExpression());
    }

    @Test
    void evaluatesWatchesAndRefreshesCallStack() {
        DebugViewModel vm = new DebugViewModel();

        DebugSession mockSession = new DebugSession() {
            @Override
            public CompletableFuture<ExitInfo> exit() { return CompletableFuture.completedFuture(null); }
            @Override
            public SessionState state() { return SessionState.RUNNING; }
            @Override
            public void stop() {}
            @Override
            public void close() {}

            @Override
            public Optional<String> evaluateExpression(String expr) {
                if ("RAX".equals(expr)) return Optional.of("0x42");
                return Optional.empty();
            }

            @Override
            public List<CallFrame> callStack(int depth) {
                return List.of(new CallFrame(0, "0x401000", "main", "src/main.asm", 15));
            }

            @Override
            public MemoryView readMemory(int segment, int offset, int length) {
                return new MemoryView(segment, offset, new byte[] { 0x48, (byte) 0x89, (byte) 0xC8 });
            }
        };

        vm.attachSession(mockSession);
        vm.addWatch("RAX");
        assertEquals("0x42", vm.getWatches().get(0).getValue());

        vm.memorySegmentProperty().set("0");
        vm.memoryOffsetProperty().set("0x401000");
        vm.refreshMemoryDump();

        assertFalse(vm.memoryDumpTextProperty().get().isBlank());
        assertEquals(1, vm.getStackLines().size());
        assertTrue(vm.getStackLines().get(0).getText().contains("main"));
        assertTrue(vm.getStackLines().get(0).getText().contains("src/main.asm:15"));
        // A frame that knows its source line can be opened from the panel.
        assertTrue(vm.getStackLines().get(0).isNavigable());
    }

    @Test
    void aNativeProgramShowsItsOwnRegistersAndAFlatAddress() {
        DebugViewModel vm = new DebugViewModel();
        long[] asked = new long[1];
        vm.attachSession(new DebugSession() {
            @Override public CompletableFuture<ExitInfo> exit() { return new CompletableFuture<>(); }
            @Override public SessionState state() { return SessionState.RUNNING; }
            @Override public void stop() {}
            @Override public void close() {}
            @Override
            public MemoryView readMemory(long address, int length) {
                asked[0] = address;
                return MemoryView.flat(address, new byte[] {1, 2, 3});
            }
        });

        var registers = new java.util.LinkedHashMap<String, Long>();
        registers.put("RAX", 0x1122334455667788L);
        registers.put("RSP", 0x5FFF30L);
        registers.put("RIP", 0x401010L);
        registers.put("EFLAGS", 0x246L);
        registers.put("CS", 0x33L);
        vm.updateRegisters(io.github.dinamo541.idearm.domain.debug.RegisterState.fromExtended(registers));

        assertTrue(vm.nativeSessionProperty().get());
        assertEquals(List.of("RAX", "RSP", "RIP", "EFLAGS", "CS"),
                vm.getRegisterList().stream().map(RegisterItemViewModel::getName).toList());
        assertEquals("RSP", vm.memoryOffsetProperty().get(), "The stack is the first memory shown");
        assertTrue(vm.zfProperty().get());

        vm.refreshMemoryDump();
        assertEquals(0x5FFF30L, asked[0]);
        assertTrue(vm.memoryDumpTextProperty().get().startsWith("005FFF30  01 02 03"), vm.memoryDumpTextProperty().get());

        vm.memoryOffsetProperty().set("403000h");
        vm.refreshMemoryDump();
        assertEquals(0x403000L, asked[0]);

        // The next DOS session gets the 8086 registers and a segment:offset address back.
        vm.reset();
        assertFalse(vm.nativeSessionProperty().get());
        assertEquals("AX", vm.getRegisterList().getFirst().getName());
        assertEquals("0710", vm.memorySegmentProperty().get());
    }
}
