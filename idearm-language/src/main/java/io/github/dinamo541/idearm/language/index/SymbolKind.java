package io.github.dinamo541.idearm.language.index;

/**
 * Kind of symbol recognized in x86 Assembly codebases.
 */
public enum SymbolKind {
    PROCEDURE,
    LABEL,
    VARIABLE,
    CONSTANT,
    SEGMENT,
    MACRO
}
