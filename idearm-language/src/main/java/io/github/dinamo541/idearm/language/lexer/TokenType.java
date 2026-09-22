package io.github.dinamo541.idearm.language.lexer;

/**
 * Categorization of Assembly source tokens.
 */
public enum TokenType {
    INSTRUCTION,
    REGISTER,
    DIRECTIVE,
    IDENTIFIER,
    NUMBER,
    STRING,
    COMMENT,
    COMMA,
    COLON,
    LBRACKET,
    RBRACKET,
    LPAREN,
    RPAREN,
    PLUS,
    MINUS,
    STAR,
    SLASH,
    DOT,
    EQUALS,
    QUESTION,
    EOL,
    EOF
}
