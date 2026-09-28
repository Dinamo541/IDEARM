package io.github.dinamo541.idearm.language.model;

import java.util.List;
import java.util.Objects;

/**
 * AST node representing an executed instruction statement (e.g. {@code MOV AX, @DATA}).
 */
public record InstructionNode(
        String mnemonic,
        List<String> operands,
        int line,
        int column,
        String comment,
        List<ParsedOperand> parsedOperands
) implements AstNode {
    public InstructionNode {
        Objects.requireNonNull(mnemonic, "mnemonic cannot be null");
        operands = operands != null ? List.copyOf(operands) : List.of();
        parsedOperands = parsedOperands != null ? List.copyOf(parsedOperands) : List.of();
    }

    public InstructionNode(String mnemonic, List<String> operands, int line, int column, String comment) {
        this(mnemonic, operands, line, column, comment, List.of());
    }
}
