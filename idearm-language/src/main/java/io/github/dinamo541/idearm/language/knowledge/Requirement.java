package io.github.dinamo541.idearm.language.knowledge;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Architectural and environmental requirements for an instruction form or service.
 */
public record Requirement(
        CpuGeneration minGeneration,
        Set<Feature> features,
        Set<ProcessorMode> validModes,
        Set<ProcessorMode> invalidModes,
        Privilege privilege,
        List<Integer> operandSizes,
        List<Integer> addressSizes
) {
    public Requirement {
        minGeneration = minGeneration != null ? minGeneration : CpuGeneration.I8086;
        features = features != null ? Collections.unmodifiableSet(features) : Set.of();
        validModes = validModes != null ? Collections.unmodifiableSet(validModes) : Set.of();
        invalidModes = invalidModes != null ? Collections.unmodifiableSet(invalidModes) : Set.of();
        privilege = privilege != null ? privilege : Privilege.ANY;
        operandSizes = operandSizes != null ? Collections.unmodifiableList(operandSizes) : List.of();
        addressSizes = addressSizes != null ? Collections.unmodifiableList(addressSizes) : List.of();
    }

    public static Requirement forGeneration(CpuGeneration gen) {
        return new Requirement(gen, Set.of(), Set.of(), Set.of(), Privilege.ANY, List.of(), List.of());
    }
}
