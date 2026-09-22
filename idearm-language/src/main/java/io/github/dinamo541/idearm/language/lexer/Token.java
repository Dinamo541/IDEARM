package io.github.dinamo541.idearm.language.lexer;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable token with position tracking (1-based line and column).
 */
public record Token(
        TokenType type,
        String text,
        int line,
        int column,
        int length
) {
    public Token {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(text, "text cannot be null");
    }

    public boolean is(TokenType expectedType) {
        return this.type == expectedType;
    }

    public boolean isIdentifier(String name) {
        return this.type == TokenType.IDENTIFIER && this.text.equalsIgnoreCase(name);
    }

    public boolean isInstruction(String mnemonic) {
        return this.type == TokenType.INSTRUCTION && this.text.equalsIgnoreCase(mnemonic);
    }

    public boolean isDirective(String directive) {
        return this.type == TokenType.DIRECTIVE && this.text.equalsIgnoreCase(directive);
    }

    public String normalized() {
        return text.toUpperCase(Locale.ROOT);
    }
}
