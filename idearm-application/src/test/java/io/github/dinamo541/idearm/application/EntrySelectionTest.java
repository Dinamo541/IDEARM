package io.github.dinamo541.idearm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.model.Sources;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntrySelectionTest {

    // ---------------------------------------------------------------- one program in several files

    @Test
    void aPatternAlreadyCoversBothFilesSoItIsLeftAlone() {
        Sources sources = EntrySelection.choose(
                new Sources("src/main.asm", List.of("src/*.asm"), List.of(), List.of()), "src/video.asm", false);

        assertEquals("src/video.asm", sources.entry());
        assertEquals(List.of("src/*.asm"), sources.modules());
    }

    /** The planner sees the same file twice and refuses the project, so a literal copy of the entry must go. */
    @Test
    void aLiteralCopyOfTheNewMainFileIsTakenOutOfTheModules() {
        Sources sources = EntrySelection.choose(
                new Sources("src/main.asm", List.of("src/video.asm", "src/*.inc.asm"), List.of(), List.of()),
                "src/video.asm", false);

        assertFalse(sources.modules().contains("src/video.asm"));
        assertEquals(List.of("src/*.inc.asm"), sources.modules());
    }

    /** An imported project names every file it builds, so the two simply swap places. */
    @Test
    void aLiteralListSwapsTheOldMainFileForTheNewOne() {
        Sources sources = EntrySelection.choose(
                new Sources("src/main.asm", List.of("src/teclado.asm", "src/video.asm"), List.of(), List.of()),
                "src/video.asm", false);

        assertEquals("src/video.asm", sources.entry());
        assertEquals(List.of("src/main.asm", "src/teclado.asm"), sources.modules());
    }

    @Test
    void theOldMainFileIsNotAddedTwice() {
        Sources sources = EntrySelection.choose(
                new Sources("src/main.asm", List.of("src/main.asm", "src/video.asm"), List.of(), List.of()),
                "src/video.asm", false);

        assertEquals(List.of("src/main.asm"), sources.modules());
    }

    /** Windows and DOS match names without regard to case, so MAIN.ASM and main.asm are the same file. */
    @Test
    void aModuleThatOnlyDiffersInCaseIsStillTheSameFile() {
        Sources sources = EntrySelection.choose(
                new Sources("src/main.asm", List.of("SRC/VIDEO.ASM"), List.of(), List.of()),
                "src/video.asm", false);

        assertEquals(List.of("src/main.asm"), sources.modules());
    }

    // ---------------------------------------------------------------- separate programs

    @Test
    void buildingOnlyTheMainFileEmptiesTheModules() {
        Sources sources = EntrySelection.choose(
                new Sources("src/main.asm", List.of("src/*.asm"), List.of(), List.of()),
                "src/ejercicio2.asm", true);

        assertEquals("src/ejercicio2.asm", sources.entry());
        assertEquals(List.of(), sources.modules());
        assertTrue(EntrySelection.buildsOnlyTheEntry(sources));
    }

    @Test
    void leavingThatBehindBringsBackTheFolderOfTheMainFile() {
        Sources sources = EntrySelection.choose(
                new Sources("src/ejercicio2.asm", List.of(), List.of(), List.of()), "src/ejercicio2.asm", false);

        assertEquals(List.of("src/*.asm"), sources.modules());
        assertFalse(EntrySelection.buildsOnlyTheEntry(sources));
    }

    @Test
    void aMainFileAtTheProjectRootGetsThePatternOfTheRoot() {
        Sources sources = EntrySelection.choose(
                new Sources("main.asm", List.of(), List.of(), List.of()), "main.asm", false);

        assertEquals(List.of("*.asm"), sources.modules());
    }

    // ---------------------------------------------------------------- everything else is kept

    @Test
    void theIncludeFoldersAndExclusionsAreUntouched() {
        Sources sources = EntrySelection.choose(
                new Sources("src/main.asm", List.of("src/*.asm"), List.of("sprite"), List.of("src/old.asm")),
                "src/video.asm", false);

        assertEquals(List.of("sprite"), sources.include());
        assertEquals(List.of("src/old.asm"), sources.exclude());
    }

    @Test
    void aBackslashPathIsStoredTheWayTheProjectFileSpellsPaths() {
        Sources sources = EntrySelection.choose(
                new Sources("src/main.asm", List.of("src/*.asm"), List.of(), List.of()),
                "src\\video.asm", false);

        assertEquals("src/video.asm", sources.entry());
    }
}
