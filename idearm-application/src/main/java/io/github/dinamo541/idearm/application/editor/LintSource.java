package io.github.dinamo541.idearm.application.editor;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.linter.AssemblyLinter;

import java.util.List;
import java.util.Objects;

/**
 * Use case: Executes educational and safety linter rules on an Assembly document.
 */
public final class LintSource {

    private final AssemblyLinter linter;

    public LintSource() {
        this(new AssemblyLinter());
    }

    public LintSource(AssemblyLinter linter) {
        this.linter = Objects.requireNonNull(linter, "linter cannot be null");
    }

    public List<Diagnostic> execute(String sourceText, String filePath, String targetCpu, ProjectSymbolIndex index) {
        return linter.lint(sourceText, filePath, targetCpu, index);
    }
}
