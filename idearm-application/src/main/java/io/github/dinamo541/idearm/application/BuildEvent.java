package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import java.nio.file.Path;
import java.util.List;

/** Delivery occurs on the task thread; a desktop subscriber must marshal to the JavaFX thread. */
public sealed interface BuildEvent {
    record Started(Path project, String configuration) implements BuildEvent {}
    record Output(String text) implements BuildEvent {}
    record DiagnosticsPublished(List<Diagnostic> diagnostics) implements BuildEvent {
        public DiagnosticsPublished { diagnostics = List.copyOf(diagnostics); }
    }
    record Finished(BuildResult result) implements BuildEvent {}
}
