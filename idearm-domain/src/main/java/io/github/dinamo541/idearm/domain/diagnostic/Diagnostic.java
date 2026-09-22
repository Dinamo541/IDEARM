package io.github.dinamo541.idearm.domain.diagnostic;

import java.util.List;
import java.util.Objects;

/**
 * One problem to show the user. {@code message} is the text as the tool or the IDE wrote it; when the IDE wrote
 * it, {@code code} and {@code arguments} let the user interface show it in the selected language instead.
 */
public record Diagnostic(Severity severity, String code, String message, Location location, String tool,
                         String rawLine, List<String> arguments) {

    public Diagnostic {
        Objects.requireNonNull(severity); Objects.requireNonNull(code); Objects.requireNonNull(message);
        Objects.requireNonNull(tool); Objects.requireNonNull(rawLine);
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    public Diagnostic(Severity severity, String code, String message, Location location, String tool, String rawLine) {
        this(severity, code, message, location, tool, rawLine, List.of());
    }
}
