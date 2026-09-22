package io.github.dinamo541.idearm.language.linter;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssemblyLinterTest {

    private final AssemblyLinter linter = new AssemblyLinter();
    private final ProjectSymbolIndex index = new ProjectSymbolIndex();

    @Test
    void warnsOnMissingProgramTermination() {
        String brokenCode = """
                .model small
                .code
                main proc
                    mov ax, 1
                    mov bx, 2
                main endp
                end main
                """;

        List<Diagnostic> diagnostics = linter.lint(brokenCode, "main.asm", "8086", index);
        assertFalse(diagnostics.isEmpty());
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("lint.missing-exit")));
    }

    @Test
    void acceptsCleanDosExit() {
        String goodCode = """
                .model small
                .code
                main proc
                    mov ax, @data
                    mov ds, ax
                    mov ax, 4C00h
                    int 21h
                main endp
                end main
                """;

        List<Diagnostic> diagnostics = linter.lint(goodCode, "main.asm", "8086", index);
        assertFalse(diagnostics.stream().anyMatch(d -> d.code().equals("lint.missing-exit")));
    }

    @Test
    void warnsOnCpuBaselineViolations() {
        String codeWith186 = """
                .model small
                .code
                main proc
                    pusha
                    shl ax, 3
                    mov ah, 4ch
                    int 21h
                main endp
                end main
                """;

        List<Diagnostic> diagnostics = linter.lint(codeWith186, "main.asm", "8086", index);
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("lint.cpu-baseline")));
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("lint.cpu-baseline-shift")));
    }

    @Test
    void warnsOnUndefinedJumpTarget() {
        String code = """
                .model small
                .code
                main proc
                    jmp non_existent_label
                    mov ah, 4ch
                    int 21h
                main endp
                end main
                """;

        List<Diagnostic> diagnostics = linter.lint(code, "main.asm", "8086", index);
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("lint.undefined-target")));
    }

    @Test
    void warnsOn32BitRegistersAnd386InstructionsOn8086() {
        String code = """
                .model small
                .code
                main proc
                    mov eax, 10
                    movzx bx, al
                    mov ah, 4ch
                    int 21h
                main endp
                end main
                """;

        List<Diagnostic> diagnostics = linter.lint(code, "main.asm", "8086", index);
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("lint.cpu-baseline-register32")),
                "Should warn on 32-bit register EAX on 8086 target");
        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("lint.cpu-baseline")),
                "Should warn on MOVZX requiring 80386+");
    }

    /** .EXIT is a directive; the rule never saw it and told students their program did not end. */
    @Test
    void dotExitEndsTheProgram() {
        String code = """
                .model small
                .stack 100h
                .code
                main proc
                    .startup
                    mov ax, 1
                    .exit
                main endp
                end main
                """;

        assertTrue(linter.lint(code, "main.asm", "8086", index).stream()
                .noneMatch(d -> d.code().equals("lint.missing-exit")));
    }

    /** "JMP SHORT fin" used to report "short fin" as an undefined label. */
    @Test
    void distanceKeywordsAreNotPartOfTheLabel() {
        String code = """
                .code
                main proc
                    jmp short fin
                    call near ptr rutina
                    jmp word ptr [bx]
                    jmp es:[di]
                    jmp $+2
                fin:
                    mov ax, 4C00h
                    int 21h
                main endp
                rutina proc
                    ret
                rutina endp
                """;

        List<Diagnostic> diagnostics = linter.lint(code, "main.asm", "8086", index);
        assertTrue(diagnostics.stream().noneMatch(d -> d.code().equals("lint.undefined-target")), diagnostics.toString());
    }

    @Test
    void aMisspelledLabelBehindShortIsStillReported() {
        String code = """
                .code
                main proc
                    jmp short fni
                fin:
                    mov ax, 4C00h
                    int 21h
                main endp
                """;

        var undefined = linter.lint(code, "main.asm", "8086", index).stream()
                .filter(d -> d.code().equals("lint.undefined-target")).toList();
        assertEquals(1, undefined.size());
        assertEquals(List.of("fni"), undefined.getFirst().arguments());
    }
}
