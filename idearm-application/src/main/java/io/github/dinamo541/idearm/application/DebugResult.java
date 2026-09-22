package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of a debugging session.
 */
public record DebugResult(SessionState state, ExitInfo exitInfo, List<Diagnostic> diagnostics, String output) {

    public DebugResult {
        Objects.requireNonNull(state, "state cannot be null");
        diagnostics = List.copyOf(diagnostics);
        output = output == null ? "" : output;
    }

    public static DebugResult failed(List<Diagnostic> diagnostics, String output) {
        return new DebugResult(SessionState.FAILED, null, diagnostics, output);
    }

    public static DebugResult completed(ExitInfo exitInfo, String output) {
        return new DebugResult(SessionState.EXITED, exitInfo, List.of(), output);
    }

    public static DebugResult stopped(ExitInfo exitInfo, String output) {
        return new DebugResult(SessionState.STOPPED, exitInfo, List.of(), output);
    }
}
