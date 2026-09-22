package io.github.dinamo541.idearm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.port.ProjectRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CreateProjectTest {

    @TempDir
    Path tempDir;

    @Test
    void createsProjectStructureAndPersistsToml() {
        Map<Path, Project> savedProjects = new HashMap<>();
        ProjectRepository repo = new ProjectRepository() {
            @Override
            public Project load(Path projectRoot) {
                return savedProjects.get(projectRoot);
            }

            @Override
            public void save(Path projectRoot, Project project) {
                savedProjects.put(projectRoot, project);
            }
        };

        CreateProject useCase = new CreateProject(repo);
        Project project = useCase.execute(
                tempDir,
                "DemoApp",
                "dos-exe-16",
                "8086",
                "borland-tasm",
                ">=3.2"
        );

        assertNotNull(project);
        assertEquals("DemoApp", project.info().name());
        assertEquals("dos-exe-16", project.target().profile());
        assertEquals("8086", project.target().cpu());
        assertEquals("borland-tasm", project.toolchain().id());

        Path projectRoot = tempDir.resolve("DemoApp");
        assertTrue(Files.exists(projectRoot.resolve("src").resolve("main.asm")));
        assertTrue(Files.exists(projectRoot.resolve(".gitignore")));
        assertTrue(savedProjects.containsKey(projectRoot));
    }

    @Test
    void rejectsInvalidProjectNames() {
        ProjectRepository repo = new ProjectRepository() {
            @Override public Project load(Path p) { return null; }
            @Override public void save(Path p, Project proj) {}
        };

        CreateProject useCase = new CreateProject(repo);

        assertThrows(DomainException.class, () ->
                useCase.execute(tempDir, "Invalid/Name", "dos-exe-16", "8086", "borland-tasm", ">=3.2"));

        assertThrows(DomainException.class, () ->
                useCase.execute(tempDir, "   ", "dos-exe-16", "8086", "borland-tasm", ">=3.2"));
    }

    /** ".." used to pass the character check and created the project in the parent folder. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"..", ".", "name.", "CON", "nul"})
    void rejectsNamesThatAreNotAFolderOfTheirOwn(String name) throws IOException {
        Path parent = java.nio.file.Files.createDirectories(tempDir.resolve("parent").resolve("child"));
        CreateProject useCase = new CreateProject(new ProjectRepository() {
            @Override public Project load(Path p) { return null; }
            @Override public void save(Path p, Project proj) { throw new AssertionError("Nothing may be saved"); }
        });

        var failure = assertThrows(DomainException.class,
                () -> useCase.execute(parent, name, "dos-exe-16", "8086", "borland-tasm", ">=3.2"));
        assertEquals("project.name.invalid", failure.code());
        assertFalse(java.nio.file.Files.exists(tempDir.resolve("parent").resolve("src")), "Nothing is written above");
    }

    @Test
    void rejectsExistingProjectWithToml() throws IOException {
        Path existing = Files.createDirectories(tempDir.resolve("ExistingProj"));
        Files.writeString(existing.resolve("idearm.toml"), "# existing");

        ProjectRepository repo = new ProjectRepository() {
            @Override public Project load(Path p) { return null; }
            @Override public void save(Path p, Project proj) {}
        };

        CreateProject useCase = new CreateProject(repo);
        assertThrows(DomainException.class, () ->
                useCase.execute(tempDir, "ExistingProj", "dos-exe-16", "8086", "borland-tasm", ">=3.2"));
    }

    @Test
    void createsWinPe64ProjectWithNasmStarter() throws IOException {
        Map<Path, Project> savedProjects = new HashMap<>();
        ProjectRepository repo = new ProjectRepository() {
            @Override public Project load(Path p) { return savedProjects.get(p); }
            @Override public void save(Path p, Project proj) { savedProjects.put(p, proj); }
        };

        CreateProject useCase = new CreateProject(repo);
        Project project = useCase.execute(
                tempDir,
                "Hello64",
                "win-pe64-console",
                "x86-64",
                "nasm",
                ">=2.14"
        );

        assertNotNull(project);
        assertEquals("win-pe64-console", project.target().profile());
        assertEquals("x86-64", project.target().cpu());
        assertEquals("nasm", project.toolchain().id());

        Path mainAsm = tempDir.resolve("Hello64").resolve("src").resolve("main.asm");
        assertTrue(Files.exists(mainAsm));
        String content = Files.readString(mainAsm);
        assertTrue(content.contains("default rel"));
        assertTrue(content.contains("ExitProcess"));
    }
}
