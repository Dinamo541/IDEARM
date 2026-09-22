package io.github.dinamo541.idearm.domain.diagnostic;
import java.util.Objects;

/** A source location; absent line/column are null, never invented as line zero. */
public record Location(String path, Integer line, Integer column) {
    public Location {
        Objects.requireNonNull(path);
        if (line != null && line < 1 || column != null && column < 1) {
            throw new IllegalArgumentException("Source line and column must be positive.");
        }
    }
}
