package io.github.dinamo541.idearm.domain.model;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Local machine state, never serialized into a portable project file. */
public record ToolInstallation(String toolId, String version, Path executable, HostKind hostKind,
                               Map<String, Path> companions, String sha256, String source) {
    public ToolInstallation {
        Objects.requireNonNull(toolId); Objects.requireNonNull(version);
        Objects.requireNonNull(executable); Objects.requireNonNull(hostKind);
        companions = Map.copyOf(companions);
    }
    public ToolInstallation(String toolId, String version, Path executable, HostKind hostKind,
                            Map<String, Path> companions) {
        this(toolId, version, executable, hostKind, companions, null, "registered");
    }
}
