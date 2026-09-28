package io.github.dinamo541.idearm.application.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import java.util.List;
import org.junit.jupiter.api.Test;

class LintSourceTest {

    private final LintSource lintSource = new LintSource();
    private final ProjectSymbolIndex index = new ProjectSymbolIndex();

    private static final String DOS_PROGRAM_WITHOUT_AN_EXIT = """
            .model small
            .code
            main proc
              mov ax, @data
            main endp
            end main
            """;

    private static final String MISTYPED_MNEMONIC = """
            section .text
            global _start
            _start:
              MUV eax, 1
            """;

    @Test
    void appliesTheDosRulesToADosTarget() {
        List<Diagnostic> diagnostics = lintSource.execute(DOS_PROGRAM_WITHOUT_AN_EXIT, "main.asm", "8086", index, true);

        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("lint.missing-exit")));
    }

    @Test
    void doesNotApplyTheDosRulesToAnotherTarget() {
        // Warning a Linux program that it never returns to DOS would simply be wrong.
        List<Diagnostic> diagnostics = lintSource.execute(DOS_PROGRAM_WITHOUT_AN_EXIT, "main.asm", "80386", index, false);

        assertFalse(diagnostics.stream().anyMatch(d -> d.code().equals("lint.missing-exit")));
    }

    @Test
    void reportsAMistypedMnemonicOnEveryTarget() {
        for (boolean dosTarget : List.of(true, false)) {
            List<Diagnostic> diagnostics = lintSource.execute(MISTYPED_MNEMONIC, "main.asm", "80386", index, dosTarget);

            assertTrue(diagnostics.stream().anyMatch(d -> d.code().startsWith("lint.unknown-instruction")),
                    "a typo is the same mistake whatever the target, dosTarget=" + dosTarget);
        }
    }

    @Test
    void keepsTheDosRulesForCallersThatDoNotSayWhichTarget() {
        List<Diagnostic> diagnostics = lintSource.execute(DOS_PROGRAM_WITHOUT_AN_EXIT, "main.asm", "8086", index);

        assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("lint.missing-exit")));
    }
}
