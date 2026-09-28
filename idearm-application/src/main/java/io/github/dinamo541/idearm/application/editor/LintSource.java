package io.github.dinamo541.idearm.application.editor;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.linter.AssemblyLinter;
import io.github.dinamo541.idearm.language.linter.UnknownInstructionRule;

import java.util.List;
import java.util.Objects;

/**
 * Use case: Executes educational and safety linter rules on an Assembly document.
 *
 * <p>Which rules run depends on the target, because most of the educational rules are about DOS: warning a Linux
 * program that it never calls {@code INT 21h/4Ch} would be wrong. A mistyped mnemonic is the same mistake on every
 * target, so that rule always runs.
 */
public final class LintSource {

    private final AssemblyLinter dosLinter;
    private final AssemblyLinter portableLinter;

    public LintSource() {
        this(new AssemblyLinter());
    }

    public LintSource(AssemblyLinter linter) {
        this.dosLinter = Objects.requireNonNull(linter, "linter cannot be null");
        this.portableLinter = new AssemblyLinter(List.of(new UnknownInstructionRule()));
    }

    public List<Diagnostic> execute(String sourceText, String filePath, String targetCpu, ProjectSymbolIndex index) {
        return execute(sourceText, filePath, targetCpu, index, true);
    }

    /**
     * Lints one document.
     *
     * @param dosTarget whether the project builds for DOS, which decides whether the DOS-specific rules apply
     */
    public List<Diagnostic> execute(String sourceText, String filePath, String targetCpu, ProjectSymbolIndex index,
                                    boolean dosTarget) {
        AssemblyLinter linter = dosTarget ? dosLinter : portableLinter;
        return linter.lint(sourceText, filePath, targetCpu, index);
    }
}
