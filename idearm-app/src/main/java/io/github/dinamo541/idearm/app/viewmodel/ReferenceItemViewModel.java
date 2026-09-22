package io.github.dinamo541.idearm.app.viewmodel;

import java.util.Objects;

/**
 * ViewModel representing a single symbol reference occurrence across the project.
 */
public record ReferenceItemViewModel(
        String file,
        int line,
        int column,
        String preview
) {
    public ReferenceItemViewModel {
        Objects.requireNonNull(file, "file cannot be null");
        preview = preview != null ? preview.trim() : "";
    }
}
