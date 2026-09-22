package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * ViewModel wrapping a single {@link Diagnostic} for display in the Problems table.
 */
public final class DiagnosticItemViewModel {

    private final ObjectProperty<Severity> severity = new SimpleObjectProperty<>();
    private final StringProperty file = new SimpleStringProperty();
    private final IntegerProperty line = new SimpleIntegerProperty();
    private final StringProperty code = new SimpleStringProperty();
    private final StringProperty message = new SimpleStringProperty();
    private final StringProperty tool = new SimpleStringProperty();
    private final Diagnostic diagnostic;

    public DiagnosticItemViewModel(Diagnostic diagnostic) {
        this(diagnostic, null);
    }

    /** Files inside the project are shown relative to it, whichever tool reported them. */
    public DiagnosticItemViewModel(Diagnostic diagnostic, Path projectRoot) {
        this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic cannot be null");
        this.severity.set(diagnostic.severity());
        this.file.set(diagnostic.location() != null ? display(diagnostic.location().path(), projectRoot) : "");
        this.line.set(diagnostic.location() != null && diagnostic.location().line() != null
                ? diagnostic.location().line() : 0);
        this.code.set(diagnostic.code());
        this.message.set(diagnostic.message());
        this.tool.set(diagnostic.tool());
    }

    public static DiagnosticItemViewModel from(Diagnostic d) {
        return new DiagnosticItemViewModel(d);
    }

    private static String display(String path, Path projectRoot) {
        if (path == null || path.isBlank() || projectRoot == null) {
            return path == null ? "" : path;
        }
        try {
            Path file = Path.of(path);
            Path root = projectRoot.toAbsolutePath().normalize();
            if (file.isAbsolute() && file.normalize().startsWith(root)) {
                return root.relativize(file.normalize()).toString().replace('\\', '/');
            }
        } catch (RuntimeException notAPath) {
            // Tool text that is not a path is shown as written.
        }
        return path;
    }

    public Severity getSeverity() {
        return severity.get();
    }

    public ObjectProperty<Severity> severityProperty() {
        return severity;
    }

    public String getFile() {
        return file.get();
    }

    public StringProperty fileProperty() {
        return file;
    }

    public int getLine() {
        return line.get();
    }

    public IntegerProperty lineProperty() {
        return line;
    }

    public String getCode() {
        return code.get();
    }

    public StringProperty codeProperty() {
        return code;
    }

    public String getMessage() {
        return message.get();
    }

    public StringProperty messageProperty() {
        return message;
    }

    public String getTool() {
        return tool.get();
    }

    public StringProperty toolProperty() {
        return tool;
    }

    public Diagnostic getDiagnostic() {
        return diagnostic;
    }
}
