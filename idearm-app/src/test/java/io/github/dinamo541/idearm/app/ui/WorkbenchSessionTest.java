package io.github.dinamo541.idearm.app.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.github.dinamo541.idearm.app.editor.FakeEditorComponent;
import io.github.dinamo541.idearm.app.ui.WorkbenchSession.FileState;
import io.github.dinamo541.idearm.app.ui.WorkbenchSession.ProjectWorkspaceState;
import io.github.dinamo541.idearm.app.ui.WorkbenchSession.WorkbenchSessionState;
import io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel;
import io.github.dinamo541.idearm.app.viewmodel.EditorAreaViewModel;
import io.github.dinamo541.idearm.app.viewmodel.ProjectExplorerViewModel;
import io.github.dinamo541.idearm.app.viewmodel.StatusBarViewModel;
import io.github.dinamo541.idearm.app.viewmodel.WorkbenchServices;
import io.github.dinamo541.idearm.app.viewmodel.WorkbenchViewModel;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.port.ProjectRepository;
import io.github.dinamo541.idearm.infrastructure.persistence.TomlProjectRepository;
import io.github.dinamo541.idearm.infrastructure.workspace.FileBuildWorkspace;
import io.github.dinamo541.idearm.infrastructure.workspace.LocalProjectFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkbenchSessionTest {

    @TempDir
    Path tempDir;

    @Test
    void readingNonExistentFileReturnsEmptySession() {
        Path missing = tempDir.resolve("does-not-exist.properties");
        WorkbenchSessionState state = WorkbenchSession.read(missing);
        assertNotNull(state);
        assertNull(state.lastProject());
        assertTrue(state.openFiles().isEmpty());
        assertNull(state.activeFile());
        assertTrue(state.sidebarVisible());
        assertEquals(0.20, state.sidebarPosition(), 0.001);
        assertTrue(state.panelVisible());
        assertEquals(0.73, state.panelPosition(), 0.001);
        assertEquals("dark", state.theme());
        assertEquals("en", state.language());
        assertTrue(state.minimapVisible());
    }

    @Test
    void roundTripSessionProperties() {
        Path sessionFile = tempDir.resolve("session.properties");
        Path project = tempDir.resolve("my-proj").toAbsolutePath().normalize();
        Path file1 = project.resolve("src").resolve("main.asm");
        Path file2 = project.resolve("src").resolve("utils.asm");

        WorkbenchSessionState original = new WorkbenchSessionState(
                project,
                List.of(new FileState(file1, 42, 5), new FileState(file2, 10, 1)),
                file2,
                false,
                0.28,
                false,
                0.60,
                "TERMINAL",
                "light",
                "es",
                false
        );

        WorkbenchSession.write(sessionFile, original);
        assertTrue(Files.isRegularFile(sessionFile));

        WorkbenchSessionState restored = WorkbenchSession.read(sessionFile);
        assertEquals(project, restored.lastProject());
        assertEquals(2, restored.openFiles().size());
        assertEquals(file1, restored.openFiles().get(0).path());
        assertEquals(42, restored.openFiles().get(0).line());
        assertEquals(5, restored.openFiles().get(0).column());
        assertEquals(file2, restored.openFiles().get(1).path());
        assertEquals(10, restored.openFiles().get(1).line());
        assertEquals(1, restored.openFiles().get(1).column());
        assertEquals(file2, restored.activeFile());
        assertFalse(restored.sidebarVisible());
        assertEquals(0.28, restored.sidebarPosition(), 0.001);
        assertFalse(restored.panelVisible());
        assertEquals(0.60, restored.panelPosition(), 0.001);
        assertEquals("TERMINAL", restored.activePanelTab());
        assertEquals("light", restored.theme());
        assertEquals("es", restored.language());
        assertFalse(restored.minimapVisible());
    }

    @Test
    void corruptedFileFallsBackToEmptySession() throws IOException {
        Path sessionFile = tempDir.resolve("corrupt.properties");
        Files.writeString(sessionFile, "invalid-content:\u0000\u0000");

        WorkbenchSessionState state = WorkbenchSession.read(sessionFile);
        assertNotNull(state);
    }

    @Test
    void roundTripProjectWorkspace() throws IOException {
        Path projectDir = Files.createDirectories(tempDir.resolve("project-alpha"));
        Path src = Files.createDirectories(projectDir.resolve("src"));
        Path main = Files.writeString(src.resolve("main.asm"), "mov ax, 1");
        Path helper = Files.writeString(src.resolve("helper.asm"), "ret");

        WorkbenchSession.writeProjectWorkspace(
                projectDir,
                List.of(new FileState(main, 12, 1), new FileState(helper, 5, 3)),
                helper
        );

        ProjectWorkspaceState restored = WorkbenchSession.readProjectWorkspace(projectDir);
        assertTrue(restored.exists());
        assertEquals(2, restored.openFiles().size());
        assertEquals(main.toAbsolutePath().normalize(), restored.openFiles().get(0).path());
        assertEquals(12, restored.openFiles().get(0).line());
        assertEquals(1, restored.openFiles().get(0).column());
        assertEquals(helper.toAbsolutePath().normalize(), restored.openFiles().get(1).path());
        assertEquals(5, restored.openFiles().get(1).line());
        assertEquals(3, restored.openFiles().get(1).column());
        assertEquals(helper.toAbsolutePath().normalize(), restored.activeFile());
    }

    @Test
    void missingProjectWorkspaceReturnsNotExists() {
        ProjectWorkspaceState state = WorkbenchSession.readProjectWorkspace(tempDir.resolve("no-project"));
        assertFalse(state.exists());
        assertTrue(state.openFiles().isEmpty());
        assertNull(state.activeFile());
    }

    @Test
    void workbenchViewModelRestoresProjectWorkspaceAndCarets() throws IOException {
        Path projectDir = Files.createDirectories(tempDir.resolve("test-proj"));
        Path src = Files.createDirectories(projectDir.resolve("src"));
        Path main = Files.writeString(src.resolve("main.asm"), "line1\nline2\nline3\nline4\n");
        Path extra = Files.writeString(src.resolve("extra.asm"), "code1\ncode2\ncode3\ncode4\ncode5\n");
        Files.writeString(projectDir.resolve("idearm.toml"), "# test");

        WorkbenchSession.writeProjectWorkspace(
                projectDir,
                List.of(new FileState(main, 3, 1), new FileState(extra, 4, 1)),
                extra
        );

        var editorArea = new EditorAreaViewModel(FakeEditorComponent::new);
        var statusBar = new StatusBarViewModel();
        var vm = createWorkbench(editorArea, statusBar, repositoryReturning(Project.hello("TestWorkspace")));

        try {
            vm.openProject(projectDir);

            assertEquals(2, editorArea.getDocuments().size());
            assertEquals(extra.toAbsolutePath().normalize(), editorArea.getActiveDocument().getFilePath());
            assertEquals(4, editorArea.getActiveDocument().caretLineProperty().get());

            // Check the other document caret
            var mainDoc = editorArea.getDocuments().stream()
                    .filter(d -> d.getFilePath().equals(main.toAbsolutePath().normalize()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(3, mainDoc.caretLineProperty().get());
        } finally {
            vm.dispose();
        }
    }

    @Test
    void switchingProjectsSavesPreviousAndLoadsNew() throws IOException {
        Path proj1 = Files.createDirectories(tempDir.resolve("proj1"));
        Path src1 = Files.createDirectories(proj1.resolve("src"));
        Path file1 = Files.writeString(src1.resolve("main.asm"), "code in proj1");
        Files.writeString(proj1.resolve("idearm.toml"), "# proj1");

        Path proj2 = Files.createDirectories(tempDir.resolve("proj2"));
        Path src2 = Files.createDirectories(proj2.resolve("src"));
        Path file2 = Files.writeString(src2.resolve("main.asm"), "code in proj2");
        Files.writeString(proj2.resolve("idearm.toml"), "# proj2");

        var editorArea = new EditorAreaViewModel(FakeEditorComponent::new);
        var statusBar = new StatusBarViewModel();
        var vm = createWorkbench(editorArea, statusBar, repositoryReturning(Project.hello("SwitchTest")));

        try {
            vm.openProject(proj1);
            assertEquals(1, editorArea.getDocuments().size());
            assertEquals(file1.toAbsolutePath().normalize(), editorArea.getActiveDocument().getFilePath());

            // Open proj2
            vm.openProject(proj2);
            assertEquals(1, editorArea.getDocuments().size());
            assertEquals(file2.toAbsolutePath().normalize(), editorArea.getActiveDocument().getFilePath());

            // Verify proj1's workspace was saved
            var p1State = WorkbenchSession.readProjectWorkspace(proj1);
            assertTrue(p1State.exists());
            assertEquals(1, p1State.openFiles().size());
            assertEquals(file1.toAbsolutePath().normalize(), p1State.openFiles().get(0).path());
        } finally {
            vm.dispose();
        }
    }

    private WorkbenchViewModel createWorkbench(EditorAreaViewModel editorArea,
                                                      StatusBarViewModel statusBar,
                                                      ProjectRepository repository) {
        var services = new WorkbenchServices(
                repository,
                id -> Optional.empty(),
                new FileBuildWorkspace(),
                new NoToolRunner(),
                tempDir.resolve("staging"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new io.github.dinamo541.idearm.infrastructure.persistence.FileBreakpointStore(),
                Duration.ofSeconds(10),
                new LocalProjectFiles()
        );
        return new WorkbenchViewModel(
                new ProjectExplorerViewModel(),
                editorArea,
                new BottomPanelViewModel(),
                statusBar,
                services
        );
    }

    private static final class NoToolRunner implements io.github.dinamo541.idearm.domain.port.ToolRunner {
        @Override
        public io.github.dinamo541.idearm.domain.build.ToolRunResult run(
                io.github.dinamo541.idearm.domain.build.BuildPlan plan, Path projectRoot,
                io.github.dinamo541.idearm.domain.model.ResolvedToolchain tools,
                io.github.dinamo541.idearm.domain.build.CancellationToken cancellation, Duration timeout) {
            return new io.github.dinamo541.idearm.domain.build.ToolRunResult(
                    io.github.dinamo541.idearm.domain.build.BuildStatus.FAILED, List.of(), null, "no toolchain in tests");
        }

        @Override
        public void release(io.github.dinamo541.idearm.domain.build.ToolRunResult result) {}
    }

    private static ProjectRepository repositoryReturning(Project project) {
        return new ProjectRepository() {
            @Override public boolean exists(Path path) { return true; }
            @Override public Project load(Path path) { return project; }
            @Override public void save(Path path, Project proj) {}
        };
    }
}
