package io.github.dinamo541.idearm.domain.model;
import java.util.Objects;
public record ToolchainSelection(String id, String version) {
    public ToolchainSelection { Objects.requireNonNull(id); Objects.requireNonNull(version); }
}
