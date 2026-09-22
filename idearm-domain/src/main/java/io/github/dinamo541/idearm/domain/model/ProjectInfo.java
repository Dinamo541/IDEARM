package io.github.dinamo541.idearm.domain.model;
import java.util.Objects;
public record ProjectInfo(String name, String version) {
    public ProjectInfo { Objects.requireNonNull(name); Objects.requireNonNull(version); }
}
