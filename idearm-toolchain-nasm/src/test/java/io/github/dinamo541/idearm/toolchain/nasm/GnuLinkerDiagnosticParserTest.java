package io.github.dinamo541.idearm.toolchain.nasm;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class GnuLinkerDiagnosticParserTest {

    private final GnuLinkerDiagnosticParser parser = new GnuLinkerDiagnosticParser();

    @Test
    void parsesUndefinedReference() {
        String output = "build/obj/main.obj:(.text+0x1a): undefined reference to `ExitProcess'\n";
        List<Diagnostic> diags = parser.parse(output, Function.identity());

        assertEquals(1, diags.size());
        assertEquals(Severity.ERROR, diags.get(0).severity());
        assertEquals("build/obj/main.obj", diags.get(0).location().path());
        assertTrue(diags.get(0).message().contains("undefined reference"));
    }

    @Test
    void parsesLdWarning() {
        String output = "ld: warning: cannot find entry symbol _start; defaulting to 0000000000401000\n";
        List<Diagnostic> diags = parser.parse(output, Function.identity());

        assertEquals(1, diags.size());
        // ld only warns, but the program would start at an arbitrary instruction.
        assertEquals(Severity.ERROR, diags.get(0).severity());
        assertTrue(diags.get(0).message().contains("cannot find entry symbol"));
    }

    @Test
    void parsesFileWithLine() {
        String output = "src/main.asm:10: undefined reference to `_start'\n";
        List<Diagnostic> diags = parser.parse(output, Function.identity());

        assertEquals(1, diags.size());
        assertEquals("src/main.asm", diags.get(0).location().path());
        assertEquals(10, diags.get(0).location().line());
    }

    /** Lines exactly as ld 2.46 from MSYS2 prints them when started by its full path. */
    @Test
    void readsWindowsLdOutput() {
        String ld = "C:\\msys64\\ucrt64\\bin\\ld.exe: ";
        String output = String.join("\n",
                ld + "C:\\stage\\obj\\main.obj: in function `main':",
                "C:\\proj\\/src/main.asm:6:(.text+0x1): undefined reference to `WriteFileX'",
                ld + "C:\\stage\\obj\\main.obj:src/main.asm:(.text+0x1): undefined reference to `ExitProcessX'",
                ld + "warning: cannot find entry symbol main; defaulting to 0000000140001000",
                ld + "cannot find -lkernel33: No such file or directory",
                ld + "warning: something harmless",
                "");

        List<Diagnostic> diags = parser.parse(output, Function.identity());

        assertEquals(5, diags.size(), diags::toString);
        // The compilation folder joined to the name is dropped: the name is the project-relative path.
        assertEquals("src/main.asm", diags.get(0).location().path());
        assertEquals(6, diags.get(0).location().line());
        assertEquals("undefined reference to `WriteFileX'", diags.get(0).message());
        assertEquals("src/main.asm", diags.get(1).location().path());
        assertNull(diags.get(1).location().line());
        assertEquals(Severity.ERROR, diags.get(2).severity());
        assertTrue(diags.get(2).message().startsWith("cannot find entry symbol main"));
        assertNull(diags.get(3).location());
        assertEquals("cannot find -lkernel33: No such file or directory", diags.get(3).message());
        assertEquals(Severity.WARNING, diags.get(4).severity());
    }
}
