package io.github.dinamo541.idearm.application.editor;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QueryEditorUseCasesTest {

    private ProjectSymbolIndex index;

    @BeforeEach
    void setUp() {
        index = new ProjectSymbolIndex();
        String code = """
                .model small
                .data
                counter dw 0
                .code
                main proc
                start:
                    mov ax, 10h
                    call do_work
                    mov ax, 4c00h
                    int 21h
                main endp
                do_work proc
                    inc counter
                    ret
                do_work endp
                end main
                """;
        index.updateFile("src/main.asm", code);
    }

    @Test
    void queryDefinitionFindsSymbol() {
        QueryDefinition query = new QueryDefinition();
        Optional<Location> loc = query.execute("do_work", index);

        assertTrue(loc.isPresent());
        assertEquals("src/main.asm", loc.get().path());
        assertEquals(12, loc.get().line());
    }

    @Test
    void queryReferencesFindsUsages() {
        QueryReferences query = new QueryReferences();
        List<Location> refs = query.execute("counter", index);

        assertFalse(refs.isEmpty());
        assertTrue(refs.stream().anyMatch(l -> l.line() == 13));
    }

    @Test
    void queryCompletionIncludesInstructionsAndSymbols() {
        QueryCompletion query = new QueryCompletion();
        List<CompletionItem> items = query.execute("co", "8086", index);

        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(i -> i.label().equalsIgnoreCase("counter")));
    }

    @Test
    void queryHoverTranslatesInstructionsAndConvertsNumbers() {
        QueryHover hover = new QueryHover();

        // 1. Instruction hover (Spanish)
        Optional<HoverInfo> instHoverEs = hover.execute("INT", "es_ES", index);
        assertTrue(instHoverEs.isPresent());
        assertEquals(HoverKind.INSTRUCTION, instHoverEs.get().kind());
        assertTrue(instHoverEs.get().description().contains("interrupción"));

        // 2. Numeric hover (Hex literal)
        Optional<HoverInfo> numHover = hover.execute("4Ch", "en_US", index);
        assertTrue(numHover.isPresent());
        assertEquals(HoverKind.NUMBER_CONVERSION, numHover.get().kind());
        assertTrue(numHover.get().description().contains("Decimal:     76"));
        assertTrue(numHover.get().description().contains("ASCII:       'L'"));
    }

    /** Hovering over AH showed "Numeric literal AH = 10": a trailing h made any hex-looking word a number. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"AH", "BH", "ch", "each", "DB", "cafe"})
    void registersAndLabelsAreNotNumbers(String word) {
        var hover = new QueryHover().execute(word, "en", index);
        assertTrue(hover.isEmpty() || hover.get().kind() != HoverKind.NUMBER_CONVERSION, word + " -> " + hover);
    }

    @Test
    void numbersCarryTheirValueForTheUserInterface() {
        var hover = new QueryHover().execute("0Ah", "es", index).orElseThrow();
        assertEquals(HoverKind.NUMBER_CONVERSION, hover.kind());
        assertEquals(10L, hover.number());
        assertEquals("0xA = 10", hover.syntax());
    }

    @Test
    void queryOutlineBuildsHierarchy() {
        QueryOutline outline = new QueryOutline();
        String code = """
                .code
                main proc
                step1:
                    nop
                step2:
                    nop
                main endp
                """;

        List<OutlineItem> items = outline.execute(code);
        assertFalse(items.isEmpty());

        OutlineItem mainProc = items.stream()
                .filter(i -> i.name().equals("main"))
                .findFirst()
                .orElseThrow();

        assertEquals(2, mainProc.children().size());
        assertEquals("step1", mainProc.children().get(0).name());
        assertEquals("step2", mainProc.children().get(1).name());
    }

    @Test
    void lintSourceFindsMissingExit() {
        LintSource lint = new LintSource();
        String broken = """
                .code
                main proc
                    mov ax, 1
                main endp
                """;

        List<Diagnostic> diags = lint.execute(broken, "test.asm", "8086", index);
        assertTrue(diags.stream().anyMatch(d -> d.code().equals("lint.missing-exit")));
    }
}
