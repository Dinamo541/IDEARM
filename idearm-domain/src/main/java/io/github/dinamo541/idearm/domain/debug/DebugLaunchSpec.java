package io.github.dinamo541.idearm.domain.debug;

import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Specification for launching an interactive or emulated debugging session.
 *
 * @param listings each assembled source (project-relative, as in {@code idearm.toml}) with the listing the build
 *                 produced for it, entry source first; an emulator maps addresses to source lines with them
 */
public record DebugLaunchSpec(
        Path executable,
        Path projectRoot,
        List<Path> resources,
        List<String> args,
        ToolInstallation environmentInstallation,
        ToolInstallation debuggerInstallation,
        List<Breakpoint> breakpoints,
        Path stagingDirectory,
        String debuggerKind,
        Map<String, Path> listings) {

    public DebugLaunchSpec {
        Objects.requireNonNull(executable, "executable cannot be null");
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        resources = List.copyOf(resources);
        args = List.copyOf(args);
        Objects.requireNonNull(environmentInstallation, "environmentInstallation cannot be null");
        breakpoints = List.copyOf(breakpoints);
        Objects.requireNonNull(stagingDirectory, "stagingDirectory cannot be null");
        Objects.requireNonNull(debuggerKind, "debuggerKind cannot be null");
        listings = listings == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(listings));
    }

    public DebugLaunchSpec(Path executable, Path projectRoot, List<Path> resources, List<String> args,
                           ToolInstallation environmentInstallation, ToolInstallation debuggerInstallation,
                           List<Breakpoint> breakpoints, Path stagingDirectory, String debuggerKind) {
        this(executable, projectRoot, resources, args, environmentInstallation, debuggerInstallation, breakpoints,
                stagingDirectory, debuggerKind, Map.of());
    }
}
