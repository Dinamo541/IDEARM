package io.github.dinamo541.idearm.language.model;

import java.util.Objects;

/**
 * A symbol or label operand (e.g. {@code @data}, {@code my_var}, {@code buffer}).
 */
public record SymbolOperand(
        String rawText,
        int line,
        int startColumn,
        int endColumn,
        String symbolName
) implements ParsedOperand {
    public SymbolOperand {
        Objects.requireNonNull(rawText, "rawText cannot be null");
        Objects.requireNonNull(symbolName, "symbolName cannot be null");
    }

    @Override
    public OperandKind kind() {
        return OperandKind.SYMBOL;
    }
}
