package io.github.dinamo541.idearm.language.knowledge;

import java.util.Collections;
import java.util.List;

public record ExceptionSpec(
        String vector,
        String condition,
        List<ProcessorMode> modes
) {
    public ExceptionSpec {
        modes = modes != null ? Collections.unmodifiableList(modes) : List.of();
    }
}
