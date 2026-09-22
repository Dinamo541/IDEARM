package io.github.dinamo541.idearm.language.model;

import java.util.Objects;

/**
 * AST node representing an include directive (e.g. {@code INCLUDE MACROS.INC}).
 */
public record IncludeNode(
        String path,
        int line,
        int column
) implements AstNode {
    public IncludeNode {
        Objects.requireNonNull(path, "path cannot be null");
    }
}
