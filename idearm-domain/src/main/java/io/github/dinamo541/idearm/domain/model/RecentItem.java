package io.github.dinamo541.idearm.domain.model;

import java.nio.file.Path;
import java.util.Objects;

/** A local project folder or file, ordered by its last successful opening. */
public record RecentItem(Kind kind, Path path) {
    public enum Kind { PROJECT, FILE }

    public RecentItem {
        Objects.requireNonNull(kind);
        path = Objects.requireNonNull(path).toAbsolutePath().normalize();
    }

    public String name() {
        return path.getFileName() == null ? path.toString() : path.getFileName().toString();
    }
}
