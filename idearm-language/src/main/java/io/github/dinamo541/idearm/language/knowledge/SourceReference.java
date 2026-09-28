package io.github.dinamo541.idearm.language.knowledge;

public record SourceReference(
        String id,
        String kind,
        String author,
        String title,
        String edition,
        String url,
        String notes
) {
}
