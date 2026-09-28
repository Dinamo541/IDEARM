package io.github.dinamo541.idearm.language.knowledge;

import java.util.Collections;
import java.util.List;

/**
 * Architectural register entity (e.g. AX, EAX, RAX, FLAGS, CS, ST0).
 */
public record RegisterEntry(
        String id,
        String name,
        RegisterGroup group,
        int sizeBits,
        String parentId,
        int parentOffsetBits,
        List<RegisterView> views,
        List<FlagField> fields,
        String conventionalUse,
        List<ArchitecturalUse> architecturalUse,
        String writeSemantics,
        List<String> accessConstraints,
        Requirement requirement,
        List<String> exposedBy,
        List<String> sources
) {
    public RegisterEntry {
        views = views != null ? Collections.unmodifiableList(views) : List.of();
        fields = fields != null ? Collections.unmodifiableList(fields) : List.of();
        architecturalUse = architecturalUse != null ? Collections.unmodifiableList(architecturalUse) : List.of();
        accessConstraints = accessConstraints != null ? Collections.unmodifiableList(accessConstraints) : List.of();
        exposedBy = exposedBy != null ? Collections.unmodifiableList(exposedBy) : List.of();
        sources = sources != null ? Collections.unmodifiableList(sources) : List.of();
        requirement = requirement != null ? requirement : Requirement.forGeneration(CpuGeneration.I8086);
    }
}
