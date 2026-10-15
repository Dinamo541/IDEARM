package io.github.dinamo541.idearm.application.editor;

import java.util.List;
import java.util.Objects;

/**
 * Node in the hierarchical document outline tree.
 *
 * <p>{@code endLine} is the last line the symbol covers: ENDP for a procedure, ENDS for a full segment. A label, and
 * a simplified segment such as {@code .CODE} that simply runs until the next one, end on their own line.
 */
public record OutlineItem(
        String name,
        String kind,
        int line,
        int column,
        List<OutlineItem> children,
        int endLine
) {
    public OutlineItem {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        children = children != null ? List.copyOf(children) : List.of();
        endLine = Math.max(endLine, line);
    }

    /** A symbol that covers only the line it is declared on. */
    public OutlineItem(String name, String kind, int line, int column, List<OutlineItem> children) {
        this(name, kind, line, column, children, line);
    }
}
