package io.github.dinamo541.idearm.toolchain.dos.borland;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class TasmDiagnosticParserTest {

    private final TasmDiagnosticParser parser = new TasmDiagnosticParser();
    private final Function<String, String> identityMapper = Function.identity();

    @Test
    void parsesUndefinedSymbolErrorFromFixture() throws IOException {
        String output = readFixture("/fixtures/diagnostics/tasm-4.1/ERRSYM.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);

        assertEquals(1, diagnostics.size());
        Diagnostic d = diagnostics.getFirst();
        assertEquals(Severity.ERROR, d.severity());
        assertEquals("tasm", d.tool());
        assertEquals("Undefined symbol: PRINTNUMBER", d.message());
        assertNotNull(d.location());
        assertEquals("S:\\ERRSYM.ASM", d.location().path());
        assertEquals(12, d.location().line());
    }

    @Test
    void parsesSyntaxErrorFromFixture() throws IOException {
        String output = readFixture("/fixtures/diagnostics/tasm-4.1/ERRSYN.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);

        assertFalse(diagnostics.isEmpty());
        Diagnostic d = diagnostics.getFirst();
        assertEquals(Severity.ERROR, d.severity());
        assertNotNull(d.location());
        assertEquals(12, d.location().line());
        assertEquals("Illegal instruction", d.message());
    }

    @Test
    void parsesFatalErrorFromFixture() throws IOException {
        String output = readFixture("/fixtures/diagnostics/tasm-4.1/NOEND.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);

        assertEquals(1, diagnostics.size());
        Diagnostic d = diagnostics.getFirst();
        assertEquals(Severity.FATAL, d.severity());
        assertEquals("Unexpected end of file encountered", d.message());
        assertNotNull(d.location());
        assertEquals(19, d.location().line());
    }

    @Test
    void parsesMissingFileFatalFromFixture() throws IOException {
        String output = readFixture("/fixtures/diagnostics/tasm-4.1/NOFILE.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);

        assertEquals(1, diagnostics.size());
        Diagnostic d = diagnostics.getFirst();
        assertEquals(Severity.FATAL, d.severity());
        assertTrue(d.message().contains("Can't locate file"));
        assertNotNull(d.location());
        assertEquals("S:\\NOFILE.ASM", d.location().path());
    }

    @Test
    void parsesWarningsFromFixture() throws IOException {
        String output = readFixture("/fixtures/diagnostics/tasm-4.1/WARN.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);

        assertFalse(diagnostics.isEmpty());
        assertTrue(diagnostics.stream().anyMatch(d -> d.severity() == Severity.WARNING));
    }

    @Test
    void cleanBuildProducesNoDiagnostics() throws IOException {
        String output = readFixture("/fixtures/diagnostics/tasm-4.1/HELLO.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);
        assertTrue(diagnostics.isEmpty());
    }

    private static String readFixture(String path) throws IOException {
        try (InputStream in = TasmDiagnosticParserTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Fixture not found on classpath: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
