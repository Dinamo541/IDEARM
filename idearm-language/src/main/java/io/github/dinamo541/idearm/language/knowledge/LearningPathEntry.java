package io.github.dinamo541.idearm.language.knowledge;

import java.util.Collections;
import java.util.List;

/**
 * Guided educational learning path (R-1 to R-9).
 */
public record LearningPathEntry(
        String id,
        int sequenceNumber,
        String titleEn,
        String titleEs,
        PedagogicalLevel level,
        List<String> prerequisites,
        String objectiveEn,
        String objectiveEs,
        List<String> stepConceptIds,
        List<String> stepInstructionIds,
        List<ExerciseEntry> exercises,
        List<String> sources
) {
    public LearningPathEntry {
        prerequisites = prerequisites != null ? Collections.unmodifiableList(prerequisites) : List.of();
        stepConceptIds = stepConceptIds != null ? Collections.unmodifiableList(stepConceptIds) : List.of();
        stepInstructionIds = stepInstructionIds != null ? Collections.unmodifiableList(stepInstructionIds) : List.of();
        exercises = exercises != null ? Collections.unmodifiableList(exercises) : List.of();
        sources = sources != null ? Collections.unmodifiableList(sources) : List.of();
    }
}
