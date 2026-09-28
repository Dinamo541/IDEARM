package io.github.dinamo541.idearm.language.linter;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.language.knowledge.Dialect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that real x86/x64 instructions are never erroneously flagged as typos
 * by UnknownInstructionRule (AA-P1-04), while genuine misspellings continue to trigger actionable diagnostics.
 */
class UnknownInstructionFalsePositiveTest {

    @Test
    void realInstructionsInNasmDoNotProduceUnknownInstructionDiagnostics() {
        AssemblyLinter linter = new AssemblyLinter(Dialect.NASM);
        String code = """
                section .text
                movaps xmm0, xmm1
                paddb mm0, mm1
                fsin
                lgdt [rax]
                movsq
                """;

        List<Diagnostic> diagnostics = linter.lint(code, "test.asm", "x86_64", null);
        List<Diagnostic> unknownDiagnostics = diagnostics.stream()
                .filter(d -> d.code().startsWith("lint.unknown-instruction"))
                .toList();

        assertTrue(unknownDiagnostics.isEmpty(),
                "Real 64-bit/x86 instructions (movaps, paddb, fsin, lgdt, movsq) must not produce unknown instruction error: "
                        + unknownDiagnostics);
    }

    @Test
    void genuineTyposProduceErrorDiagnosticsWithSuggestions() {
        AssemblyLinter linter = new AssemblyLinter(Dialect.NASM);
        String code = """
                section .text
                muv ax, 1
                addd bx, 2
                """;

        List<Diagnostic> diagnostics = linter.lint(code, "test.asm", "8086", null);
        List<Diagnostic> unknownDiagnostics = diagnostics.stream()
                .filter(d -> d.code().startsWith("lint.unknown-instruction"))
                .toList();

        assertEquals(2, unknownDiagnostics.size(), "Both typos should be reported");

        Diagnostic muvDiag = unknownDiagnostics.stream()
                .filter(d -> d.rawLine().equalsIgnoreCase("muv"))
                .findFirst()
                .orElseThrow();
        assertEquals(Severity.ERROR, muvDiag.severity());
        assertTrue(muvDiag.message().contains("MOV"));

        Diagnostic adddDiag = unknownDiagnostics.stream()
                .filter(d -> d.rawLine().equalsIgnoreCase("addd"))
                .findFirst()
                .orElseThrow();
        assertEquals(Severity.ERROR, adddDiag.severity());
        assertTrue(adddDiag.message().contains("ADD"));
    }

    @Test
    void realAssemblerMnemonicsCoverTasmMasmAndNasmWithoutFalsePositives() {
        for (Dialect dialect : List.of(Dialect.TASM, Dialect.MASM, Dialect.NASM)) {
            AssemblyLinter linter = new AssemblyLinter(dialect);
            String code = """
                    bswap eax
                    popcnt eax, ebx
                    clflush [rax]
                    rdseed eax
                    """;
            List<Diagnostic> diagnostics = linter.lint(code, "test.asm", "x86_64", null);
            List<Diagnostic> unknown = diagnostics.stream()
                    .filter(d -> d.code().startsWith("lint.unknown-instruction"))
                    .toList();
            assertTrue(unknown.isEmpty(), "Dialect " + dialect + " produced false unknown instruction errors: " + unknown);
        }
    }
}
