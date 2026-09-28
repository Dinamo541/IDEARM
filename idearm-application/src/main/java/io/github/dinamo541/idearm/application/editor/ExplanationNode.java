package io.github.dinamo541.idearm.application.editor;

import java.util.List;
import java.util.Objects;

/**
 * Node in the pedagogical explanation tree produced by QueryExplain (AA-P4-02).
 */
public record ExplanationNode(
        String title,
        String role,
        String summary,
        String details,
        int startColumn,
        int endColumn,
        boolean notationNotCode,
        boolean assemblableSyntax,
        List<ExplanationNode> children
) {
    public ExplanationNode {
        Objects.requireNonNull(title, "title cannot be null");
        children = children != null ? List.copyOf(children) : List.of();
    }

    public ExplanationNode(String title, String role, String summary, String details, int startCol, int endCol) {
        this(title, role, summary, details, startCol, endCol, false, false, List.of());
    }

    public ExplanationNode(String title, String role, String summary, String details, int startCol, int endCol,
                           boolean notationNotCode, boolean assemblableSyntax) {
        this(title, role, summary, details, startCol, endCol, notationNotCode, assemblableSyntax, List.of());
    }

    public ExplanationNode withChildren(List<ExplanationNode> newChildren) {
        return new ExplanationNode(title, role, summary, details, startColumn, endColumn,
                notationNotCode, assemblableSyntax, newChildren);
    }

    public HoverInfo toHoverInfo() {
        HoverInfo sec = (children != null && !children.isEmpty()) ? children.getFirst().toHoverInfo() : null;
        String desc = details != null && !details.isBlank() ? summary + "\n\n" + details : summary;
        return new HoverInfo(
                title,
                role != null ? "[" + role + "]" : null,
                desc,
                null,
                null,
                HoverKind.INSTRUCTION,
                null,
                null,
                sec
        );
    }
}
