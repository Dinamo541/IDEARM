package io.github.dinamo541.idearm.language.knowledge;

import java.util.Collections;
import java.util.List;

/**
 * Architectural, mathematical, or conceptual topic (e.g. real-mode segmentation, effective address, two's complement).
 */
public record ConceptEntry(
        String id,
        String titleEn,
        String titleEs,
        String category,
        PedagogicalLevel level,
        List<String> prerequisites,
        List<String> relatedInstructions,
        List<String> relatedConcepts,
        String summaryEn,
        String summaryEs,
        String contentEn,
        String contentEs,
        List<String> sources
) {
    public ConceptEntry {
        prerequisites = prerequisites != null ? Collections.unmodifiableList(prerequisites) : List.of();
        relatedInstructions = relatedInstructions != null ? Collections.unmodifiableList(relatedInstructions) : List.of();
        relatedConcepts = relatedConcepts != null ? Collections.unmodifiableList(relatedConcepts) : List.of();
        sources = sources != null ? Collections.unmodifiableList(sources) : List.of();
    }
}
