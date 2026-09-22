package io.github.dinamo541.idearm.domain.dist;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Outcome of packaging a project for standalone distribution.
 */
public record DistResult(
        Path distDirectory,
        Path mainExecutable,
        Path launcherScript,
        List<Path> packagedFiles,
        List<String> warnings
) {
    public DistResult {
        Objects.requireNonNull(distDirectory, "distDirectory cannot be null");
        Objects.requireNonNull(mainExecutable, "mainExecutable cannot be null");
        packagedFiles = packagedFiles == null ? List.of() : List.copyOf(packagedFiles);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
