package io.github.dinamo541.idearm.application.editor;

public enum HoverKind {
    INSTRUCTION,
    NUMBER_CONVERSION,
    SYMBOL_INFO,
    /** A problem the editor marked in the text, such as a mistyped mnemonic. */
    DIAGNOSTIC,
    DIRECTIVE,
    REGISTER,
    OPERATOR,
    PREDEFINED_SYMBOL,
    KEYWORD,
    INTERRUPT_SERVICE,
    CONCEPT
}
