package io.github.dinamo541.idearm.application.editor;

import io.github.dinamo541.idearm.language.catalog.CpuLevel;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.index.SymbolDefinition;
import io.github.dinamo541.idearm.language.knowledge.CompatibilityContext;
import io.github.dinamo541.idearm.language.knowledge.CompletionView;
import io.github.dinamo541.idearm.language.knowledge.Corpus;
import io.github.dinamo541.idearm.language.knowledge.CpuGeneration;

import java.util.*;

/**
 * Use case: Computes contextual code completions based on prefix, target CPU/context,
 * locale, and project symbols.
 *
 * <p>Uses the Academic Knowledge Corpus completion view to provide candidates in the
 * active user interface language without hardcoded static lists.
 */
public final class QueryCompletion {

    public List<CompletionItem> execute(String prefix, String targetCpu, ProjectSymbolIndex index) {
        CompatibilityContext ctx = (targetCpu != null && !targetCpu.isBlank())
                ? CompatibilityContext.forCpu(CpuGeneration.parse(targetCpu))
                : null;
        return execute(prefix, ctx, Locale.ENGLISH, index);
    }

    public List<CompletionItem> execute(String prefix, CompatibilityContext context, Locale locale, ProjectSymbolIndex index) {
        String p = prefix != null ? prefix.trim().toUpperCase(Locale.ROOT) : "";
        Locale effectiveLocale = locale != null ? locale : Locale.ENGLISH;
        List<CompletionItem> results = new ArrayList<>();

        // 1. Query Corpus CompletionView (Instructions, Aliases, Registers, Directives)
        CompletionView completionView = Corpus.get().completionView();
        List<CompletionView.Candidate> candidates = completionView.candidates(prefix, context, effectiveLocale);

        for (CompletionView.Candidate cand : candidates) {
            CompletionKind kind = switch (cand.kind().toUpperCase(Locale.ROOT)) {
                case "INSTRUCTION", "ALIAS" -> CompletionKind.INSTRUCTION;
                case "REGISTER" -> CompletionKind.REGISTER;
                default -> CompletionKind.DIRECTIVE;
            };

            CpuLevel minCpu = CpuLevel.CPU_8086;
            if (kind == CompletionKind.INSTRUCTION) {
                var instrOpt = Corpus.get().findInstruction(cand.text());
                if (instrOpt.isPresent()) {
                    minCpu = instrOpt.get().minCpuGen().toCpuLevel();
                }
            }

            results.add(new CompletionItem(
                    cand.text(),
                    cand.insertText(),
                    cand.description(),
                    cand.description(),
                    kind,
                    minCpu
            ));
        }

        // 2. Project symbols from index
        if (index != null) {
            for (SymbolDefinition def : index.findDefinitionsStartingWith(p)) {
                CompletionKind kind = switch (def.kind()) {
                    case PROCEDURE -> CompletionKind.PROCEDURE;
                    case LABEL -> CompletionKind.LABEL;
                    case VARIABLE -> CompletionKind.VARIABLE;
                    case CONSTANT -> CompletionKind.CONSTANT;
                    default -> CompletionKind.LABEL;
                };
                results.add(CompletionItem.of(def.name(), kind, def.signature()));
            }
        }

        // Sort: exact matches first, then by kind, then alphabetically
        results.sort(Comparator.comparing((CompletionItem item) -> !item.label().equalsIgnoreCase(p))
                .thenComparing(CompletionItem::kind)
                .thenComparing(CompletionItem::label));

        return List.copyOf(results);
    }
}
