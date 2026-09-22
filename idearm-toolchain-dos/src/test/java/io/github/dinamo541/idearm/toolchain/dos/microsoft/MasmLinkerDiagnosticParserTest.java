package io.github.dinamo541.idearm.toolchain.dos.microsoft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class MasmLinkerDiagnosticParserTest {

    private final MasmLinkerDiagnosticParser parser = new MasmLinkerDiagnosticParser();
    private final Function<String, String> identityMapper = Function.identity();

    @Test
    void parsesUnresolvedExternalError() throws IOException {
        String output = readFixture("/fixtures/diagnostics/link-5.31/EXTERN.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);

        assertEquals(1, diagnostics.size());
        Diagnostic d = diagnostics.getFirst();
        assertEquals(Severity.ERROR, d.severity());
        assertEquals("L2029", d.code());
        assertEquals("'printNumber' : unresolved external", d.message());
        assertEquals("link", d.tool());
        assertNotNull(d.location());
        assertEquals("C:\\IDEARM-FIXTURE\\S\\EXTERN.ASM", d.location().path());
    }

    @Test
    void parsesDuplicateSymbolErrors() throws IOException {
        String output = readFixture("/fixtures/diagnostics/link-5.31/DUP.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);

        assertEquals(2, diagnostics.size());
        assertEquals("L2025", diagnostics.get(0).code());
        assertEquals("helper : symbol defined more than once", diagnostics.get(0).message());
        assertEquals("C:\\IDEARM-FIXTURE\\S\\DUPB.ASM", diagnostics.get(0).location().path());

        assertEquals("L2025", diagnostics.get(1).code());
        assertEquals("main : symbol defined more than once", diagnostics.get(1).message());
        assertEquals("C:\\IDEARM-FIXTURE\\S\\DUPB.ASM", diagnostics.get(1).location().path());
    }

    @Test
    void parsesNoStackWarning() throws IOException {
        String output = readFixture("/fixtures/diagnostics/link-5.31/NOSTK.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);

        assertEquals(1, diagnostics.size());
        Diagnostic d = diagnostics.getFirst();
        assertEquals(Severity.WARNING, d.severity());
        assertEquals("L4021", d.code());
        assertEquals("no stack segment", d.message());
        assertNull(d.location());
    }

    @Test
    void cleanLinkProducesNoDiagnostics() throws IOException {
        String output = readFixture("/fixtures/diagnostics/link-5.31/HELLO.txt");
        List<Diagnostic> diagnostics = parser.parse(output, identityMapper);
        assertTrue(diagnostics.isEmpty());
    }

    private static String readFixture(String path) throws IOException {
        try (InputStream in = MasmLinkerDiagnosticParserTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Fixture not found on classpath: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
