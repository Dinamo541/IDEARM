package io.github.dinamo541.idearm.infrastructure.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.port.ProjectFiles.Deletion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class LocalProjectFilesTest {

    @TempDir
    Path project;

    private final LocalProjectFiles files = new LocalProjectFiles();

    @Test
    void createsNestedFilesAndListsChildren() throws IOException {
        Path created = files.createFile(project.resolve("src/lib/io.asm"));

        assertTrue(Files.isRegularFile(created));
        assertEquals(Set.of("lib"), names(project.resolve("src")));
        assertTrue(files.list(project.resolve("src")).getFirst().directory());
    }

    @Test
    void neverOverwritesAnExistingEntry() throws IOException {
        Path existing = Files.writeString(project.resolve("main.asm"), "keep me");

        var failure = assertThrows(DomainException.class, () -> files.createFile(existing));
        assertEquals("explorer.name.exists", failure.code());
        assertThrows(DomainException.class, () -> files.createDirectory(existing));
        assertEquals("keep me", Files.readString(existing));
    }

    @Test
    void refusesToCreateThroughAFile() throws IOException {
        Files.writeString(project.resolve("notes"), "a file");

        assertThrows(DomainException.class, () -> files.createFile(project.resolve("notes/inner.asm")));
    }

    @Test
    void renamesAndRefusesToReplaceAnotherEntry() throws IOException {
        Path source = Files.writeString(project.resolve("old.asm"), "code");
        Files.writeString(project.resolve("taken.asm"), "other");

        Path renamed = files.rename(source, project.resolve("new.asm"));

        assertEquals(Set.of("new.asm", "taken.asm"), names(project));
        assertEquals("code", Files.readString(renamed));
        assertThrows(DomainException.class, () -> files.rename(renamed, project.resolve("taken.asm")));
    }

    @Test
    void changesOnlyTheLetterCase() throws IOException {
        Path source = Files.writeString(project.resolve("MAIN.ASM"), "code");

        files.rename(source, project.resolve("MAIN.asm"));

        // Compared as stored: on Windows both spellings name the same file.
        assertEquals(Set.of("MAIN.asm"), names(project));
    }

    @Test
    void deletesAFolderTreePermanentlyWhenAsked() throws IOException {
        Path folder = Files.createDirectories(project.resolve("assets/sprites"));
        Files.writeString(folder.resolve("hero.spr"), "x");

        assertEquals(Deletion.DELETED_PERMANENTLY, files.delete(project.resolve("assets"), true));
        assertFalse(Files.exists(project.resolve("assets")));
    }

    @Test
    void reportsChangesAnywhereInTheProject() throws Exception {
        Files.createDirectories(project.resolve("src/lib"));
        var changes = new Semaphore(0);
        try (AutoCloseable ignored = files.watch(project, changes::release)) {
            Files.writeString(project.resolve("src/lib/new.asm"), "x");

            assertTrue(changes.tryAcquire(10, TimeUnit.SECONDS), "A nested change must be reported");
        }
    }

    @Test
    void listsHiddenAndToolEntriesToo() throws Exception {
        Files.createDirectories(project.resolve(".idearm"));
        Files.writeString(project.resolve(".gitignore"), "build/");
        var changes = new Semaphore(0);

        assertEquals(Set.of(".idearm", ".gitignore"), files.list(project).stream()
                .map(entry -> entry.name()).collect(Collectors.toSet()));
        try (AutoCloseable ignored = files.watch(project, changes::release)) {
            Files.writeString(project.resolve(".idearm/workspace.json"), "{}");
            assertTrue(changes.tryAcquire(10, TimeUnit.SECONDS), "Changes in a hidden folder are shown too");
        }
    }

    @Test
    void aWatchedProjectCanStillBeRebuilt() throws Exception {
        // A build deletes build/debug and creates it again straight away (FileBuildWorkspace.invalidate).
        Path output = Files.createDirectories(project.resolve("build/debug/bin"));
        Files.writeString(output.resolve("main.exe"), "MZ");
        try (AutoCloseable ignored = files.watch(project, () -> { })) {
            for (int round = 0; round < 5; round++) {
                files.delete(project.resolve("build/debug"), true);
                Files.createDirectories(project.resolve("build/debug/bin"));
                Files.writeString(project.resolve("build/debug/bin/main.exe"), "MZ" + round);
            }
        }
        assertEquals("MZ4", Files.readString(project.resolve("build/debug/bin/main.exe")));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void offersTheRecycleBinOnWindows() {
        assertTrue(files.trashAvailable());
    }

    /**
     * Sends a real file to the Recycle Bin, so it only runs on request:
     * {@code mvn test -Didearm.test.recycleBin=true}.
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    @EnabledIfSystemProperty(named = "idearm.test.recycleBin", matches = "true")
    void movesEntriesToTheRecycleBin() throws IOException {
        Path file = Files.writeString(project.resolve("idearm-recycle-bin-check.txt"), "safe to delete");

        assertEquals(Deletion.MOVED_TO_TRASH, files.delete(file, false));
        assertFalse(Files.exists(file));
    }

    private static Set<String> names(Path folder) throws IOException {
        try (var entries = Files.list(folder)) {
            return entries.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }
}
