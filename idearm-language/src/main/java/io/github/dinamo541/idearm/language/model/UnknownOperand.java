package io.github.dinamo541.idearm.language.model;

import java.util.Objects;

/**
 * Fallback representation for unrecognised operand constructs.
 * Prevents parser crashes or erroneous diagnostics while preserving position spans.
 */
public record UnknownOperand(
        String rawText,
        int line,
        int startColumn,
        int endColumn
) implements ParsedOperand {
    public UnknownOperand {
        Objects.requireNonNull(rawText, "rawText cannot be null");
    }

    @Override
    public OperandKind kind() {
        return OperandKind.UNKNOWN;
    }
}
