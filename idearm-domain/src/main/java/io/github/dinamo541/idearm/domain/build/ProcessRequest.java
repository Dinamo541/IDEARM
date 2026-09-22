package io.github.dinamo541.idearm.domain.build;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One external process to run.
 *
 * <p>{@code inheritEnvironment} is false by default so a build never depends on machine-wide variables: ML 6.11
 * and LINK read INCLUDE and LIB from the environment, which would make results differ between machines. The
 * executor still provides the minimum a process needs to start.
 */
public record ProcessRequest(List<String> command, Path workingDirectory, Map<String, String> environment,
                             Duration timeout, boolean inheritEnvironment, boolean interactive) {
    public ProcessRequest {
        command = List.copyOf(command); environment = Map.copyOf(environment); Objects.requireNonNull(timeout);
        if (command.isEmpty()) throw new IllegalArgumentException("A process command is required.");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Timeout must be positive.");
    }

    public ProcessRequest(List<String> command, Path workingDirectory, Map<String, String> environment,
                          Duration timeout, boolean inheritEnvironment) {
        this(command, workingDirectory, environment, timeout, inheritEnvironment, false);
    }

    public ProcessRequest(List<String> command, Path workingDirectory, Map<String, String> environment,
                          Duration timeout) {
        this(command, workingDirectory, environment, timeout, false, false);
    }

    /** A process with no inherited tool configuration, which is what every build step wants. */
    public static ProcessRequest isolated(List<String> command, Path workingDirectory, Duration timeout) {
        return new ProcessRequest(command, workingDirectory, Map.of(), timeout, false, false);
    }

    /** The same request with extra environment variables for the process (they win over inherited ones). */
    public ProcessRequest withEnvironment(Map<String, String> extra) {
        var merged = new java.util.HashMap<>(environment);
        merged.putAll(extra);
        return new ProcessRequest(command, workingDirectory, merged, timeout, inheritEnvironment, interactive);
    }

    /**
     * The same request with its standard input and output kept as pipes the caller reads and writes, for a program
     * whose console is shown inside the IDE. Launchers otherwise discard them.
     */
    public ProcessRequest asInteractive() {
        return new ProcessRequest(command, workingDirectory, environment, timeout, inheritEnvironment, true);
    }
}
