package io.github.dinamo541.idearm.domain.execution;

/**
 * Declares runtime capabilities exposed by an execution environment.
 */
public enum EnvCapability {
    WINDOW,
    CONSOLE_CAPTURE,
    EXTERNAL_DEBUGGER,
    GDB_RSP,
    SHELL
}
