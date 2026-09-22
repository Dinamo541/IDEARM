package io.github.dinamo541.idearm.toolchain.dos.microsoft;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.LinkRequest;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.model.TargetProfileCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MasmLinkerAdapterTest {

    private final MasmLinkerAdapter adapter = new MasmLinkerAdapter();
    private final TargetProfile target = TargetProfileCatalog.DOS_EXE_16;

    @Test
    void linksSingleObjectFileEndingWithSemicolon() {
        LinkRequest req = new LinkRequest(List.of("OBJ/MAIN.OBJ"), "BIN/MAIN.EXE", null, false, target);
        ToolInvocation inv = adapter.link(req);

        assertEquals("link", inv.toolId());
        assertEquals(HostKind.DOS_REAL, inv.hostKind());
        assertEquals("link.rsp", inv.responseFileName());
        assertEquals(List.of("@C:\\link.rsp"), inv.arguments());
        assertEquals(List.of("BIN/MAIN.EXE"), inv.outputs());

        String rsp = inv.responseFileContents();
        assertNotNull(rsp);
        assertTrue(rsp.startsWith("/NOLOGO /ONERROR:NOEXE /BATCH"));
        assertFalse(rsp.contains("/CO"));
        assertTrue(rsp.contains("C:\\OBJ\\MAIN.OBJ,C:\\BIN\\MAIN.EXE"));
        assertTrue(rsp.endsWith(";"), "Response file must end with semicolon to prevent prompt hanging");
    }

    @Test
    void linksMultipleObjectFilesWithPlusSeparator() {
        LinkRequest req = new LinkRequest(
                List.of("OBJ/MAIN.OBJ", "OBJ/EXTRAS.OBJ", "OBJ/UTILS.OBJ"),
                "BIN/APP.EXE",
                "MAP/APP.MAP",
                true,
                target
        );
        ToolInvocation inv = adapter.link(req);

        assertEquals(List.of("BIN/APP.EXE", "MAP/APP.MAP"), inv.outputs());

        String rsp = inv.responseFileContents();
        assertTrue(rsp.contains("/CO"));
        assertTrue(rsp.contains("C:\\OBJ\\MAIN.OBJ+C:\\OBJ\\EXTRAS.OBJ+C:\\OBJ\\UTILS.OBJ,C:\\BIN\\APP.EXE,C:\\MAP\\APP.MAP;"));
        assertTrue(rsp.endsWith(";"));
    }

    @Test
    void rejectsUnsupportedTargetFormat() {
        TargetProfile elfTarget = new TargetProfile("linux-elf-32", "x86", "80386", 32, "protected", "Linux", "ELF", "flat", "ELF");
        LinkRequest req = new LinkRequest(List.of("OBJ/MAIN.OBJ"), "BIN/MAIN.EXE", null, false, elfTarget);

        assertThrows(DomainException.class, () -> adapter.link(req));
    }

    @Test
    void providesMasmLinkerDiagnosticParser() {
        assertNotNull(adapter.diagnostics());
        assertInstanceOf(MasmLinkerDiagnosticParser.class, adapter.diagnostics());
    }
}
