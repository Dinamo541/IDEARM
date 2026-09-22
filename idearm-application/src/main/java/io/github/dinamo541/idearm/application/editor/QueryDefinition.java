package io.github.dinamo541.idearm.application.editor;

import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.index.SymbolDefinition;

import java.util.Objects;
import java.util.Optional;

/**
 * Use case: Resolves the definition location for a symbol under cursor.
 */
public final class QueryDefinition {

    public Optional<Location> execute(String symbolName, ProjectSymbolIndex index) {
        if (symbolName == null || symbolName.isBlank() || index == null) {
            return Optional.empty();
        }

        Optional<SymbolDefinition> defOpt = index.findDefinition(symbolName.trim());
        return defOpt.map(def -> new Location(def.filePath(), def.line(), def.column()));
    }
}
