package io.github.dinamo541.idearm.language.knowledge;

public record SyntaxForm(
        Dialect dialect,
        String version,
        String text
) {
    public static SyntaxForm of(Dialect dialect, String text) {
        return new SyntaxForm(dialect, null, text);
    }
}
