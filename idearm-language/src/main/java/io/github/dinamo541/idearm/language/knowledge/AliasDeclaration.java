package io.github.dinamo541.idearm.language.knowledge;

public record AliasDeclaration(
        String name,
        Dialect dialect,
        String version,
        String targetMnemonic,
        String note
) {
    public AliasDeclaration(String name, Dialect dialect, String targetMnemonic) {
        this(name, dialect, null, targetMnemonic, null);
    }
}
