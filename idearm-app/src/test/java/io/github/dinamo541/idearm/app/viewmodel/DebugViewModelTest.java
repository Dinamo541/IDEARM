package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.domain.debug.CallFrame;
import io.github.dinamo541.idearm.domain.debug.MemoryView;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;
import io.github.dinamo541.idearm.domain.port.DebugSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DebugViewModelTest {

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
        assertTrue(vm.getStackLines().get(0).contains("main"));
        assertTrue(vm.getStackLines().get(0).contains("src/main.asm:15"));
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
