package io.github.dinamo541.idearm.infrastructure.persistence;

import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileBreakpointStoreTest {

    @Test
    void returnsEmptyListWhenFileDoesNotExist(@TempDir Path tempDir) {
        FileBreakpointStore store = new FileBreakpointStore();
        List<Breakpoint> loaded = store.loadBreakpoints(tempDir);
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void savesAndLoadsBreakpointsRoundTrip(@TempDir Path tempDir) {
        FileBreakpointStore store = new FileBreakpointStore();
        List<Breakpoint> original = List.of(
                new Breakpoint("src/MAIN.ASM", 10, true),
                new Breakpoint("src/UTILS.ASM", 45, false)
        );

        store.saveBreakpoints(tempDir, original);
        assertTrue(Files.exists(tempDir.resolve(".idearm").resolve("breakpoints.json")));

        List<Breakpoint> loaded = store.loadBreakpoints(tempDir);
        assertEquals(2, loaded.size());

        assertEquals("src/MAIN.ASM", loaded.get(0).path());
        assertEquals(10, loaded.get(0).line());
        assertTrue(loaded.get(0).enabled());

        assertEquals("src/UTILS.ASM", loaded.get(1).path());
        assertEquals(45, loaded.get(1).line());
        assertFalse(loaded.get(1).enabled());
    }
}
