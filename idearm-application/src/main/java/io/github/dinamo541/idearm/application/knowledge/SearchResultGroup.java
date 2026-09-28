package io.github.dinamo541.idearm.application.knowledge;

import java.util.Collections;
import java.util.List;

/**
 * Group of search results categorized by entity kind.
 */
public record SearchResultGroup(
        KnowledgeEntityKind kind,
        List<KnowledgeSearchResult> items
) {
    public SearchResultGroup {
        items = items != null ? Collections.unmodifiableList(items) : List.of();
    }
}
