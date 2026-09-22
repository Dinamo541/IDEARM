package io.github.dinamo541.idearm.domain.model;
import java.util.List;
public record Resources(List<String> files) {
    public Resources { files = List.copyOf(files); }
}
