package io.github.dinamo541.idearm.app.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.app.editor.FakeEditorComponent;
import io.github.dinamo541.idearm.domain.build.BuildPlan;
import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.build.ToolRunResult;
import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.files.EntryNames;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.ResolvedToolchain;
import io.github.dinamo541.idearm.domain.port.BreakpointStore;
import io.github.dinamo541.idearm.domain.port.ProjectRepository;
import io.github.dinamo541.idearm.domain.port.ToolRunner;
import io.github.dinamo541.idearm.infrastructure.workspace.FileBuildWorkspace;
import io.github.dinamo541.idearm.infrastructure.workspace.LocalProjectFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What the rest of the workbench does when files change from the explorer. */
class ExplorerConnectionsTest {

    @TempDir
    Path root;

    private Project project = Project.hello("GAME");
    private final List<Breakpoint> breakpoints = new ArrayList<>();
    private EditorAreaViewModel editors;
    private WorkbenchViewModel workbench;
    private ProjectExplorerViewModel explorer;

    @BeforeEach
    void openProject() throws IOException {
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/main.asm"), "; entry\n.MODEL small\n.CODE\nEND\n");
        Files.writeString(root.resolve("idearm.toml"), "schema = 1\n");

        ProjectRepository repository = new ProjectRepository() {
            @Override public Project load(Path projectRoot) { return project; }
            @Override public void save(Path projectRoot, Project updated) { project = updated; }
            @Override public boolean exists(Path projectRoot) {
                return Files.exists(projectRoot.resolve("idearm.toml"));
            }
        };
        BreakpointStore store = new BreakpointStore() {
            @Override public List<Breakpoint> loadBreakpoints(Path projectRoot) { return List.copyOf(breakpoints); }
            @Override public void saveBreakpoints(Path projectRoot, List<Breakpoint> updated) {
                breakpoints.clear();
                breakpoints.addAll(updated);
            }
        };
        ToolRunner runner = new ToolRunner() {
            @Override public ToolRunResult run(BuildPlan plan, Path projectRoot, ResolvedToolchain tools,
                                               CancellationToken cancellation, Duration timeout) {
                return new ToolRunResult(BuildStatus.FAILED, List.of(), null, "");
            }
        };
        var services = new WorkbenchServices(repository, id -> Optional.empty(), new FileBuildWorkspace(), runner,
                root.resolveSibling("staging"), List.of(), List.of(), List.of(), List.of(), store,
                Duration.ofSeconds(5), new LocalProjectFiles());

        editors = new EditorAreaViewModel(FakeEditorComponent::new);
        explorer = new ProjectExplorerViewModel();
        workbench = new WorkbenchViewModel(explorer, editors, new BottomPanelViewModel(), new StatusBarViewModel(),
                services);
        workbench.openProject(root);
    }

    @AfterEach
    void close() {
        workbench.dispose();
    }

    @Test
    void theEntryNamedByTheProjectOpensWithIt() {
        assertEquals(root.resolve("src/main.asm"), editors.getActiveDocument().getFilePath());
    }

    @Test
    void aNewFileGetsALowerCaseExtensionAndOpensInTheEditor() throws IOException {
        Path created = explorer.createFile(root.resolve("src"), "Utils.ASM");

        assertEquals(Set.of("main.asm", "Utils.asm"), stored(root.resolve("src")));
        assertEquals(created, editors.getActiveDocument().getFilePath());
    }

    @Test
    void typingANameExplainsWhatWillHappen() {
        var lowered = explorer.check(root.resolve("src"), "Video.ASM", EntryNames.Kind.FILE, null);
        var taken = explorer.check(root.resolve("src"), "MAIN.asm", EntryNames.Kind.FILE, null);
        var tooLong = explorer.check(root.resolve("src"), "functions.asm", EntryNames.Kind.FILE, null);

        assertEquals("explorer.name.lowerCaseExtension", lowered.message().key());
        assertTrue(taken.blocking());
        assertEquals(EntryNames.Severity.WARNING, tooLong.severity(), "A DOS project warns about 8.3 names");
    }

    @Test
    void renamingTheEntryMovesItsTabItsBreakpointsAndTheProjectDescription() throws IOException {
        Path main = root.resolve("src/main.asm");
        workbench.toggleBreakpoint(main, 3);

        Path renamed = explorer.rename(main, "Game.asm");

        assertEquals(renamed, editors.getActiveDocument().getFilePath());
        assertEquals(List.of("src/Game.asm"), breakpoints.stream().map(Breakpoint::path).toList());
        assertEquals("src/Game.asm", project.sources().entry(), "The next build must find the entry");
        assertEquals(Set.of("Game.asm"), stored(root.resolve("src")));
    }

    @Test
    void renamingAFolderKeepsTheFilesInsideItOpen() throws IOException {
        Path io = explorer.createFile(root.resolve("src"), "lib/io.asm");

        explorer.rename(root.resolve("src/lib"), "disk");

        assertTrue(editors.getDocuments().stream()
                .anyMatch(doc -> doc.getFilePath().equals(root.resolve("src/disk/io.asm"))));
        assertFalse(Files.exists(io));
    }

    @Test
    void deletingAFileClosesItsTabAndForgetsItsBreakpoints() {
        Path utils = explorer.createFile(root.resolve("src"), "utils.asm");
        workbench.toggleBreakpoint(utils, 1);

        explorer.delete(utils, true);

        assertTrue(editors.getDocuments().stream().noneMatch(doc -> doc.getFilePath().equals(utils)));
        assertTrue(breakpoints.isEmpty());
        assertFalse(Files.exists(utils));
    }

    @Test
    void newEntriesGoIntoTheSelectedFolderOrNextToTheSelectedFile() {
        assertEquals(root.resolve("src"), explorer.targetFolder(root.resolve("src/main.asm"), false));
        assertEquals(root.resolve("src"), explorer.targetFolder(root.resolve("src"), true));
        assertEquals(root, explorer.targetFolder(null, false));
    }

    private static Set<String> stored(Path folder) throws IOException {
        try (var entries = Files.list(folder)) {
            return entries.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }
}
