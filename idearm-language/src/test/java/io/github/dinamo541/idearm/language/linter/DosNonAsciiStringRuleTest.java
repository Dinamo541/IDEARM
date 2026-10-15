package io.github.dinamo541.idearm.language.linter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.parser.AssemblyParser;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DosNonAsciiStringRuleTest {

    private DosNonAsciiStringRule rule;
    private AssemblyParser parser;
    private ProjectSymbolIndex index;

    @BeforeEach
    void setUp() {
        rule = new DosNonAsciiStringRule();
        parser = new AssemblyParser();
        index = new ProjectSymbolIndex();
    }

    @Test
    void warnsOnNonAsciiInStringLiteralDataDefinition() {
        String code = """
                .DATA
                msg DB 'Hola año$'
                """;
        var ast = parser.parse(code);
        List<Diagnostic> diags = rule.check(ast, "main.asm", "8086", index);

        assertEquals(1, diags.size());
        Diagnostic d = diags.getFirst();
        assertEquals(DosNonAsciiStringRule.CODE, d.code());
        assertEquals(Severity.WARNING, d.severity());
        assertEquals("'Hola año$'", d.rawLine());
        assertEquals(2, d.location().line());
    }

    @Test
    void warnsOnNonAsciiInDoubleQuotedStringLiteral() {
        String code = """
                .DATA
                msg DB "¿Cómo estás?$"
                """;
        var ast = parser.parse(code);
        List<Diagnostic> diags = rule.check(ast, "main.asm", "8086", index);

        assertEquals(1, diags.size());
        assertEquals("\"¿Cómo estás?$\"", diags.getFirst().rawLine());
    }

    @Test
    void warnsOnNonAsciiInInstructionOperand() {
        String code = """
                .CODE
                MOV AL, 'ñ'
                """;
        var ast = parser.parse(code);
        List<Diagnostic> diags = rule.check(ast, "main.asm", "8086", index);

        assertEquals(1, diags.size());
        assertEquals("'ñ'", diags.getFirst().rawLine());
        assertEquals(2, diags.getFirst().location().line());
    }

    @Test
    void warnsOnNonAsciiInConstantDefinition() {
        String code = """
                LETRA EQU 'á'
                """;
        var ast = parser.parse(code);
        List<Diagnostic> diags = rule.check(ast, "main.asm", "8086", index);

        assertEquals(1, diags.size());
        assertEquals("'á'", diags.getFirst().rawLine());
    }

    @Test
    void noWarningOnPureAsciiStrings() {
        String code = """
                .DATA
                msg DB 'Hello world$', 13, 10
                .CODE
                MOV AL, 'A'
                """;
        var ast = parser.parse(code);
        List<Diagnostic> diags = rule.check(ast, "main.asm", "8086", index);

        assertTrue(diags.isEmpty());
    }

    @Test
    void noWarningOnAccentsInComments() {
        String code = """
                ; Cálculo del año en español: año 2026
                .DATA
                msg DB 'Hello$'
                .CODE
                ; Fin del código
                """;
        var ast = parser.parse(code);
        List<Diagnostic> diags = rule.check(ast, "main.asm", "8086", index);

        assertTrue(diags.isEmpty());
    }

    @Test
    void noWarningWhenCharsetIsCp437OrCp850() {
        String code = """
                .DATA
                msg DB 'Hola año$'
                """;
        var ast = parser.parse(code);
        Charset cp437 = Charset.forName("IBM437");
        Charset cp850 = Charset.forName("IBM850");

        List<Diagnostic> diags437 = rule.check(ast, "main.asm", "8086", index, cp437);
        assertTrue(diags437.isEmpty());

        List<Diagnostic> diags850 = rule.check(ast, "main.asm", "8086", index, cp850);
        assertTrue(diags850.isEmpty());
    }

    @Test
    void customPredicateControlsUtf8Detection() {
        DosNonAsciiStringRule nonUtf8Rule = new DosNonAsciiStringRule(path -> false);
        String code = """
                .DATA
                msg DB 'Hola año$'
                """;
        var ast = parser.parse(code);

        List<Diagnostic> diags = nonUtf8Rule.check(ast, "main.asm", "8086", index);
        assertTrue(diags.isEmpty());
    }

    @Test
    void integratedWithAssemblyLinterDefaultRules() {
        AssemblyLinter linter = new AssemblyLinter();
        String code = """
                .MODEL small
                .STACK 100h
                .DATA
                msg DB 'Año nuevo$'
                .CODE
                main PROC
                    MOV AX, @DATA
                    MOV DS, AX
                    MOV AH, 4Ch
                    INT 21h
                main ENDP
                END main
                """;
        List<Diagnostic> diags = linter.lint(code, "main.asm", "8086", index, StandardCharsets.UTF_8);
        boolean hasDosAccentWarn = diags.stream().anyMatch(d -> d.code().equals(DosNonAsciiStringRule.CODE));
        assertTrue(hasDosAccentWarn);

        List<Diagnostic> diagsCp437 = linter.lint(code, "main.asm", "8086", index, Charset.forName("IBM437"));
        boolean hasDosAccentWarnCp437 = diagsCp437.stream().anyMatch(d -> d.code().equals(DosNonAsciiStringRule.CODE));
        assertFalse(hasDosAccentWarnCp437);
    }
}
