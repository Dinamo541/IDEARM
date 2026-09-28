package io.github.dinamo541.idearm.language.linter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnknownInstructionRuleTest {

    private final AssemblyLinter linter = new AssemblyLinter(List.of(new UnknownInstructionRule()));
    private final ProjectSymbolIndex index = new ProjectSymbolIndex();

    private List<Diagnostic> lint(String source) {
        return linter.lint(source, "main.asm", "8086", index);
    }

    @Test
    void reportsAMistypedMnemonicAndSuggestsTheRealOne() {
        List<Diagnostic> diagnostics = lint("""
                .code
                main proc
                  MUV bx, 1
                main endp
                end main
                """);

        assertEquals(1, diagnostics.size());
        Diagnostic diagnostic = diagnostics.getFirst();
        assertEquals("lint.unknown-instruction-suggestion", diagnostic.code());
        assertEquals(List.of("MUV", "MOV"), diagnostic.arguments());
        assertEquals(Severity.ERROR, diagnostic.severity(),
                "no assembler accepts the word, so the file will not build; this is what marks it red");
    }

    @Test
    void marksExactlyTheMistypedWord() {
        List<Diagnostic> diagnostics = lint("  ADDD cx, 2\n");

        assertEquals(1, diagnostics.size());
        var location = diagnostics.getFirst().location();
        assertEquals(1, location.line());
        assertEquals(3, location.column());
        assertEquals(4, location.length());
    }

    @Test
    void reportsAWordNothingIsCloseTo() {
        List<Diagnostic> diagnostics = lint("  printmsg ax\n");

        assertEquals(1, diagnostics.size());
        assertEquals("lint.unknown-instruction", diagnostics.getFirst().code());
        assertEquals(List.of("PRINTMSG"), diagnostics.getFirst().arguments());
    }

    @Test
    void acceptsEveryInstructionTheCatalogKnows() {
        // BSWAP, SAL, RETF, MOVS and the REP prefix are all real mnemonics that used to fall outside the lexer list.
        assertTrue(lint("""
                  mov ax, bx
                  bswap eax
                  sal ax, 1
                  rep movsb
                  movs byte ptr es:[di], byte ptr ds:[si]
                  retf
                """).isEmpty());
    }

    @Test
    void acceptsTheFloatingPointAndConditionalFamilies() {
        // The catalog describes x87, SETcc and CMOVcc, so their members are recognised instead of being skipped.
        assertTrue(lint("""
                  fadd st(1)
                  fstp qword ptr [x]
                  fldpi
                  fstsw ax
                  setz al
                  setnbe bl
                  cmova ax, bx
                  cmovnle cx, dx
                """).isEmpty());
    }

    @Test
    void reportsATypoInsideThoseFamiliesNowThatTheyAreDescribed() {
        // What the old uncatalogued-family guard had to let through: a misspelling that merely looks like an x87
        // or a conditional mnemonic is a typo like any other.
        List<Diagnostic> floating = lint("  FADDD st(1)\n");
        assertEquals(1, floating.size());
        assertEquals("lint.unknown-instruction-suggestion", floating.getFirst().code());
        assertEquals(List.of("FADDD", "FADD"), floating.getFirst().arguments());

        List<Diagnostic> conditional = lint("  SETZZ al\n");
        assertEquals(1, conditional.size());
        assertEquals("lint.unknown-instruction-suggestion", conditional.getFirst().code());
        assertTrue(conditional.getFirst().arguments().get(1).startsWith("SET"),
                "the hint should point at a real SETcc: " + conditional.getFirst().arguments());
    }

    @Test
    void doesNotReportAMacroDefinedInTheSameFile() {
        assertTrue(lint("""
                showmsg macro
                  int 21h
                endm
                .code
                  showmsg
                """).isEmpty());
    }

    @Test
    void doesNotReportLabelsConstantsDataOrDeclarations() {
        assertTrue(lint("""
                .data
                msg db 'hola$'
                count equ 10
                buffer label byte
                total = 0
                .code
                main proc
                start:
                  jmp start
                main endp
                end main
                """).isEmpty());
    }

    @Test
    void doesNotReportAWordThatLooksLikeAnIdentifier() {
        // An underscore, an @@ local label or a dot is how course code names its own things, never a mnemonic.
        assertTrue(lint("""
                  helper_routine
                  @@again
                """).isEmpty());
    }

    @Test
    void doesNotReportASymbolDefinedElsewhereInTheProject() {
        ProjectSymbolIndex projectIndex = new ProjectSymbolIndex();
        projectIndex.updateFile("util.asm", """
                .code
                cleanup proc
                  ret
                cleanup endp
                """);

        List<Diagnostic> diagnostics = new AssemblyLinter(List.of(new UnknownInstructionRule()))
                .lint("  cleanup\n", "main.asm", "8086", projectIndex);

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void findsATypoAfterALabelOnTheSameLine() {
        List<Diagnostic> diagnostics = lint("start: MVO dx, 3\n");

        assertEquals(1, diagnostics.size());
        assertEquals(8, diagnostics.getFirst().location().column());
        assertEquals(List.of("MVO", "MOV"), diagnostics.getFirst().arguments());
    }

    @Test
    void reportsNothingForAnEmptyDocument() {
        assertTrue(lint("").isEmpty());
        assertFalse(lint("; only a comment\n").iterator().hasNext());
    }
}
