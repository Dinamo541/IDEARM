package io.github.dinamo541.idearm.language.linter;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.model.SourceFileNode;

import java.util.List;

/**
 * Interface for educational linting rules in Assembly source files.
 */
public interface LintRule {
    List<Diagnostic> check(SourceFileNode ast, String filePath, String targetCpu, ProjectSymbolIndex index);
}
