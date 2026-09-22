package io.github.dinamo541.idearm.domain.model;
import java.util.Map;
import java.util.Objects;
public record ResolvedToolchain(String providerId, Map<String, ToolInstallation> tools, ToolInstallation environment) {
    public ResolvedToolchain {
        Objects.requireNonNull(providerId);
        tools = Map.copyOf(tools);
    }

    public ResolvedToolchain(String providerId, Map<String, ToolInstallation> tools) {
        this(providerId, tools, null);
    }
}
