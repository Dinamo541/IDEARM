package io.github.dinamo541.idearm.language.model;

import java.util.Objects;

/**
 * AST node representing a procedure definition (e.g. {@code main PROC ... main ENDP}).
 */
public record ProcedureNode(
        String name,
        int line,
        int column,
        int endLine,
        boolean isFar,
        boolean isPublic
) implements AstNode {
    public ProcedureNode {
        Objects.requireNonNull(name, "name cannot be null");
    }
}
