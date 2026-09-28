package io.github.dinamo541.idearm.language.model;

import java.util.Locale;
import java.util.Objects;

/**
 * A register operand (e.g. {@code AX}, {@code EBX}, {@code CL}).
 */
public record RegisterOperand(
        String rawText,
        int line,
        int startColumn,
        int endColumn,
        String registerName,
        int bitSize
) implements ParsedOperand {
    public RegisterOperand {
        Objects.requireNonNull(rawText, "rawText cannot be null");
        Objects.requireNonNull(registerName, "registerName cannot be null");
    }

    @Override
    public OperandKind kind() {
        return OperandKind.REGISTER;
    }

    public String normalizedRegister() {
        return registerName.toUpperCase(Locale.ROOT);
    }
}
