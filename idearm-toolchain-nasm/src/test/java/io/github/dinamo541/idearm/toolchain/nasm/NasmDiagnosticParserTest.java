package io.github.dinamo541.idearm.toolchain.nasm;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class NasmDiagnosticParserTest {

    private final NasmDiagnosticParser parser = new NasmDiagnosticParser();

    @Test
    void parsesStandardErrorWithLine() {
        String output = "src/main.asm:15: error: symbol `undefined_label' undefined\n";
        List<Diagnostic> diagnostics = parser.parse(output, Function.identity());

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals(Severity.ERROR, diag.severity());
        assertEquals("src/main.asm", diag.location().path());
        assertEquals(15, diag.location().line());
        assertTrue(diag.message().contains("undefined_label"));
    }

    @Test
    void parsesWarningWithLine() {
        String output = "src/main.asm:22: warning: label alone on a line without a colon [-w+orphan-labels]\n";
        List<Diagnostic> diagnostics = parser.parse(output, Function.identity());

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals(Severity.WARNING, diag.severity());
        assertEquals("src/main.asm", diag.location().path());
        assertEquals(22, diag.location().line());
    }

    @Test
    void parsesFatalErrorWithoutLine() {
        String output = "missing.asm: fatal: unable to open input file\n";
        List<Diagnostic> diagnostics = parser.parse(output, Function.identity());

        assertEquals(1, diagnostics.size());
        Diagnostic diag = diagnostics.get(0);
        assertEquals(Severity.ERROR, diag.severity());
        assertEquals("missing.asm", diag.location().path());
        assertNull(diag.location().line());
    }

    @Test
    void ignoresEmptyOutput() {
        assertTrue(parser.parse("", Function.identity()).isEmpty());
        assertTrue(parser.parse(null, Function.identity()).isEmpty());
    }
}
