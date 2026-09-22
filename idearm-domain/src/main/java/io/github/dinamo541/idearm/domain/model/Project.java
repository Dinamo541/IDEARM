package io.github.dinamo541.idearm.domain.model;

import java.util.Map;
import java.util.Objects;

/** Portable logical project settings; machine installations never belong here. */
public record Project(int schema, ProjectInfo info, TargetSelection target, ToolchainSelection toolchain,
                      Sources sources, Resources resources, Map<String, BuildConfiguration> build,
                      RunConfiguration run, DebugConfiguration debug, DistConfiguration dist) {
    public Project {
        Objects.requireNonNull(info); Objects.requireNonNull(target); Objects.requireNonNull(toolchain);
        Objects.requireNonNull(sources); Objects.requireNonNull(resources); build = Map.copyOf(build);
        Objects.requireNonNull(run); Objects.requireNonNull(debug); Objects.requireNonNull(dist);
    }
    public static Project hello(String name) {
        return new Project(1, new ProjectInfo(name, "0.1.0"),
                new TargetSelection("dos-exe-16", "8086"), new ToolchainSelection("borland-tasm", ">=3.2"),
                new Sources("src/main.asm", java.util.List.of(), java.util.List.of(), java.util.List.of()),
                new Resources(java.util.List.of()),
                Map.of("debug", BuildConfiguration.debug(), "release", BuildConfiguration.release()),
                RunConfiguration.defaults(), new DebugConfiguration("external"), new DistConfiguration(true, false));
    }
}
