package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.port.BreakpointStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ManageBreakpointsTest {

    static final class FakeBreakpointStore implements BreakpointStore {
        List<Breakpoint> list = new ArrayList<>();
        @Override public List<Breakpoint> loadBreakpoints(Path projectRoot) { return List.copyOf(list); }
        @Override public void saveBreakpoints(Path projectRoot, List<Breakpoint> breakpoints) { list = new ArrayList<>(breakpoints); }
    }

    @Test
    void togglesBreakpointOnAndOff() {
        FakeBreakpointStore store = new FakeBreakpointStore();
        ManageBreakpoints mb = new ManageBreakpoints(store);
        Path root = Path.of("project");

        // Toggle on
        Optional<Breakpoint> bp = mb.toggleBreakpoint(root, "src/main.asm", 15);
        assertTrue(bp.isPresent());
        assertEquals("src/main.asm", bp.get().path());
        assertEquals(15, bp.get().line());
        assertTrue(bp.get().enabled());
        assertEquals(1, mb.getBreakpoints(root).size());

        // Toggle off
        Optional<Breakpoint> bpOff = mb.toggleBreakpoint(root, "src/main.asm", 15);
        assertTrue(bpOff.isEmpty());
        assertEquals(0, mb.getBreakpoints(root).size());
    }

    @Test
    void filtersByFileAndUpdatesEnabled() {
        FakeBreakpointStore store = new FakeBreakpointStore();
        ManageBreakpoints mb = new ManageBreakpoints(store);
        Path root = Path.of("project");

        mb.toggleBreakpoint(root, "src/main.asm", 10);
        mb.toggleBreakpoint(root, "src/main.asm", 20);
        mb.toggleBreakpoint(root, "src/UTILS.ASM", 30);

        assertEquals(2, mb.getBreakpointsForFile(root, "src/main.asm").size());
        assertEquals(1, mb.getBreakpointsForFile(root, "src/UTILS.ASM").size());

        mb.setBreakpointEnabled(root, "src/main.asm", 10, false);
        List<Breakpoint> mainBps = mb.getBreakpointsForFile(root, "src/main.asm");
        assertFalse(mainBps.stream().filter(b -> b.line() == 10).findFirst().orElseThrow().enabled());

        mb.clearBreakpoints(root);
        assertTrue(mb.getBreakpoints(root).isEmpty());
    }

    @Test
    void breakpointsFollowARenamedFileOrFolderAndLeaveWithADeletedOne() {
        var saved = new java.util.ArrayList<Breakpoint>();
        var store = new io.github.dinamo541.idearm.domain.port.BreakpointStore() {
            @Override public List<Breakpoint> loadBreakpoints(Path root) { return List.copyOf(saved); }
            @Override public void saveBreakpoints(Path root, List<Breakpoint> breakpoints) {
                saved.clear();
                saved.addAll(breakpoints);
            }
        };
        var breakpoints = new ManageBreakpoints(store);
        Path root = Path.of("project");
        breakpoints.toggleBreakpoint(root, "src/main.asm", 3);
        breakpoints.toggleBreakpoint(root, "src/lib/io.asm", 7);
        breakpoints.toggleBreakpoint(root, "src/library.asm", 9);

        breakpoints.movePath(root, "src/lib", "src/disk");
        breakpoints.movePath(root, "src/main.asm", "src/game.asm");

        assertEquals(List.of("src/game.asm", "src/disk/io.asm", "src/library.asm"),
                saved.stream().map(Breakpoint::path).toList(), "src/library.asm is not inside src/lib");

        breakpoints.removePath(root, "src/disk");

        assertEquals(List.of("src/game.asm", "src/library.asm"), saved.stream().map(Breakpoint::path).toList());
    }
}
