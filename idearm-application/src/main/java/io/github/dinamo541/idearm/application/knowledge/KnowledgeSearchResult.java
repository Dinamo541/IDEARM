package io.github.dinamo541.idearm.application.knowledge;

/**
 * An individual search result representing a matched knowledge entity.
 */
public record KnowledgeSearchResult(
        String id,
        String title,
        String subtitle,
        KnowledgeEntityKind kind,
        String summary,
        Object rawEntity,
        int score
) implements Comparable<KnowledgeSearchResult> {

    @Override
    public int compareTo(KnowledgeSearchResult other) {
        int cmp = Integer.compare(other.score, this.score);
        if (cmp != 0) return cmp;
        return this.title.compareToIgnoreCase(other.title);
    }
}
