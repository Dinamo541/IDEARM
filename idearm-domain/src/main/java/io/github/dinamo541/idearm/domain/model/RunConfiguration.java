package io.github.dinamo541.idearm.domain.model;
import java.util.List;
import java.util.Objects;
public record RunConfiguration(String environment, String isolation, boolean keepOpen,
                               String cycles, int memsize, List<String> args) {
    public RunConfiguration {
        Objects.requireNonNull(environment); Objects.requireNonNull(isolation);
        Objects.requireNonNull(cycles); args = List.copyOf(args);
    }
    public static RunConfiguration defaults() {
        return new RunConfiguration("dosbox", "required", true, "auto", 16, List.of());
    }
}
