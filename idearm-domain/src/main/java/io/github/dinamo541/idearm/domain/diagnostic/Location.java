package io.github.dinamo541.idearm.domain.diagnostic;
import java.util.Objects;

/**
 * A source location; absent line/column are null, never invented as line zero.
 *
 * <p>{@code length} is how many characters the problem spans on that line, so an editor can underline exactly the
 * offending word. Assembler output never reports it, so it stays null for tool diagnostics; the linter fills it.
 */
public record Location(String path, Integer line, Integer column, Integer length) {
    public Location {
        Objects.requireNonNull(path);
        if (line != null && line < 1 || column != null && column < 1 || length != null && length < 1) {
            throw new IllegalArgumentException("Source line, column and length must be positive.");
        }
    }

    /** A location without a span, which is all an assembler ever gives. */
    public Location(String path, Integer line, Integer column) {
        this(path, line, column, null);
    }
}
