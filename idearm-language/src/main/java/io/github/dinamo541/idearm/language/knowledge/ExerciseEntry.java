package io.github.dinamo541.idearm.language.knowledge;

public record ExerciseEntry(
        String id,
        String promptEn,
        String promptEs,
        String starterCode,
        String solutionCode,
        String solutionExplanationEn,
        String solutionExplanationEs
) {
}
