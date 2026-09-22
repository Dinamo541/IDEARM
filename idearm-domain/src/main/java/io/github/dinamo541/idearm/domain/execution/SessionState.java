package io.github.dinamo541.idearm.domain.execution;

/**
 * Lifecycle states of an execution session.
 */
public enum SessionState {
    STARTING,
    RUNNING,
    EXITED,
    STOPPED,
    FAILED
}
