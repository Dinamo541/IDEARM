package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;

import java.util.List;
import java.util.Objects;

/**
 * Result of running a project in an isolated execution environment.
 */
public record RunResult(
        SessionState state,
        ExitInfo exitInfo,
        List<Diagnostic> diagnostics,
        String output
) {
    public RunResult {
        Objects.requireNonNull(state, "state cannot be null");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        output = output == null ? "" : output;
    }

    public static RunResult failed(List<Diagnostic> diagnostics, String output) {
        return new RunResult(SessionState.FAILED, null, diagnostics, output);
    }

    public static RunResult stopped(ExitInfo exitInfo, String output) {
        return new RunResult(SessionState.STOPPED, exitInfo, List.of(), output);
    }

    public static RunResult success(ExitInfo exitInfo, String output) {
        return new RunResult(SessionState.EXITED, exitInfo, List.of(), output);
    }
}
