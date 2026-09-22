package io.github.dinamo541.idearm.toolchain.dos.microsoft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class MasmDiagnosticParserTest {

    private final MasmDiagnosticParser parser = new MasmDiagnosticParser();
    private final Function<String, String> identityMapper = Function.identity();

    @Test
    void parsesUndefinedSymbolError() throws IOException {
        String output = readFixture("/fixtures/diagnostics/ml-6.11/ERRSYM.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);

        assertEquals(1, diagnostics.size());
        Diagnostic d = diagnostics.getFirst();
        assertEquals(Severity.ERROR, d.severity());
        assertEquals("A2006", d.code());
        assertEquals("undefined symbol : printNumber", d.message());
        assertEquals("ml", d.tool());
        assertNotNull(d.location());
        assertEquals("C:\\IDEARM-FIXTURE\\S\\ERRSYM.ASM", d.location().path());
        assertEquals(12, d.location().line());
    }

    @Test
    void parsesMultipleSyntaxErrors() throws IOException {
        String output = readFixture("/fixtures/diagnostics/ml-6.11/ERRSYN.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);

        assertEquals(2, diagnostics.size());
        assertEquals(12, diagnostics.get(0).location().line());
        assertEquals("A2008", diagnostics.get(0).code());
        assertEquals(13, diagnostics.get(1).location().line());
        assertEquals("A2008", diagnostics.get(1).code());
    }

    @Test
    void parsesEndDirectiveRequired() throws IOException {
        String output = readFixture("/fixtures/diagnostics/ml-6.11/NOEND.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);

        assertEquals(1, diagnostics.size());
        Diagnostic d = diagnostics.getFirst();
        assertEquals(Severity.ERROR, d.severity());
        assertEquals("A2088", d.code());
        assertEquals(17, d.location().line());
    }

    @Test
    void parsesCpuInstructionError() throws IOException {
        String output = readFixture("/fixtures/diagnostics/ml-6.11/CPU186.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);

        assertEquals(2, diagnostics.size());
        assertEquals("A2001", diagnostics.get(0).code());
        assertEquals(8, diagnostics.get(0).location().line());
        assertEquals("A2070", diagnostics.get(1).code());
        assertEquals(9, diagnostics.get(1).location().line());
    }

    @Test
    void parsesWarningsAndErrors() throws IOException {
        String output = readFixture("/fixtures/diagnostics/ml-6.11/WARN.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);

        assertEquals(2, diagnostics.size());
        assertEquals(Severity.WARNING, diagnostics.get(0).severity());
        assertEquals("A4011", diagnostics.get(0).code());
        assertEquals(3, diagnostics.get(0).location().line());

        assertEquals(Severity.ERROR, diagnostics.get(1).severity());
        assertEquals("A2008", diagnostics.get(1).code());
        assertEquals(7, diagnostics.get(1).location().line());
    }

    @Test
    void cleanBuildProducesNoDiagnostics() throws IOException {
        String output = readFixture("/fixtures/diagnostics/ml-6.11/HELLO.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);
        assertTrue(diagnostics.isEmpty());
    }

    private static String readFixture(String path) throws IOException {
        try (InputStream in = MasmDiagnosticParserTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Fixture not found on classpath: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
