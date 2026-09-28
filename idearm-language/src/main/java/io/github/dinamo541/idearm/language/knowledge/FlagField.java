package io.github.dinamo541.idearm.language.knowledge;

public record FlagField(
        String id,
        String name,
        int bitPosition,
        String meaning,
        String setCondition,
        String clearCondition
) {
}
