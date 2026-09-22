package io.github.dinamo541.idearm.language.model;

import java.util.Objects;

/**
 * AST node representing a segment definition or directive (.CODE, .DATA, .STACK, or SEGMENT ... ENDS).
 */
public record SegmentNode(
        String name,
        String kind,
        int line,
        int column,
        int endLine
) implements AstNode {
    public SegmentNode {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
    }
}
