package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies AA-P6-01: The nine guided learning paths (R-1 to R-9) with prerequisites,
 * objectives, steps, and verified exercises with solutions.
 */
@DisplayName("AA-P6-01: Nine Guided Learning Paths (R-1 to R-9) Integrity")
class LearningPathsIntegrityTest {

    private static Corpus corpus;

    @BeforeAll
    static void setUp() {
        corpus = Corpus.get();
        assertNotNull(corpus, "Corpus must not be null");
    }

    @Test
    @DisplayName("Verify that exactly 9 guided learning paths R-1 to R-9 exist in the corpus")
    void verifyNineLearningPathsExist() {
        List<LearningPathEntry> paths = corpus.getAllLearningPaths();
        assertEquals(9, paths.size(), "Corpus must contain exactly 9 learning paths (R-1 to R-9)");

        for (int i = 1; i <= 9; i++) {
            int seq = i;
            LearningPathEntry path = paths.stream()
                    .filter(p -> p.sequenceNumber() == seq)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing path with sequence number " + seq));

            assertTrue(path.id().startsWith("path.r" + seq), "Path ID must follow path.r" + seq + " convention");
            assertNotNull(path.titleEs(), "Spanish title must not be null");
            assertNotNull(path.titleEn(), "English title must not be null");
            assertFalse(path.titleEs().isBlank(), "Spanish title must not be blank");
            assertFalse(path.titleEn().isBlank(), "English title must not be blank");

            assertNotNull(path.objectiveEs(), "Spanish objective must not be null");
            assertNotNull(path.objectiveEn(), "English objective must not be null");
            assertFalse(path.objectiveEs().isBlank(), "Spanish objective must not be blank");
            assertFalse(path.objectiveEn().isBlank(), "English objective must not be blank");

            assertFalse(path.stepConceptIds().isEmpty(), "Path R-" + seq + " must link to concepts");
            assertFalse(path.stepInstructionIds().isEmpty(), "Path R-" + seq + " must link to instructions");
            assertFalse(path.sources().isEmpty(), "Path R-" + seq + " must have authoritative sources");
        }
    }

    @Test
    @DisplayName("Verify prerequisite graph validity and progression across R-1 to R-9")
    void verifyPrerequisitesGraph() {
        List<LearningPathEntry> paths = corpus.getAllLearningPaths();
        Set<String> knownPathIds = new HashSet<>();
        for (LearningPathEntry p : paths) {
            knownPathIds.add(p.id());
        }

        LearningPathEntry r1 = paths.stream().filter(p -> p.sequenceNumber() == 1).findFirst().orElseThrow();
        assertTrue(r1.prerequisites().isEmpty(), "R-1 must have no prerequisites (entry point)");

        LearningPathEntry r2 = paths.stream().filter(p -> p.sequenceNumber() == 2).findFirst().orElseThrow();
        assertTrue(r2.prerequisites().contains(r1.id()), "R-2 must require R-1");

        LearningPathEntry r7 = paths.stream().filter(p -> p.sequenceNumber() == 7).findFirst().orElseThrow();
        assertTrue(r7.prerequisites().contains("path.r5.stack_and_procedures"), "R-7 must require R-5");
        assertTrue(r7.prerequisites().contains("path.r6.environment_services"), "R-7 must require R-6");

        // Verify all prerequisites reference existing paths and are strictly acyclic
        for (LearningPathEntry p : paths) {
            for (String prereq : p.prerequisites()) {
                assertTrue(knownPathIds.contains(prereq), "Prerequisite " + prereq + " must exist");
                LearningPathEntry prereqPath = paths.stream().filter(x -> x.id().equals(prereq)).findFirst().orElseThrow();
                assertTrue(prereqPath.sequenceNumber() < p.sequenceNumber(),
                        "Prerequisite " + prereq + " must have lower sequence number than " + p.id());
            }
        }
    }

    @Test
    @DisplayName("Verify that all learning paths contain original exercises with starter code and solutions")
    void verifyExercisesWithSolutions() {
        List<LearningPathEntry> paths = corpus.getAllLearningPaths();

        for (LearningPathEntry path : paths) {
            assertFalse(path.exercises().isEmpty(), "Path " + path.id() + " must have at least one exercise");

            for (ExerciseEntry ex : path.exercises()) {
                assertNotNull(ex.id());
                assertNotNull(ex.promptEs());
                assertNotNull(ex.promptEn());
                assertFalse(ex.promptEs().isBlank(), "Exercise prompt ES must not be blank");
                assertFalse(ex.promptEn().isBlank(), "Exercise prompt EN must not be blank");

                assertNotNull(ex.starterCode());
                assertNotNull(ex.solutionCode());
                assertFalse(ex.solutionCode().isBlank(), "Solution code must not be blank");

                assertNotNull(ex.solutionExplanationEs());
                assertNotNull(ex.solutionExplanationEn());
                assertFalse(ex.solutionExplanationEs().isBlank(), "Solution explanation ES must not be blank");
                assertFalse(ex.solutionExplanationEn().isBlank(), "Solution explanation EN must not be blank");
            }
        }
    }
}
