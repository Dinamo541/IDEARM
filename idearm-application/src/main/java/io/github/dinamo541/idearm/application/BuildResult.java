package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import java.nio.file.Path;
import java.util.List;

/** The immutable outcome shared by both front ends. */
public record BuildResult(BuildStatus status, String configuration, List<Diagnostic> diagnostics,
                          List<Path> artifacts, String output) {
    public BuildResult {
        diagnostics = List.copyOf(diagnostics);
        artifacts = List.copyOf(artifacts);
    }
    public boolean succeeded() { return status == BuildStatus.SUCCEEDED; }
}
