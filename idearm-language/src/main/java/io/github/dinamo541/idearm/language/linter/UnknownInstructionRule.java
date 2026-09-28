package io.github.dinamo541.idearm.language.linter;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.language.catalog.InstructionCatalog;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.model.AstNode;
import io.github.dinamo541.idearm.language.model.ConstantNode;
import io.github.dinamo541.idearm.language.model.DataNode;
import io.github.dinamo541.idearm.language.model.LabelNode;
import io.github.dinamo541.idearm.language.model.MacroNode;
import io.github.dinamo541.idearm.language.model.ProcedureNode;
import io.github.dinamo541.idearm.language.model.SourceFileNode;
import io.github.dinamo541.idearm.language.model.UnknownStatementNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Educational lint rule: reports a word written where an instruction belongs that no instruction is called, such as
 * {@code MUV AX, 1} for {@code MOV AX, 1}, and suggests the closest real mnemonic.
 *
 * <p>The severity is {@code ERROR}: once the guards below have cleared, the word is not a macro, a label or a
 * symbol of this project, so no assembler will accept it and the file will not build. That is also what marks it
 * in red in the editor, the way every IDE marks a misspelling.
 *
 * <p>The word is only reported once every guard has been cleared. Each guard exists because of a case real course
 * code contains, and they all err on the side of silence: a missed typo costs a student one build, while a report
 * on correct code teaches them to distrust the editor.
 */
public final class UnknownInstructionRule implements LintRule {

    /** Plain letters and digits only, 2 to 10 characters. Macros and labels overwhelmingly use _, @, $, ? or a dot. */
    private static final Pattern MNEMONIC_SHAPE = Pattern.compile("[A-Za-z][A-Za-z0-9]{1,9}");

    private final io.github.dinamo541.idearm.language.knowledge.Dialect dialect;

    public UnknownInstructionRule() {
        this(null);
    }

    public UnknownInstructionRule(io.github.dinamo541.idearm.language.knowledge.Dialect dialect) {
        this.dialect = dialect;
    }

    @Override
    public List<Diagnostic> check(SourceFileNode ast, String filePath, String targetCpu, ProjectSymbolIndex index) {
        List<UnknownStatementNode> candidates = ast.statements().stream()
                .filter(UnknownStatementNode.class::isInstance)
                .map(UnknownStatementNode.class::cast)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        Set<String> declaredHere = namesDeclaredIn(ast);
        List<Diagnostic> diagnostics = new ArrayList<>();

        for (UnknownStatementNode candidate : candidates) {
            String word = candidate.word();
            String upper = word.toUpperCase(Locale.ROOT);

            if (io.github.dinamo541.idearm.language.knowledge.Corpus.get().analysisView().isRecognized(upper, dialect)) continue;
            if (InstructionCatalog.isKnownInstruction(upper)) continue;
            if (io.github.dinamo541.idearm.language.knowledge.Corpus.get().getRecognizedAssemblerMnemonics(null).contains(upper)) {
                continue;
            }
            if (!MNEMONIC_SHAPE.matcher(word).matches()) continue;
            if (declaredHere.contains(upper)) continue;
            if (index != null && index.findDefinition(word).isPresent()) continue;

            List<String> suggestions = InstructionCatalog.suggest(upper);
            Location location = new Location(filePath, candidate.line(), candidate.column(), candidate.length());

            if (suggestions.isEmpty()) {
                diagnostics.add(new Diagnostic(
                        Severity.ERROR,
                        "lint.unknown-instruction",
                        "No instruction is called '" + upper + "'.",
                        location,
                        "idearm-linter",
                        word,
                        List.of(upper)
                ));
            } else {
                String suggestion = suggestions.getFirst();
                diagnostics.add(new Diagnostic(
                        Severity.ERROR,
                        "lint.unknown-instruction-suggestion",
                        "No instruction is called '" + upper + "'. Did you mean '" + suggestion + "'?",
                        location,
                        "idearm-linter",
                        word,
                        List.of(upper, suggestion)
                ));
            }
        }

        return List.copyOf(diagnostics);
    }

    /** Every name this file declares, upper case: a macro, label, procedure, constant or data name is not a typo. */
    private static Set<String> namesDeclaredIn(SourceFileNode ast) {
        Set<String> names = new HashSet<>();
        for (AstNode statement : ast.statements()) {
            if (statement instanceof MacroNode macro) {
                names.add(macro.name().toUpperCase(Locale.ROOT));
            }
        }
        for (LabelNode label : ast.labels()) {
            names.add(label.name().toUpperCase(Locale.ROOT));
        }
        for (ProcedureNode procedure : ast.procedures()) {
            names.add(procedure.name().toUpperCase(Locale.ROOT));
        }
        for (ConstantNode constant : ast.constants()) {
            names.add(constant.name().toUpperCase(Locale.ROOT));
        }
        for (DataNode data : ast.dataDefinitions()) {
            names.add(data.name().toUpperCase(Locale.ROOT));
        }
        return names;
    }
}
