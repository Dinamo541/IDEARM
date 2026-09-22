package io.github.dinamo541.idearm.domain.debug;

import java.util.Objects;

/**
 * An editor/debugger breakpoint attached to a source file line.
 *
 * @param path project-relative slash-separated file path (e.g. "src/main.asm")
 * @param line 1-based source line number
 * @param enabled whether this breakpoint is currently active
 */
public record Breakpoint(String path, int line, boolean enabled) {
    public Breakpoint {
        Objects.requireNonNull(path, "path cannot be null");
        path = path.replace('\\', '/').strip();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (line < 1) {
            throw new IllegalArgumentException("Breakpoint line must be >= 1, was: " + line);
        }
    }

    public Breakpoint(String path, int line) {
        this(path, line, true);
    }

    public Breakpoint withEnabled(boolean enabled) {
        return new Breakpoint(path, line, enabled);
    }
}
