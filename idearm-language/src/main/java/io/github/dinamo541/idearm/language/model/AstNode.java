package io.github.dinamo541.idearm.language.model;

/**
 * Base interface for all Assembly Abstract Syntax Tree (AST) nodes.
 */
public interface AstNode {
    int line();
    int column();
}
