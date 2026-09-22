package io.github.dinamo541.idearm.domain.execution;

import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Specifications for launching an isolated execution session.
 */
public record LaunchSpec(
        Path executable,
        Path projectRoot,
        List<Path> resources,
        List<String> args,
        boolean keepOpen,
        String cycles,
        int memsize,
        Path stagingDirectory,
        ToolInstallation environmentInstallation
) {
    public LaunchSpec {
        Objects.requireNonNull(executable, "executable cannot be null");
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        resources = resources == null ? List.of() : List.copyOf(resources);
        args = args == null ? List.of() : List.copyOf(args);
        cycles = cycles == null ? "auto" : cycles;
        if (memsize <= 0) memsize = 16;
        Objects.requireNonNull(stagingDirectory, "stagingDirectory cannot be null");
        Objects.requireNonNull(environmentInstallation, "environmentInstallation cannot be null");
    }
}
