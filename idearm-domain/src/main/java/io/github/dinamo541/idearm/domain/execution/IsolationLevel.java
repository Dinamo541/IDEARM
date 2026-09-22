package io.github.dinamo541.idearm.domain.execution;

/**
 * Declares the isolation level of an execution environment.
 */
public enum IsolationLevel {
    EMULATED,
    VIRTUALIZED,
    CONTAINER,
    NATIVE
}
