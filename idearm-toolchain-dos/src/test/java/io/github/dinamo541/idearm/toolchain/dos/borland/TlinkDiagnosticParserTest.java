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

class TlinkDiagnosticParserTest {

    private final TlinkDiagnosticParser parser = new TlinkDiagnosticParser();
    private final Function<String, String> identityMapper = Function.identity();

    @Test
    void parsesUnresolvedExternalFromFixture() throws IOException {
        String output = readFixture("/fixtures/diagnostics/tlink-7.1/EXTERN.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);

        assertEquals(1, diagnostics.size());
        Diagnostic d = diagnostics.getFirst();
        assertEquals(Severity.ERROR, d.severity());
        assertEquals("tlink", d.tool());
        assertTrue(d.message().contains("Undefined symbol PRINTNUMBER"));
        assertNotNull(d.location());
        assertEquals("S:\\EXTERN.ASM", d.location().path());
    }

    @Test
    void parsesDuplicateSymbolFromFixture() throws IOException {
        String output = readFixture("/fixtures/diagnostics/tlink-7.1/DUP.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);

        // Both modules in duplicate symbol should produce diagnostics for navigation
        assertFalse(diagnostics.isEmpty());
        assertTrue(diagnostics.stream().anyMatch(d -> d.location() != null && d.location().path().contains("DUP")));
    }

    @Test
    void cleanBuildProducesNoDiagnostics() throws IOException {
        String output = readFixture("/fixtures/diagnostics/tlink-7.1/HELLO.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);
        assertTrue(diagnostics.isEmpty());
    }

    private static String readFixture(String path) throws IOException {
        try (InputStream in = TlinkDiagnosticParserTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Fixture not found on classpath: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
