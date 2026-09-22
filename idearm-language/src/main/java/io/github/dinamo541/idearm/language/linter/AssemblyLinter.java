package io.github.dinamo541.idearm.language.linter;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.model.SourceFileNode;
import io.github.dinamo541.idearm.language.parser.AssemblyParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Educational linter for Assembly codebases. Runs domain-level checks to catch common student errors
 * (e.g., missing program termination, instruction/CPU baseline mismatches, undefined symbols).
 */
public final class AssemblyLinter {

    private final List<LintRule> rules;
    private final AssemblyParser parser;

    public AssemblyLinter() {
        this(List.of(
                new MissingTerminationRule(),
                new CpuBaselineRule(),
                new UndefinedSymbolRule()
        ));
    }

    public AssemblyLinter(List<LintRule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules cannot be null"));
        this.parser = new AssemblyParser();
    }

    public List<Diagnostic> lint(String sourceText, String filePath, String targetCpu, ProjectSymbolIndex index) {
        if (sourceText == null || sourceText.isBlank()) return List.of();
        SourceFileNode ast = parser.parse(sourceText);
        return lint(ast, filePath, targetCpu, index);
    }

    public List<Diagnostic> lint(SourceFileNode ast, String filePath, String targetCpu, ProjectSymbolIndex index) {
        if (ast == null) return List.of();
        List<Diagnostic> diagnostics = new ArrayList<>();

        for (LintRule rule : rules) {
            diagnostics.addAll(rule.check(ast, filePath, targetCpu, index));
        }

        return List.copyOf(diagnostics);
    }
}
