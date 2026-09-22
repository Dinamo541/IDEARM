package io.github.dinamo541.idearm.emu8086.debug;

import java.util.Objects;

/**
 * Correlates a source file path and a 1-based line number.
 */
public record SourceLocation(String file, int line) {
    public SourceLocation {
        Objects.requireNonNull(file, "file cannot be null");
    }
}
