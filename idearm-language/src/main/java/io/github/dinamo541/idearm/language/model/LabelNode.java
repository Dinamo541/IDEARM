package io.github.dinamo541.idearm.language.model;

import java.util.Objects;

/**
 * AST node representing a label definition (e.g. {@code entrada:}).
 */
public record LabelNode(
        String name,
        int line,
        int column,
        boolean isLocal
) implements AstNode {
    public LabelNode {
        Objects.requireNonNull(name, "name cannot be null");
    }
}
