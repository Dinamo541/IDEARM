package io.github.dinamo541.idearm.language.knowledge;

import java.util.Collections;
import java.util.List;

/**
 * Syntax, directive, punctuation, operator, or preprocessor token entity.
 */
public record SyntaxItem(
        String id,
        String token,
        SyntaxClass syntaxClass,
        List<DialectSupport> dialects,
        List<String> contexts,
        boolean notation,
        boolean assemblable,
        List<String> contrastWith,
        String summaryEn,
        String summaryEs,
        String descriptionEn,
        String descriptionEs,
        String example,
        List<String> sources
) {
    public SyntaxItem {
        dialects = dialects != null ? Collections.unmodifiableList(dialects) : List.of();
        contexts = contexts != null ? Collections.unmodifiableList(contexts) : List.of();
        contrastWith = contrastWith != null ? Collections.unmodifiableList(contrastWith) : List.of();
        sources = sources != null ? Collections.unmodifiableList(sources) : List.of();
        summaryEn = summaryEn != null ? summaryEn : "";
        summaryEs = summaryEs != null ? summaryEs : "";
        descriptionEn = descriptionEn != null ? descriptionEn : "";
        descriptionEs = descriptionEs != null ? descriptionEs : "";
        example = example != null ? example : "";
    }

    public boolean supportsDialect(Dialect dialect) {
        if (dialect == null || dialect == Dialect.COMMON || dialect == Dialect.UNKNOWN) return true;
        return dialects.stream().anyMatch(d -> d.dialect() == dialect || d.dialect() == Dialect.COMMON);
    }
}
