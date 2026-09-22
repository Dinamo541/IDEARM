package io.github.dinamo541.idearm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.files.EntryNames.Kind;
import io.github.dinamo541.idearm.domain.files.EntryNames.Severity;
import io.github.dinamo541.idearm.domain.port.ProjectFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManageProjectFilesTest {

    @TempDir
    Path root;

    private final ManageProjectFiles explorer = new ManageProjectFiles(new DiskFiles());

    @BeforeEach
    void createProject() throws IOException {
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/main.asm"), "; entry");
    }

    @Test
    void showsEveryEntryOnDiskWithFoldersFirst() throws IOException {
        Files.createDirectories(root.resolve(".git"));
        Files.createDirectories(root.resolve(".idearm"));
        Files.createDirectories(root.resolve("build"));
        Files.writeString(root.resolve("idearm.toml"), "schema = 1");
        Files.writeString(root.resolve("README.md"), "x");
        Files.writeString(root.resolve(".gitignore"), "build/");
        Files.writeString(root.resolve("desktop.ini"), "");
        Files.writeString(root.resolve("notes.TXT"), "");

        List<String> names = explorer.children(root, root).stream().map(ProjectFiles.Entry::name).toList();

        // Like VS Code, nothing in the project folder is left out, hidden and tool files included.
        assertEquals(List.of(".git", ".idearm", "build", "src", ".gitignore", "desktop.ini", "idearm.toml",
                "notes.TXT", "README.md"), names);
    }

    @Test
    void createsFilesWithALowerCaseExtensionAndTheTypedName() throws IOException {
        Path created = explorer.createFile(root, root.resolve("src"), "GameLoop.ASM", true);

        assertEquals("GameLoop.asm", created.getFileName().toString());
        assertEquals(Set.of("main.asm", "GameLoop.asm"), stored(root.resolve("src")));
    }

    @Test
    void createsTheFoldersANestedNameAsksFor() {
        Path created = explorer.createFile(root, root.resolve("src"), "lib/io/disk.INC", true);

        assertEquals(root.resolve("src/lib/io/disk.inc"), created);
        assertTrue(Files.isRegularFile(created));
    }

    @Test
    void aTrailingSlashCreatesAFolder() {
        Path created = explorer.createFile(root, root, "assets/", true);

        assertTrue(Files.isDirectory(created));
        assertEquals("assets", created.getFileName().toString());
    }

    @Test
    void reportsAnExistingNameWhileTyping() {
        var check = explorer.check(root, root.resolve("src"), "MAIN.asm", Kind.FILE, true, null);

        assertEquals(Severity.ERROR, check.severity());
        assertEquals("explorer.name.exists", check.code());
        assertThrows(DomainException.class, () -> explorer.createFile(root, root.resolve("src"), "main.asm", true));
    }

    /** On Linux main.asm and MAIN.asm are two files, but Windows and the DOS tools would see only one. */
    @Test
    void aNameThatDiffersOnlyInCaseIsRefusedOnACaseSensitiveFileSystem() {
        var linux = new ManageProjectFiles(new DiskFiles() {
            @Override public boolean exists(Path path) {
                return super.exists(path) && list(path.getParent()).stream()
                        .anyMatch(entry -> entry.name().equals(path.getFileName().toString()));
            }
        });

        var check = linux.check(root, root.resolve("src"), "MAIN.asm", Kind.FILE, true, null);
        assertEquals(Severity.ERROR, check.severity());
        assertEquals(List.of("main.asm"), check.arguments());

        // Changing the case of the entry itself is still a rename.
        Path main = root.resolve("src/main.asm");
        assertFalse(linux.check(root, root.resolve("src"), "Main.asm", Kind.FILE, true, main).blocking());
    }

    @Test
    void warnsAboutNamesDosToolsCannotSee() {
        var check = explorer.check(root, root.resolve("src"), "functions.asm", Kind.FILE, true, null);

        assertEquals(Severity.WARNING, check.severity());
        assertEquals(root.resolve("src/functions.asm"),
                explorer.createFile(root, root.resolve("src"), "functions.asm", true), "A warning does not block");
    }

    @Test
    void renamesToADifferentCaseOnly() throws IOException {
        Path file = explorer.createFile(root, root.resolve("src"), "Video.asm", false);

        var check = explorer.check(root, root.resolve("src"), "VIDEO.asm", Kind.FILE, false, file);
        Path renamed = explorer.rename(root, file, "VIDEO.asm", false);

        assertTrue(!check.blocking(), check.toString());
        assertEquals("VIDEO.asm", renamed.getFileName().toString());
        assertTrue(stored(root.resolve("src")).contains("VIDEO.asm"));
    }

    @Test
    void refusesToRenameOntoAnotherEntryOrToMoveIt() {
        Path file = explorer.createFile(root, root.resolve("src"), "io.asm", false);

        assertThrows(DomainException.class, () -> explorer.rename(root, file, "main.asm", false));
        assertThrows(DomainException.class, () -> explorer.rename(root, file, "lib/io.asm", false));
    }

    @Test
    void theProjectFolderItselfCanNeitherBeRenamedNorDeleted() {
        assertThrows(DomainException.class, () -> explorer.rename(root, root, "other", false));
        assertThrows(DomainException.class, () -> explorer.delete(root, root, true));
        assertThrows(DomainException.class, () -> explorer.delete(root, root.getParent(), true));
    }

    @Test
    void deletesEntriesInsideTheProject() {
        Path folder = explorer.createFolder(root, root, "scratch", false);

        assertEquals(ProjectFiles.Deletion.DELETED_PERMANENTLY, explorer.delete(root, folder, true));
        assertTrue(Files.notExists(folder));
    }

    private static Set<String> stored(Path folder) throws IOException {
        try (var entries = Files.list(folder)) {
            return entries.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    /** The port on the local disk, without the infrastructure layer (which this module must not see). */
    private static class DiskFiles implements ProjectFiles {
        @Override public List<Entry> list(Path directory) {
            try (var entries = Files.list(directory)) {
                return entries.map(path -> new Entry(path, Files.isDirectory(path))).toList();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        @Override public boolean exists(Path path) {
            return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
        }

        @Override public boolean isDirectory(Path path) {
            return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
        }

        @Override public boolean isSameEntry(Path first, Path second) {
            try {
                return exists(first) && exists(second) && Files.isSameFile(first, second);
            } catch (IOException failure) {
                return false;
            }
        }

        @Override public Path createFile(Path file) {
            try {
                Files.createDirectories(file.getParent());
                return Files.createFile(file);
            } catch (IOException failure) {
                throw new DomainException("explorer.create.failed", failure.toString());
            }
        }

        @Override public Path createDirectory(Path directory) {
            try {
                return Files.createDirectories(directory);
            } catch (IOException failure) {
                throw new DomainException("explorer.create.failed", failure.toString());
            }
        }

        @Override public Path rename(Path source, Path target) {
            try {
                Path temporary = source.resolveSibling("rename.tmp");
                Files.move(source, temporary);
                return Files.move(temporary, target);
            } catch (IOException failure) {
                throw new DomainException("explorer.rename.failed", failure.toString());
            }
        }

        @Override public boolean trashAvailable() {
            return false;
        }

        @Override public Deletion delete(Path target, boolean permanently) {
            try (var tree = Files.walk(target)) {
                for (Path path : new ArrayList<>(tree.sorted(Comparator.reverseOrder()).toList())) {
                    Files.delete(path);
                }
                return Deletion.DELETED_PERMANENTLY;
            } catch (IOException failure) {
                throw new DomainException("explorer.delete.failed", failure.toString());
            }
        }

        @Override public AutoCloseable watch(Path root, Runnable onChange) {
            return () -> { };
        }
    }
}
