package io.github.dinamo541.idearm.language.model;

import java.util.Objects;

/**
 * AST node representing a constant definition (e.g. {@code BUFFER_SIZE EQU 256}).
 */
public record ConstantNode(
        String name,
        String value,
        int line,
        int column
) implements AstNode {
    public ConstantNode {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
    }
}
