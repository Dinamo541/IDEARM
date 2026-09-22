package io.github.dinamo541.idearm.application.editor;

import java.util.Objects;

/**
 * Educational hover content rendered when hovering over a symbol, mnemonic or number.
 *
 * <p>The texts are English. For a number and a symbol the facts behind them are also given ({@link #number()},
 * {@link #symbol()}), so the user interface can write them in its own language (ADR-006).
 */
public record HoverInfo(
        String title,
        String syntax,
        String description,
        String flagsTable,
        String example,
        HoverKind kind,
        Long number,
        SymbolFacts symbol
) {
    /** Where a symbol is defined and, while a debugger is paused, its current value. */
    public record SymbolFacts(String name, String kind, String file, int line, Long liveValue) {
        public SymbolFacts {
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(kind, "kind cannot be null");
            Objects.requireNonNull(file, "file cannot be null");
        }
    }

    public HoverInfo {
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
    }

    public HoverInfo(String title, String syntax, String description, String flagsTable, String example,
                     HoverKind kind) {
        this(title, syntax, description, flagsTable, example, kind, null, null);
    }
}
