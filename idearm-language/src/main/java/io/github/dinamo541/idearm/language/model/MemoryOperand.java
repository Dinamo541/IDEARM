package io.github.dinamo541.idearm.language.model;

import java.util.Objects;

/**
 * A memory addressing operand (e.g. {@code [bx+si+4]}, {@code ES:[di]}, {@code word ptr [bp-2]}).
 */
public record MemoryOperand(
        String rawText,
        int line,
        int startColumn,
        int endColumn,
        String segmentOverride,
        OperandComponent segmentComponent,
        String sizePrefix,
        OperandComponent sizeComponent,
        MemoryExpression expression
) implements ParsedOperand {
    public MemoryOperand {
        Objects.requireNonNull(rawText, "rawText cannot be null");
    }

    @Override
    public OperandKind kind() {
        return OperandKind.MEMORY;
    }

    public String effectiveSegment() {
        return segmentOverride != null ? segmentOverride : (expression != null ? expression.defaultSegment() : "DS");
    }
}
