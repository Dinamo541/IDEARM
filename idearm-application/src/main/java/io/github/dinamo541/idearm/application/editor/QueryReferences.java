package io.github.dinamo541.idearm.application.editor;

import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.index.SymbolReference;

import java.util.List;

/**
 * Use case: Locates all references to a symbol across the project.
 */
public final class QueryReferences {

    public List<Location> execute(String symbolName, ProjectSymbolIndex index) {
        if (symbolName == null || symbolName.isBlank() || index == null) {
            return List.of();
        }

        List<SymbolReference> refs = index.findReferences(symbolName.trim());
        return refs.stream()
                .map(r -> new Location(r.filePath(), r.line(), r.column()))
                .toList();
    }
}
