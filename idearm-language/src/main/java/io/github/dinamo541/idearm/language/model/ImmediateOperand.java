package io.github.dinamo541.idearm.language.model;

import java.util.Objects;

/**
 * An immediate constant operand (e.g. {@code 4}, {@code 10h}, {@code 'A'}).
 */
public record ImmediateOperand(
        String rawText,
        int line,
        int startColumn,
        int endColumn,
        Long numericValue,
        int bitSizeHint
) implements ParsedOperand {
    public ImmediateOperand {
        Objects.requireNonNull(rawText, "rawText cannot be null");
    }

    @Override
    public OperandKind kind() {
        return OperandKind.IMMEDIATE;
    }
}
