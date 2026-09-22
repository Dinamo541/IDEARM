package io.github.dinamo541.idearm.domain.model;
import java.util.List;
import java.util.Map;
public record BuildConfiguration(boolean debugInfo, boolean listing, boolean map,
                                 Map<String, String> defines, List<String> extraArgs) {
    public BuildConfiguration { defines = Map.copyOf(defines); extraArgs = List.copyOf(extraArgs); }
    public static BuildConfiguration debug() { return new BuildConfiguration(true, true, true, Map.of(), List.of()); }
    public static BuildConfiguration release() { return new BuildConfiguration(false, false, false, Map.of(), List.of()); }
}
