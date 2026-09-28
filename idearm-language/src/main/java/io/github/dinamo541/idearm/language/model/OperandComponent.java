package io.github.dinamo541.idearm.language.model;

import java.util.Objects;

/**
 * An individual textual component within a parsed operand with position tracking.
 */
public record OperandComponent(
        String text,
        int line,
        int startColumn,
        int endColumn,
        Role role
) {
    public OperandComponent {
        Objects.requireNonNull(text, "text cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
    }

    public enum Role {
        SEGMENT_OVERRIDE,
        SIZE_PREFIX,
        BRACKET_OPEN,
        BRACKET_CLOSE,
        BASE_REGISTER,
        INDEX_REGISTER,
        SCALE,
        DISPLACEMENT,
        OPERATOR,
        SYMBOL,
        REGISTER,
        IMMEDIATE,
        UNKNOWN
    }
}
