package io.github.dinamo541.idearm.language.index;

import java.util.Objects;

/**
 * Occurrence or reference to a symbol in an Assembly source file.
 */
public record SymbolReference(
        String name,
        String filePath,
        int line,
        int column
) {
    public SymbolReference {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(filePath, "filePath cannot be null");
    }
}
