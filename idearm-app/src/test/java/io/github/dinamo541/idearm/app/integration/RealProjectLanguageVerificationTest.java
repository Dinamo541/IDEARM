package io.github.dinamo541.idearm.app.integration;

import io.github.dinamo541.idearm.application.editor.HoverKind;
import io.github.dinamo541.idearm.application.editor.OutlineItem;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.application.editor.QueryDefinition;
import io.github.dinamo541.idearm.application.editor.QueryHover;
import io.github.dinamo541.idearm.application.editor.QueryOutline;
import io.github.dinamo541.idearm.application.editor.LintSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RealProjectLanguageVerificationTest {

    private static final Path MASTERMIND_DIR = Path.of("C:/Codigo/Asembly/Mastermind");
    private static final Path TURTORIA_DIR = Path.of("C:/Codigo/Asembly/Turtoria");

    @Test
    void verifyMastermindLanguageIntelligence() throws IOException {
        assumeTrue(Files.exists(MASTERMIND_DIR), "Mastermind project directory exists");

        Path mainAsm = MASTERMIND_DIR.resolve("src").resolve("main.asm");
        assumeTrue(Files.exists(mainAsm), "main.asm exists");

        String mainContent = Files.readString(mainAsm, StandardCharsets.UTF_8);
        // The user keeps working on this project; the checks below describe the version that had this label.
        assumeTrue(mainContent.contains("desigual:"), "main.asm still has the 'desigual:' label");

        ProjectSymbolIndex index = new ProjectSymbolIndex();
        index.updateFile("src/main.asm", mainContent);

        // 1. Go to Definition on 'desigual'
        QueryDefinition queryDef = new QueryDefinition();
        Optional<Location> defOpt = queryDef.execute("desigual", index);
        assertTrue(defOpt.isPresent(), "Should resolve definition of 'desigual'");
        assertEquals(30, defOpt.get().line(), "Line of 'desigual:' label in main.asm");

        // 2. Hover on instruction 'INT' in Spanish
        QueryHover queryHover = new QueryHover();
        var intHover = queryHover.execute("INT", "es", index);
        assertTrue(intHover.isPresent(), "Hover on INT should be present");
        assertEquals(HoverKind.INSTRUCTION, intHover.get().kind());
        assertTrue(intHover.get().title().contains("INT"));
        assertNotNull(intHover.get().flagsTable());

        // 3. Hover on number '21h'
        var hexHover = queryHover.execute("21h", "es", index);
        assertTrue(hexHover.isPresent(), "Hover on 21h should be present");
        assertEquals(HoverKind.NUMBER_CONVERSION, hexHover.get().kind());
        assertTrue(hexHover.get().syntax().contains("0x21"));
        assertTrue(hexHover.get().description().contains("33")); // Decimal 33

        // 4. Document Outline
        QueryOutline queryOutline = new QueryOutline();
        List<OutlineItem> outline = queryOutline.execute(mainContent);
        assertFalse(outline.isEmpty(), "Outline should not be empty");
        var mainProc = outline.stream().filter(o -> o.name().equalsIgnoreCase("main")).findFirst();
        assertTrue(mainProc.isPresent(), "Outline should contain procedure 'main'");
        assertTrue(mainProc.get().children().stream().anyMatch(c -> c.name().equalsIgnoreCase("desigual")),
                "Procedure 'main' should nest label 'desigual'");

        // 5. Educational Linter flags missing DOS exit
        LintSource lintSource = new LintSource();
        List<Diagnostic> diagnostics = lintSource.execute(mainContent, "src/main.asm", "8086", index);
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("lint.missing-exit")),
                "Linter should detect missing DOS program termination (4Ch/INT 21h)");
        assertTrue(diagnostics.stream().anyMatch(d -> d.severity() == Severity.WARNING));
    }

    @Test
    void verifyTurtoriaLanguageIntelligence() throws IOException {
        assumeTrue(Files.exists(TURTORIA_DIR), "Turtoria project directory exists");

        Path mainAsm = TURTORIA_DIR.resolve("main.asm");
        if (!Files.exists(mainAsm)) {
            mainAsm = TURTORIA_DIR.resolve("src").resolve("main.asm");
        }
        assumeTrue(Files.exists(mainAsm), "Turtoria main asm exists");

        String content = Files.readString(mainAsm, StandardCharsets.UTF_8);
        ProjectSymbolIndex index = new ProjectSymbolIndex();
        index.updateFile("main.asm", content);

        // 1. Variable definition
        var varDef = index.findDefinition("mensaje");
        assertTrue(varDef.isPresent(), "Variable 'mensaje' should be indexed");
        assertEquals(5, varDef.get().line());

        // 2. Outline
        QueryOutline queryOutline = new QueryOutline();
        List<OutlineItem> outline = queryOutline.execute(content);
        assertFalse(outline.isEmpty(), "Turtoria outline should not be empty");
        assertTrue(outline.stream().anyMatch(o -> o.name().equalsIgnoreCase("MAIN")),
                "Outline should contain procedure 'MAIN'");

        // 3. Clean termination check: should have zero missing-exit warnings
        LintSource lintSource = new LintSource();
        List<Diagnostic> diagnostics = lintSource.execute(content, "main.asm", "8086", index);
        assertFalse(diagnostics.stream().anyMatch(d -> d.code().equals("lint.missing-exit")),
                "Turtoria cleanly calls 4C00h / INT 21h, so no missing-exit warning should be emitted");
    }
}
