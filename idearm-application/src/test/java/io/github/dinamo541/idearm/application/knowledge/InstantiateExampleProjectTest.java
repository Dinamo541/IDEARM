package io.github.dinamo541.idearm.application.knowledge;

import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.port.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InstantiateExampleProjectTest {

    @TempDir
    Path tempDir;

    private InMemoryProjectRepository repository;
    private InstantiateExampleProject useCase;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProjectRepository();
        useCase = new InstantiateExampleProject(repository);
    }

    @Test
    @DisplayName("Wraps raw assembly snippet into a runnable DOS 16-bit template with clean exit")
    void instantiatesProjectWithSnippetWrappedInDosTemplate() throws IOException {
        String snippet = """
                mov ax, 5
                shl ax, 1
                """;

        var request = new InstantiateExampleProject.Request(
                tempDir,
                "Example_SHL",
                snippet,
                "dos-exe-16",
                "8086",
                "borland-tasm"
        );

        InstantiateExampleProject.Result result = useCase.execute(request);

        assertNotNull(result.project());
        assertTrue(Files.exists(result.projectDirectory()));
        assertTrue(Files.exists(result.mainSourceFile()));

        String mainContent = Files.readString(result.mainSourceFile());
        assertTrue(mainContent.contains(".MODEL small"), "Must contain DOS memory model");
        assertTrue(mainContent.contains("MAIN PROC"), "Must contain main procedure");
        assertTrue(mainContent.contains("shl ax, 1"), "Must contain the example snippet");
        assertTrue(mainContent.contains("4C00h"), "Must terminate cleanly with INT 21h AH=4Ch");

        assertTrue(repository.savedProjects.containsKey(result.projectDirectory()));
    }

    @Test
    @DisplayName("Preserves already complete program without double-wrapping")
    void instantiatesProjectPreservingCompleteAssemblySource() throws IOException {
        String fullSource = """
                .MODEL small
                .STACK 200h
                .DATA
                    msg db 'Hello$'
                .CODE
                MAIN PROC
                    mov ah, 09h
                    int 21h
                    mov ax, 4C00h
                    int 21h
                MAIN ENDP
                END MAIN
                """;

        var request = new InstantiateExampleProject.Request(
                tempDir,
                "FullProgram",
                fullSource,
                "dos-exe-16",
                "8086",
                "microsoft-masm"
        );

        InstantiateExampleProject.Result result = useCase.execute(request);
        String mainContent = Files.readString(result.mainSourceFile());

        assertEquals(fullSource.trim() + "\n", mainContent);
    }

    @Test
    @DisplayName("Generates unique project directory names without overwriting existing files")
    void generatesUniqueFolderNamesWithoutOverwriting() throws IOException {
        String snippet = "nop";

        var request1 = new InstantiateExampleProject.Request(
                tempDir, "DemoProject", snippet, "dos-exe-16", "8086", "borland-tasm"
        );
        InstantiateExampleProject.Result res1 = useCase.execute(request1);
        assertEquals("DemoProject", res1.projectDirectory().getFileName().toString());

        // Modify a file in the first project to prove it is not overwritten
        Path canary = res1.projectDirectory().resolve("canary.txt");
        Files.writeString(canary, "untouched");

        var request2 = new InstantiateExampleProject.Request(
                tempDir, "DemoProject", snippet, "dos-exe-16", "8086", "borland-tasm"
        );
        InstantiateExampleProject.Result res2 = useCase.execute(request2);
        assertEquals("DemoProject_1", res2.projectDirectory().getFileName().toString());

        // Ensure first project canary remains intact
        assertTrue(Files.exists(canary));
        assertEquals("untouched", Files.readString(canary));
    }

    @Test
    @DisplayName("Instantiates 64-bit template when target profile is 64-bit")
    void instantiatesNative64BitTemplate() throws IOException {
        String snippet = "mov rax, 60";

        var request = new InstantiateExampleProject.Request(
                tempDir, "NativeExample", snippet, "win-pe64-console", "x86-64", "nasm"
        );

        InstantiateExampleProject.Result result = useCase.execute(request);
        String mainContent = Files.readString(result.mainSourceFile());

        assertTrue(mainContent.contains("default rel"));
        assertTrue(mainContent.contains("global main"));
        assertTrue(mainContent.contains("mov rax, 60"));
    }

    private static class InMemoryProjectRepository implements ProjectRepository {
        final Map<Path, Project> savedProjects = new HashMap<>();

        @Override
        public Project load(Path directory) {
            return savedProjects.get(directory);
        }

        @Override
        public void save(Path directory, Project project) {
            savedProjects.put(directory, project);
            try {
                Files.createDirectories(directory);
                Files.writeString(directory.resolve("idearm.toml"), "# In-memory test toml\n");
            } catch (IOException ignored) {}
        }

        @Override
        public boolean exists(Path directory) {
            return savedProjects.containsKey(directory);
        }
    }
}
