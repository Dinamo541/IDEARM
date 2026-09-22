package io.github.dinamo541.idearm.toolchain.dos;

import io.github.dinamo541.idearm.domain.DomainException;
import java.util.Locale;

/** Utility for converting logical paths to DOS drive 8.3 paths. */
public final class DosArguments {
    private DosArguments() {}

    public static String path(char drive, String logical) {
        if (logical == null || logical.isBlank()) {
            throw new DomainException("build.invalid-path", "A DOS input or output path is empty.");
        }
        String normalized = logical.replace('\\', '/');
        for (String part : normalized.split("/", -1)) {
            if (!part.matches("[A-Za-z0-9_][A-Za-z0-9_-]{0,7}(\\.[A-Za-z0-9_]{1,3})?")) {
                throw new DomainException("build.invalid-dos-path", "DOS build paths must use safe 8.3 names: " + logical, logical);
            }
        }
        return drive + ":\\" + normalized.replace('/', '\\').toUpperCase(Locale.ROOT);
    }
}
