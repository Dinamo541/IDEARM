package io.github.dinamo541.idearm.language.catalog;

/**
 * Status of an individual processor flag after instruction execution.
 */
public enum FlagEffect {
    MODIFIED("M"),
    CLEARED("0"),
    SET("1"),
    UNDEFINED("U"),
    UNAFFECTED("-");

    private final String symbol;

    FlagEffect(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
