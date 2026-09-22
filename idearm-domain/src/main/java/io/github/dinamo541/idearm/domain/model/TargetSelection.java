package io.github.dinamo541.idearm.domain.model;
import java.util.Objects;
public record TargetSelection(String profile, String cpu) {
    public TargetSelection { Objects.requireNonNull(profile); Objects.requireNonNull(cpu); }
}
