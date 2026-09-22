package io.github.dinamo541.idearm.application.editor;

import io.github.dinamo541.idearm.language.catalog.CpuLevel;

import java.util.Objects;

/**
 * Autocompletion suggestion candidate.
 */
public record CompletionItem(
        String label,
        String insertText,
        String detail,
        String documentation,
        CompletionKind kind,
        CpuLevel minCpu
) {
    public CompletionItem {
        Objects.requireNonNull(label, "label cannot be null");
        Objects.requireNonNull(insertText, "insertText cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
    }

    public static CompletionItem of(String label, CompletionKind kind, String detail) {
        return new CompletionItem(label, label, detail, null, kind, CpuLevel.CPU_8086);
    }
}
