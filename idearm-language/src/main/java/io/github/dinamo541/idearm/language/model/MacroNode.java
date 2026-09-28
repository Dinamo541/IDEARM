package io.github.dinamo541.idearm.language.model;

import java.util.Objects;

/**
 * AST node representing a macro definition (e.g. {@code print_str MACRO texto}).
 *
 * <p>Only the name is kept, because the rules that need it only ask whether a word is a macro of this file: a macro
 * invocation looks exactly like a mistyped instruction, and without this node every invocation would be reported.
 */
public record MacroNode(
        String name,
        int line,
        int column
) implements AstNode {
    public MacroNode {
        Objects.requireNonNull(name, "name cannot be null");
    }
}
