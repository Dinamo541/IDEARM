package io.github.dinamo541.idearm.domain.model;
import java.util.List;
import java.util.Objects;
public record Sources(String entry, List<String> modules, List<String> include, List<String> exclude) {
    public Sources {
        Objects.requireNonNull(entry); modules = List.copyOf(modules);
        include = List.copyOf(include); exclude = List.copyOf(exclude);
    }
}
