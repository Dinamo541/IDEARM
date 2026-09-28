package io.github.dinamo541.idearm.language.linter;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CpuBaselineRuleTest {

    private final AssemblyLinter linter = new AssemblyLinter();
    private final ProjectSymbolIndex index = new ProjectSymbolIndex();

    @Test
    void imulWithThreeOperandsWarnsOn8086AndPassesOn80186() {
        String code = """
                .code
                imul ax, bx, 3
                """;

        List<Diagnostic> diag8086 = linter.lint(code, "test.asm", "8086", index);
        assertTrue(diag8086.stream().anyMatch(d -> "lint.cpu-baseline".equals(d.code()) && d.message().contains("IMUL")),
                "IMUL with 3 operands must warn on 8086");

        List<Diagnostic> diag186 = linter.lint(code, "test.asm", "80186", index);
        assertFalse(diag186.stream().anyMatch(d -> "lint.cpu-baseline".equals(d.code()) && d.message().contains("IMUL")),
                "IMUL with 3 operands must pass on 80186+");
    }

    @Test
    void iretdWarnsOn8086() {
        String code = """
                .code
                iretd
                """;

        List<Diagnostic> diags = linter.lint(code, "test.asm", "8086", index);
        assertTrue(diags.stream().anyMatch(d -> "lint.cpu-baseline".equals(d.code()) && d.message().contains("IRETD")),
                "IRETD must warn on 8086 as requiring 80386+");
    }

    @Test
    void shlWithEquConstantOneDoesNotWarnOn8086() {
        String code = """
                CUENTA EQU 1
                .code
                shl ax, CUENTA
                """;

        List<Diagnostic> diags = linter.lint(code, "test.asm", "8086", index);
        assertFalse(diags.stream().anyMatch(d -> "lint.cpu-baseline".equals(d.code()) && d.message().contains("SHL")),
                "SHL ax, CUENTA with CUENTA EQU 1 must not produce a CPU baseline warning on 8086");
    }

    @Test
    void pushOffsetWarnsOn8086AsImmediate() {
        String code = """
                .data
                msg db 'hi', 0
                .code
                push offset msg
                """;

        List<Diagnostic> diags = linter.lint(code, "test.asm", "8086", index);
        assertTrue(diags.stream().anyMatch(d -> d.code().startsWith("lint.cpu-baseline") && d.message().contains("immediate")),
                "PUSH offset msg must warn on 8086 because immediate pushes require 80186+");
    }

    @Test
    void fsAndGsDiagnosedAsSegmentRegistersNot32BitRegisters() {
        String code = """
                .code
                mov ax, fs
                """;

        List<Diagnostic> diags = linter.lint(code, "test.asm", "8086", index);
        assertTrue(diags.stream().anyMatch(d -> d.code().startsWith("lint.cpu-baseline")
                        && d.message().contains("Segment register 'FS'")
                        && !d.message().contains("32-bit register")),
                "FS/GS violation message must call them segment registers, not 32-bit registers");
    }

    @Test
    void longModeDetectsInvalidInstructions() {
        String code = """
                .code
                pusha
                aaa
                """;

        List<Diagnostic> diags = linter.lint(code, "test.asm", "x86-64", index);
        assertTrue(diags.stream().anyMatch(d -> "lint.long-mode-invalid".equals(d.code()) && d.message().contains("PUSHA") && d.message().contains("64-bit")),
                "PUSHA must be detected as invalid in 64-bit long mode");
        assertTrue(diags.stream().anyMatch(d -> "lint.long-mode-invalid".equals(d.code()) && d.message().contains("AAA") && d.message().contains("64-bit")),
                "AAA must be detected as invalid in 64-bit long mode");
    }

    /** The first "EQU" in FREQUENCY EQU 1 is inside the name, so the count read as "ENCY EQU 1". */
    @Test
    void aConstantWhoseNameContainsEquResolvesItsValue() {
        var otherFile = new ProjectSymbolIndex();
        otherFile.updateFile("consts.asm", """
                FREQUENCY EQU 1
                """);
        String code = """
                .code
                shl ax, FREQUENCY
                """;
        List<Diagnostic> diags = linter.lint(code, "test.asm", "8086", otherFile);
        assertFalse(diags.stream().anyMatch(d -> "lint.cpu-baseline-shift".equals(d.code())), diags.toString());
    }

    @Test
    void pushingANegativeImmediateWarnsOn8086() {
        String code = """
                .code
                push -1
                """;
        List<Diagnostic> diags = linter.lint(code, "test.asm", "8086", index);
        assertTrue(diags.stream().anyMatch(d -> "lint.cpu-baseline-push".equals(d.code())), diags.toString());
    }

    @Test
    void cpuNamesWithAnIAreRecognised() {
        assertEquals(6, io.github.dinamo541.idearm.language.catalog.CpuLevel.parseLevel("Pentium Pro"));
        assertEquals(6, io.github.dinamo541.idearm.language.catalog.CpuLevel.parseLevel("Pentium II"));
        assertEquals(5, io.github.dinamo541.idearm.language.catalog.CpuLevel.parseLevel("pentium"));
        assertEquals(3, io.github.dinamo541.idearm.language.catalog.CpuLevel.parseLevel("i386"));
        assertEquals(io.github.dinamo541.idearm.language.knowledge.CpuGeneration.P6,
                io.github.dinamo541.idearm.language.knowledge.CpuGeneration.parse("Pentium II"));
    }
}
