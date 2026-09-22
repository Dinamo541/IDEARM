package io.github.dinamo541.idearm.domain.model;
import java.util.Objects;
public record DebugConfiguration(String backend) {
    public DebugConfiguration { Objects.requireNonNull(backend); }
}
