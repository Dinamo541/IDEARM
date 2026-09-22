package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.port.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ImportProjectTest {

    private final Map<Path, Project> savedProjects = new HashMap<>();
    private ProjectRepository repository;
    private ImportProject importProject;

    @BeforeEach
    void setUp() {
        savedProjects.clear();
        repository = new ProjectRepository() {
            @Override
            public Project load(Path projectRoot) {
                Project p = savedProjects.get(projectRoot.toAbsolutePath().normalize());
                if (p == null) throw new DomainException("project.not-found", "Not found");
                return p;
            }

            @Override
            public void save(Path projectRoot, Project project) {
                savedProjects.put(projectRoot.toAbsolutePath().normalize(), project);
            }
        };
        importProject = new ImportProject(repository);
    }

    @Test
    void importsMultiModuleProjectWithMainEntry(@TempDir Path tempDir) throws IOException {
        Path projDir = Files.createDirectory(tempDir.resolve("MyGame"));
        Path srcDir = Files.createDirectory(projDir.resolve("src"));

        Files.writeString(srcDir.resolve("main.asm"), """
                .model small
                .code
                main proc
                    ret
                main endp
                end main
                """);
        Files.writeString(srcDir.resolve("extras.asm"), """
                .model small
                .code
                calc proc
                    ret
                calc endp
                end
                """);

        Project project = importProject.execute(projDir);

        assertNotNull(project);
        assertEquals("MyGame", project.info().name());
        assertEquals("src/main.asm", project.sources().entry());
        assertEquals(1, project.sources().modules().size());
        assertEquals("src/extras.asm", project.sources().modules().get(0));
        assertTrue(savedProjects.containsKey(projDir.toAbsolutePath().normalize()));
        assertTrue(Files.exists(projDir.resolve(".gitignore")));
    }

    @Test
    void infersMasmWhenMasmDirectivesPresent(@TempDir Path tempDir) throws IOException {
        Path projDir = Files.createDirectory(tempDir.resolve("MasmApp"));
        Files.writeString(projDir.resolve("MAIN.ASM"), """
                .386
                .model flat
                option scoped
                invoke ExitProcess, 0
                end
                """);

        Project project = importProject.execute(projDir);
        assertEquals("microsoft-masm", project.toolchain().id());
        assertEquals(">=6.11", project.toolchain().version());
    }

    @Test
    void infersTasmWhenTasmDirectivesPresent(@TempDir Path tempDir) throws IOException {
        Path projDir = Files.createDirectory(tempDir.resolve("TasmApp"));
        Files.writeString(projDir.resolve("MAIN.ASM"), """
                ideal
                model small
                p386
                ends
                end
                """);

        Project project = importProject.execute(projDir);
        assertEquals("borland-tasm", project.toolchain().id());
        assertEquals(">=3.2", project.toolchain().version());
    }

    @Test
    void respectsExplicitPreferredToolchain(@TempDir Path tempDir) throws IOException {
        Path projDir = Files.createDirectory(tempDir.resolve("CustomApp"));
        Files.writeString(projDir.resolve("MAIN.ASM"), "; simple code");

        Project project = importProject.execute(new ImportProject.Request(
                projDir, "CustomApp", "dos-exe-16", "8086", "microsoft-masm"
        ));

        assertEquals("microsoft-masm", project.toolchain().id());
    }

    @Test
    void rejectsAlreadyConfiguredDirectory(@TempDir Path tempDir) throws IOException {
        Path projDir = Files.createDirectory(tempDir.resolve("Existing"));
        Files.createFile(projDir.resolve("idearm.toml"));
        Files.createFile(projDir.resolve("MAIN.ASM"));

        assertThrows(DomainException.class, () -> importProject.execute(projDir));
    }

    @Test
    void rejectsDirectoryWithNoAsmSources(@TempDir Path tempDir) throws IOException {
        Path projDir = Files.createDirectory(tempDir.resolve("EmptyProj"));
        Files.writeString(projDir.resolve("README.md"), "No code here");

        assertThrows(DomainException.class, () -> importProject.execute(projDir));
    }

    @Test
    void marksPreExistingBuildAndDistDirectories(@TempDir Path tempDir) throws IOException {
        Path projDir = Files.createDirectory(tempDir.resolve("LegacyApp"));
        Files.writeString(projDir.resolve("main.asm"), ".code\nmain proc\nret\nmain endp\nend main");
        Path buildDir = Files.createDirectory(projDir.resolve("build"));
        Path distDir = Files.createDirectory(projDir.resolve("dist"));

        importProject.execute(projDir);

        Path buildMarker = buildDir.resolve(io.github.dinamo541.idearm.domain.port.BuildWorkspace.GENERATED_MARKER);
        Path distMarker = distDir.resolve(io.github.dinamo541.idearm.domain.port.BuildWorkspace.GENERATED_MARKER);

        assertTrue(Files.exists(buildMarker));
        assertTrue(Files.exists(distMarker));
        assertEquals(io.github.dinamo541.idearm.domain.port.BuildWorkspace.GENERATED_MARKER_CONTENT, Files.readString(buildMarker));
        assertEquals(io.github.dinamo541.idearm.domain.port.BuildWorkspace.GENERATED_MARKER_CONTENT, Files.readString(distMarker));
    }
}
