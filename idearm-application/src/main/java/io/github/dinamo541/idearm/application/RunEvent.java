package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import java.nio.file.Path;

/**
 * Events emitted during project execution in an isolated environment.
 */
public sealed interface RunEvent {
    record Started(Path project, String environment) implements RunEvent {}
    /** A line of the IDE's own log, such as the build that ran first. */
    record Output(String text) implements RunEvent {}
    /**
     * A program whose console is the IDE (a native program outside Windows) started: text typed for it goes to
     * {@code session.sendInput}.
     */
    record SessionAttached(io.github.dinamo541.idearm.domain.execution.ExecutionSession session) implements RunEvent {}
    /** What such a program printed, exactly as it printed it. */
    record ProgramOutput(String text) implements RunEvent {}
    record Exited(ExitInfo exitInfo) implements RunEvent {}
}
