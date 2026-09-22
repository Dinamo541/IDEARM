package io.github.dinamo541.idearm.application.editor;

import java.util.List;
import java.util.Objects;

/**
 * Node in the hierarchical document outline tree.
 */
public record OutlineItem(
        String name,
        String kind,
        int line,
        int column,
        List<OutlineItem> children
) {
    public OutlineItem {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        children = children != null ? List.copyOf(children) : List.of();
    }
}
