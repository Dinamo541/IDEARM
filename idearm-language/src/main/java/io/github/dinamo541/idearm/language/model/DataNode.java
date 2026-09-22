package io.github.dinamo541.idearm.language.model;

import java.util.Objects;

/**
 * AST node representing a data/variable declaration (e.g. {@code mensaje DB 'Hola Mundo', '$'}).
 */
public record DataNode(
        String name,
        String directive,
        String initialValue,
        int line,
        int column
) implements AstNode {
    public DataNode {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(directive, "directive cannot be null");
    }
}
