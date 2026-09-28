package io.github.dinamo541.idearm.toolchain.dos.borland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.build.AssembleRequest;
import io.github.dinamo541.idearm.domain.build.BuildPhase;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.model.HostKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class TasmAssemblerAdapterTest {

    private final TasmAssemblerAdapter adapter = new TasmAssemblerAdapter();

    @Test
    void assemblesReleaseThroughAResponseFile() {
        ToolInvocation invocation = adapter.assemble(
                new AssembleRequest("src/MAIN.ASM", "OBJ/MAIN.OBJ", null, false, "8086"));

        assertEquals("tasm", invocation.toolId());
        assertEquals(BuildPhase.ASSEMBLE, invocation.phase());
        assertEquals(HostKind.DOS_REAL, invocation.hostKind());
        assertEquals(List.of("@C:\\asm.rsp"), invocation.arguments());
        assertEquals("asm.rsp", invocation.responseFileName());
        assertEquals("/w2 S:\\SRC\\MAIN.ASM,C:\\OBJ\\MAIN.OBJ", invocation.responseFileContents());
        assertEquals(List.of("OBJ/MAIN.OBJ"), invocation.outputs());
    }

    @Test
    void assemblesDebugWithListing() {
        ToolInvocation invocation = adapter.assemble(
                new AssembleRequest("src/MAIN.ASM", "OBJ/MAIN.OBJ", "LST/MAIN.LST", true, "8086"));

        assertEquals("/w2 /zi /l S:\\SRC\\MAIN.ASM,C:\\OBJ\\MAIN.OBJ,C:\\LST\\MAIN.LST",
                invocation.responseFileContents());
        assertEquals(List.of("OBJ/MAIN.OBJ", "LST/MAIN.LST"), invocation.outputs());
    }

    @Test
    void searchesEveryIncludeFolderOnTheStagedDrive() {
        ToolInvocation invocation = adapter.assemble(new AssembleRequest("src/MAIN.ASM", "OBJ/MAIN.OBJ", null, false,
                "8086", List.of("src", "sprite")));

        // TASM looks in the current directory, which is the output drive, so every folder must be named with /i.
        assertTrue(invocation.responseFileContents().contains(" /iS:\\SRC "));
        assertTrue(invocation.responseFileContents().contains(" /iS:\\SPRITE "));
    }

    @Test
    void theProjectRootIsSearchedAsTheStagedDriveRoot() {
        ToolInvocation invocation = adapter.assemble(new AssembleRequest("MAIN.ASM", "OBJ/MAIN.OBJ", null, false,
                "8086", List.of(".")));

        assertEquals("/w2 /iS:\\ S:\\MAIN.ASM,C:\\OBJ\\MAIN.OBJ", invocation.responseFileContents());
    }

    @Test
    void searchesNothingWhenNoFolderIsGiven() {
        ToolInvocation invocation = adapter.assemble(
                new AssembleRequest("src/MAIN.ASM", "OBJ/MAIN.OBJ", null, false, "8086"));

        assertFalse(invocation.responseFileContents().contains("/i"));
    }

    @Test
    void providesTasmDiagnosticParser() {
        assertInstanceOf(TasmDiagnosticParser.class, adapter.diagnostics());
    }
}
