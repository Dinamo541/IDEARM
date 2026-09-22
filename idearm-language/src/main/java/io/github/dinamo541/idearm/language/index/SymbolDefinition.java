package io.github.dinamo541.idearm.language.index;

import java.util.Objects;

/**
 * Declared symbol in an Assembly file.
 */
public record SymbolDefinition(
        String name,
        SymbolKind kind,
        String filePath,
        int line,
        int column,
        String signature,
        String docComment
) {
    public SymbolDefinition {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        Objects.requireNonNull(filePath, "filePath cannot be null");
    }
}
