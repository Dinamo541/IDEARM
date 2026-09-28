package io.github.dinamo541.idearm.language.knowledge;

public record DialectSupport(
        Dialect dialect,
        String version,
        boolean assemblable,
        String note
) {
    public DialectSupport(Dialect dialect, boolean assemblable) {
        this(dialect, null, assemblable, null);
    }
}
