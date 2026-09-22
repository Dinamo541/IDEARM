package io.github.dinamo541.idearm.toolchain.dos.microsoft;

import io.github.dinamo541.idearm.domain.build.AssembleRequest;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.model.HostKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MasmAssemblerAdapterTest {

    private final MasmAssemblerAdapter adapter = new MasmAssemblerAdapter();

    @Test
    void assemblesReleaseWithoutDebugOrListing() {
        AssembleRequest req = new AssembleRequest("src/MAIN.ASM", "OBJ/MAIN.OBJ", null, false, "8086");
        ToolInvocation inv = adapter.assemble(req);

        assertEquals("ml", inv.toolId());
        assertEquals(HostKind.WIN32_CONSOLE, inv.hostKind());
        assertEquals(List.of("OBJ/MAIN.OBJ"), inv.outputs());
        assertNull(inv.responseFileName());

        List<String> args = inv.arguments();
        assertTrue(args.contains("/c"));
        assertTrue(args.contains("/nologo"));
        assertTrue(args.contains("/W2"));
        assertFalse(args.contains("/Zi"));
        assertTrue(args.contains("/FoOBJ\\MAIN.OBJ"));
        assertTrue(args.contains("src\\MAIN.ASM"));
    }

    @Test
    void assemblesDebugWithListing() {
        AssembleRequest req = new AssembleRequest("src/ENTRY.ASM", "OBJ/ENTRY.OBJ", "LST/ENTRY.LST", true, "8086");
        ToolInvocation inv = adapter.assemble(req);

        assertEquals("ml", inv.toolId());
        assertEquals(HostKind.WIN32_CONSOLE, inv.hostKind());
        assertEquals(List.of("OBJ/ENTRY.OBJ", "LST/ENTRY.LST"), inv.outputs());

        List<String> args = inv.arguments();
        assertTrue(args.contains("/Zi"));
        assertTrue(args.contains("/FoOBJ\\ENTRY.OBJ"));
        assertTrue(args.contains("/FlLST\\ENTRY.LST"));
        assertTrue(args.contains("src\\ENTRY.ASM"));
    }

    @Test
    void providesMasmDiagnosticParser() {
        assertNotNull(adapter.diagnostics());
        assertInstanceOf(MasmDiagnosticParser.class, adapter.diagnostics());
    }
}
