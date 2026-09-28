package io.github.dinamo541.idearm.language.model;

/**
 * Common interface for all structured operands parsed from assembly statements.
 */
public sealed interface ParsedOperand permits
        RegisterOperand,
        ImmediateOperand,
        MemoryOperand,
        SymbolOperand,
        UnknownOperand {

    String rawText();
    int line();
    int startColumn();
    int endColumn();
    OperandKind kind();

    enum OperandKind {
        REGISTER,
        IMMEDIATE,
        MEMORY,
        SYMBOL,
        UNKNOWN
    }
}
