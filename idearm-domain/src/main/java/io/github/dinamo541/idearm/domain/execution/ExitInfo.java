package io.github.dinamo541.idearm.domain.execution;

import java.time.Duration;
import java.util.Objects;

/**
 * Outcome information when a program execution finishes or terminates.
 */
public record ExitInfo(int exitCode, Duration duration, boolean wasStopped, String message) {
    public ExitInfo {
        Objects.requireNonNull(duration);
        if (message == null) message = "";
    }

    public static ExitInfo success(Duration duration) {
        return new ExitInfo(0, duration, false, "Program terminated successfully.");
    }

    public static ExitInfo stopped(Duration duration) {
        return new ExitInfo(-1, duration, true, "Execution stopped by user.");
    }

    public static ExitInfo error(int exitCode, Duration duration, String message) {
        return new ExitInfo(exitCode, duration, false, message);
    }
}
