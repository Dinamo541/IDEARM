package io.github.dinamo541.idearm.language.index;

import java.util.List;
import java.util.Objects;

/**
 * All definitions and references within a single source file.
 */
public record FileSymbols(
        String filePath,
        List<SymbolDefinition> definitions,
        List<SymbolReference> references
) {
    public FileSymbols {
        Objects.requireNonNull(filePath, "filePath cannot be null");
        definitions = definitions != null ? List.copyOf(definitions) : List.of();
        references = references != null ? List.copyOf(references) : List.of();
    }
}
