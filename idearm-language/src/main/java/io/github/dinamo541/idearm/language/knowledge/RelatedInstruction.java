package io.github.dinamo541.idearm.language.knowledge;

public record RelatedInstruction(
        String id,
        RelationType relation,
        String note
) {
    public enum RelationType {
        ALTERNATIVE,
        PAIR,
        PREREQUISITE,
        CONTRAST,
        SEE_ALSO
    }

    public RelatedInstruction(String id, RelationType relation) {
        this(id, relation, null);
    }
}
