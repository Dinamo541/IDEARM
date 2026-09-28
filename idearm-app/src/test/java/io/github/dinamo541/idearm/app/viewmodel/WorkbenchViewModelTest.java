package io.github.dinamo541.idearm.app.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.app.editor.FakeEditorComponent;
import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.BuildPlan;
import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.build.ToolRunResult;
import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.DebugEvent;
import io.github.dinamo541.idearm.domain.debug.RegisterState;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.ResolvedToolchain;
import io.github.dinamo541.idearm.domain.port.BreakpointStore;
import io.github.dinamo541.idearm.domain.port.ProjectRepository;
import io.github.dinamo541.idearm.domain.port.ToolRegistry;
import io.github.dinamo541.idearm.domain.port.ToolRunner;
import io.github.dinamo541.idearm.infrastructure.workspace.FileBuildWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkbenchViewModelTest {

    @TempDir
    Path tempDir;

    private final ToolRegistry emptyRegistry = id -> Optional.empty();

    @Test
    void missingRecentEntriesLeaveTheCurrentProjectAndEditorUntouched() throws IOException {
        var area = new EditorAreaViewModel(FakeEditorComponent::new);
        var vm = workbench(area, new StatusBarViewModel(), repositoryReturning(Project.hello("Recent")));
        try {
            var source = Files.writeString(tempDir.resolve("main.asm"), "mov ax, 1");
            vm.openProject(tempDir);
            var doc = area.openFile(source);
            for (var kind : io.github.dinamo541.idearm.domain.model.RecentItem.Kind.values()) {
                assertFalse(vm.openRecent(new io.github.dinamo541.idearm.domain.model.RecentItem(kind, tempDir.resolve("missing"))));
                assertEquals(tempDir, vm.getCurrentProjectPath());
                assertEquals(doc, area.getActiveDocument());
            }
            assertFalse(Files.exists(tempDir.resolve("missing")));
        } finally { vm.dispose(); }
    }

    @Test
    void reopeningAnAlreadyOpenFileUpdatesHistoryWithoutAnotherTab() throws IOException {
        var area = new EditorAreaViewModel(FakeEditorComponent::new);
        var vm = workbench(area, new StatusBarViewModel(), repositoryReturning(Project.hello("Recent")));
        try {
            var first = Files.writeString(tempDir.resolve("first.asm"), "one");
            var second = Files.writeString(tempDir.resolve("second.asm"), "two");
            area.openFile(first); area.openFile(second);
            assertTrue(vm.openRecent(new io.github.dinamo541.idearm.domain.model.RecentItem(
                    io.github.dinamo541.idearm.domain.model.RecentItem.Kind.FILE, first)));
            assertEquals(first, vm.getRecentItems().getItems().getFirst().path());
            assertEquals(2, area.getDocuments().size());
        } finally { vm.dispose(); }
    }

    @Test
    void openProjectSetsCurrentProjectAndStatusBadges() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("src"));
        // Named exactly as the project's entry: Linux file names are case-sensitive.
        Path mainAsm = Files.writeString(src.resolve("main.asm"), ".MODEL small\n.CODE\nEND");
        Files.writeString(tempDir.resolve("idearm.toml"), "# test project");

        var editorArea = new EditorAreaViewModel(FakeEditorComponent::new);
        var statusBar = new StatusBarViewModel();
        var viewModel = workbench(editorArea, statusBar, repositoryReturning(Project.hello("TestProj")));

        assertFalse(viewModel.isBusy());
        viewModel.openProject(tempDir);

        assertNotNull(viewModel.getCurrentProject());
        assertEquals("TestProj", viewModel.getCurrentProject().info().name());
        assertEquals("dos-exe-16 · 8086", statusBar.getTargetProfile());
        assertEquals("borland-tasm", statusBar.getToolchain());
        assertEquals("status.project.opened", statusBar.getStatus().key());

        assertEquals(1, editorArea.getDocuments().size());
        assertEquals(mainAsm, editorArea.getActiveDocument().getFilePath());
    }

    @Test
    void anUnreadableDescriptorIsReportedInsteadOfInventingAProject() throws IOException {
        Files.writeString(tempDir.resolve("idearm.toml"), "schema = broken");
        var statusBar = new StatusBarViewModel();
        var bottomPanel = new BottomPanelViewModel();
        ProjectRepository failing = new ProjectRepository() {
            @Override
            public boolean exists(Path projectRoot) {
                return true; // The description is there, it just cannot be parsed.
            }

            @Override
            public Project load(Path projectRoot) {
                throw new DomainException("project.schema.invalid", "schema is not a number");
            }

            @Override
            public void save(Path projectRoot, Project project) {
            }
        };

        var viewModel = new WorkbenchViewModel(new ProjectExplorerViewModel(),
                new EditorAreaViewModel(FakeEditorComponent::new), bottomPanel, statusBar, services(failing));
        viewModel.openProject(tempDir);

        // Build and Run stay disabled while the file is broken, and the reason is on screen.
        assertNull(viewModel.getCurrentProject());
        assertEquals("status.project.invalid", statusBar.getStatus().key());
        assertTrue(bottomPanel.getBuildText().contains("schema is not a number"), bottomPanel.getBuildText());
    }

    @Test
    void aFolderWithoutDescriptorIsNotTreatedAsAProject() throws IOException {
        Files.createDirectories(tempDir.resolve("src"));
        var statusBar = new StatusBarViewModel();
        ProjectRepository empty = new ProjectRepository() {
            @Override
            public Project load(Path projectRoot) {
                throw new DomainException("project.missing", "No project description in " + projectRoot);
            }

            @Override
            public void save(Path projectRoot, Project project) {
            }
        };
        var viewModel = workbench(new EditorAreaViewModel(FakeEditorComponent::new), statusBar, empty);

        viewModel.openProject(tempDir);

        assertNull(viewModel.getCurrentProject());
        assertEquals("status.project.notAProject", statusBar.getStatus().key());
    }

    @Test
    void actionsAreIgnoredWhileNoProjectIsOpen() {
        var viewModel = workbench(new EditorAreaViewModel(FakeEditorComponent::new), new StatusBarViewModel(),
                repositoryReturning(Project.hello("None")));
        // No folder was opened at all, so there is nothing to build.

        assertNull(viewModel.build().join());
        assertNull(viewModel.clean().join());
        assertNull(viewModel.cleanAndBuild().join());
        assertFalse(viewModel.isBusy());
    }

    @Test
    void createProjectScaffoldsAndOpensProject() {
        Map<Path, Project> saved = new HashMap<>();
        ProjectRepository repository = new ProjectRepository() {
            @Override
            public Project load(Path projectRoot) {
                return saved.get(projectRoot);
            }

            @Override
            public void save(Path projectRoot, Project project) {
                saved.put(projectRoot, project);
            }
        };
        var statusBar = new StatusBarViewModel();
        var viewModel = workbench(new EditorAreaViewModel(FakeEditorComponent::new), statusBar, repository);

        Project created = viewModel.createProject(tempDir, "NewApp", "dos-exe-16", "8086", "borland-tasm", ">=3.2");

        assertNotNull(created);
        assertEquals("NewApp", created.info().name());
        assertEquals(created, viewModel.getCurrentProject());
        assertEquals("dos-exe-16 · 8086", statusBar.getTargetProfile());
        assertEquals("borland-tasm", statusBar.getToolchain());
        assertTrue(Files.exists(tempDir.resolve("NewApp").resolve("src").resolve("main.asm")));
    }

    @Test
    void languageIntelligenceProvidesHoverCompletionAndOutline() {
        var editorArea = new EditorAreaViewModel(FakeEditorComponent::new);
        var statusBar = new StatusBarViewModel();
        var viewModel = workbench(editorArea, statusBar, repositoryReturning(Project.hello("LangApp")));

        // 1. Instruction hover
        var movHover = viewModel.getHover("MOV", "en");
        assertTrue(movHover.isPresent());
        assertEquals(io.github.dinamo541.idearm.application.editor.HoverKind.INSTRUCTION, movHover.get().kind());
        assertNotNull(movHover.get().flagsTable());

        // 2. Numeric base conversion hover
        var numHover = viewModel.getHover("20h", "es");
        assertTrue(numHover.isPresent());
        assertEquals(io.github.dinamo541.idearm.application.editor.HoverKind.NUMBER_CONVERSION, numHover.get().kind());
        assertTrue(numHover.get().syntax().contains("0x20"));

        // 2b. QueryExplain colon notation fallback hover (AA-P4-02, Acceptance Case 2)
        var colonHover = viewModel.getHover("DS:DX", "es");
        assertTrue(colonHover.isPresent());
        assertTrue(colonHover.get().description().toLowerCase().contains("no representan concatenación")
                || colonHover.get().description().toLowerCase().contains("no concatenación"));

        // 3. Autocompletion
        var completions = viewModel.getCompletions("PU");
        assertFalse(completions.isEmpty());
        assertTrue(completions.stream().anyMatch(c -> c.label().equals("PUSH")));

        // 4. Outline extraction
        String code = """
                .code
                calc proc
                loop_start:
                    inc ax
                    ret
                calc endp
                """;
        var outline = viewModel.getOutline(code);
        assertFalse(outline.isEmpty());
        // Outline contains segment (.code) first, then procedure (calc)
        var procItem = outline.stream().filter(o -> o.name().equals("calc")).findFirst().orElseThrow();
        assertEquals("procedure", procItem.kind());
        assertEquals(1, procItem.children().size());
        assertEquals("loop_start", procItem.children().getFirst().name());
    }

    @Test
    void definitionAndReferencesQueryIndexedSymbols() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("src"));
        Path mainFile = src.resolve("main.asm");
        String code = """
                .code
                saludo proc
                    call saludo
                    ret
                saludo endp
                """;
        Files.writeString(mainFile, code);

        var editorArea = new EditorAreaViewModel(FakeEditorComponent::new);
        var statusBar = new StatusBarViewModel();
        var bottomPanel = new BottomPanelViewModel();
        var viewModel = new WorkbenchViewModel(new ProjectExplorerViewModel(), editorArea, bottomPanel,
                statusBar, services(repositoryReturning(Project.hello("SymApp"))));

        viewModel.indexSingleFile(mainFile, code);

        // Definition
        viewModel.goToDefinition("saludo");
        assertNotNull(editorArea.getActiveDocument());

        // References
        viewModel.findReferences("saludo");
        assertEquals(3, bottomPanel.getReferences().size());
        assertEquals(BottomPanelViewModel.BottomTab.REFERENCES, bottomPanel.getActiveTab());
    }

    @Test
    void toggleBreakpointPersistsAndUpdatesBottomPanel() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("src"));
        Path mainAsm = Files.writeString(src.resolve("MAIN.ASM"), "MOV AX, 4C00h\nINT 21h\nEND");
        Files.writeString(tempDir.resolve("idearm.toml"), "# test project");

        var editorArea = new EditorAreaViewModel(FakeEditorComponent::new);
        var statusBar = new StatusBarViewModel();
        var bottomPanel = new BottomPanelViewModel();
        var vm = new WorkbenchViewModel(new ProjectExplorerViewModel(), editorArea, bottomPanel,
                statusBar, services(repositoryReturning(Project.hello("TestProj"))));

        vm.openProject(tempDir);
        assertEquals(0, bottomPanel.getDebugViewModel().getBreakpoints().size());

        // Toggle on line 1
        vm.toggleBreakpoint(mainAsm, 1);
        assertEquals(1, bottomPanel.getDebugViewModel().getBreakpoints().size());
        assertEquals(1, bottomPanel.getDebugViewModel().getBreakpoints().getFirst().getLine());

        // Toggle line 1 again -> removed
        vm.toggleBreakpoint(mainAsm, 1);
        assertEquals(0, bottomPanel.getDebugViewModel().getBreakpoints().size());
    }

    private final BreakpointStore inMemoryBreakpointStore = new BreakpointStore() {
        private final Map<Path, List<Breakpoint>> storage = new HashMap<>();

        @Override
        public List<Breakpoint> loadBreakpoints(Path projectRoot) {
            return storage.getOrDefault(projectRoot.toAbsolutePath().normalize(), List.of());
        }

        @Override
        public void saveBreakpoints(Path projectRoot, List<Breakpoint> breakpoints) {
            storage.put(projectRoot.toAbsolutePath().normalize(), List.copyOf(breakpoints));
        }
    };

    /**
     * Stepping is only useful if the line the program is about to run is visible. Nothing covered the debug event
     * pipeline before, so a Paused event marking no line would have gone unnoticed.
     */
    @Test
    void aPausedProgramMarksItsLineAndClearsItAgain() throws Exception {
        var editorArea = new EditorAreaViewModel(FakeEditorComponent::new);
        var vm = workbench(editorArea, new StatusBarViewModel(), repositoryReturning(Project.hello("Paused")));
        try {
            Path source = tempDir.resolve("src").resolve("main.asm");
            Files.createDirectories(source.getParent());
            Files.writeString(source, "mov ax, 1\nmov bx, 2\nint 21h\n");
            vm.openProject(tempDir);

            vm.reportDebugEvent(new DebugEvent.Paused(source.toString(), 2, RegisterState.initialDosState()));

            var doc = editorArea.getDocuments().stream()
                    .filter(open -> open.getFilePath().equals(source))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("The paused file was not opened"));
            var editor = (FakeEditorComponent) doc.getEditor();
            assertEquals(2, editor.getExecutionLine());
            assertTrue(vm.getBottomPanel().getDebugViewModel().isPaused());

            // Continuing takes the mark away: the program is no longer standing on that line.
            vm.reportDebugEvent(new DebugEvent.Resumed());
            assertNull(editor.getExecutionLine());
            assertFalse(vm.getBottomPanel().getDebugViewModel().isPaused());

            vm.reportDebugEvent(new DebugEvent.Paused(source.toString(), 3, RegisterState.initialDosState()));
            assertEquals(3, editor.getExecutionLine());

            vm.reportDebugEvent(new DebugEvent.Stopped());
            assertNull(editor.getExecutionLine());
        } finally {
            vm.dispose();
        }
    }

    /** Pressing Debug again while one runs used to do nothing at all, which reads as a broken key (P2-09). */
    @Test
    void asecondTaskWhileOneRunsSaysTheWorkbenchIsBusy() throws Exception {
        var mayEnd = new java.util.concurrent.CountDownLatch(1);
        var started = new java.util.concurrent.CountDownLatch(1);
        ProjectRepository slowRepository = new ProjectRepository() {
            @Override public boolean exists(Path projectRoot) { return true; }

            @Override
            public Project load(Path projectRoot) {
                if (started.getCount() > 0) {
                    started.countDown();
                } else {
                    try {
                        mayEnd.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
                return Project.hello("Busy");
            }

            @Override public void save(Path projectRoot, Project project) { }
        };
        var statusBar = new StatusBarViewModel();
        var vm = workbench(new EditorAreaViewModel(FakeEditorComponent::new), statusBar, slowRepository);
        try {
            vm.openProject(tempDir);
            var first = vm.build();
            // Wait until the first task really holds the slot.
            while (!vm.isBusy()) {
                Thread.sleep(5);
            }

            assertNull(vm.debug().join(), "The second request is refused");
            assertEquals("status.task.busy", statusBar.getStatus().key());

            mayEnd.countDown();
            first.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            mayEnd.countDown();
            vm.dispose();
        }
    }

    /** Restart used to call debug() while the stopped session still held the task slot, so nothing restarted. */
    @Test
    void restartWaitsForTheStoppedSessionThenDebugsAgain() throws Exception {
        var loads = new java.util.concurrent.atomic.AtomicInteger();
        var firstDebugMayEnd = new java.util.concurrent.CountDownLatch(1);
        ProjectRepository slowRepository = new ProjectRepository() {
            @Override
            public boolean exists(Path projectRoot) {
                return true;
            }

            @Override
            public Project load(Path projectRoot) {
                // The first load opens the project; the second one is the first debug, held until released.
                if (loads.incrementAndGet() == 2) {
                    try {
                        firstDebugMayEnd.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
                return Project.hello("Restart");
            }

            @Override
            public void save(Path projectRoot, Project project) {
            }
        };
        var vm = workbench(new EditorAreaViewModel(FakeEditorComponent::new), new StatusBarViewModel(), slowRepository);
        try {
            vm.openProject(tempDir);
            var first = vm.debug();
            while (loads.get() < 2) {
                Thread.sleep(5);
            }

            vm.restartDebug();
            firstDebugMayEnd.countDown();
            first.get(5, java.util.concurrent.TimeUnit.SECONDS);

            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (loads.get() < 3 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(3, loads.get(), "Restart starts a new debug session once the old one has ended");
        } finally {
            vm.dispose();
        }
    }

    /**
     * The Include Folders editor in Project Properties writes {@code [sources] include}; saving idearm.toml is
     * also what makes the next Run rebuild, because the project file is a build input.
     */
    @Test
    void savingIncludeFoldersKeepsTheRestOfTheSourcesUntouched() throws IOException {
        Files.writeString(tempDir.resolve("idearm.toml"), "schema = 1");
        var saved = new java.util.ArrayList<Project>();
        Project opened = Project.hello("MANZANA");
        ProjectRepository repository = new ProjectRepository() {
            @Override public Project load(Path projectRoot) { return opened; }
            @Override public void save(Path projectRoot, Project project) { saved.add(project); }
        };
        var statusBar = new StatusBarViewModel();
        var vm = workbench(new EditorAreaViewModel(FakeEditorComponent::new), statusBar, repository);
        try {
            vm.openProject(tempDir);

            vm.updateIncludeDirs(List.of("sprite"));

            assertEquals(1, saved.size());
            assertEquals(List.of("sprite"), saved.getFirst().sources().include());
            assertEquals(opened.sources().entry(), saved.getFirst().sources().entry());
            assertEquals(opened.sources().modules(), saved.getFirst().sources().modules());
            assertEquals(List.of("sprite"), vm.getCurrentProject().sources().include());
            assertEquals("status.project.includeDirsUpdated", statusBar.getStatus().key());

            // Picking the same folders again is not a change, so idearm.toml is left alone.
            vm.updateIncludeDirs(List.of("sprite"));
            assertEquals(1, saved.size());
        } finally { vm.dispose(); }
    }

    /**
     * The main-file chooser in Project Properties writes {@code [sources] entry}. It is what decides which
     * program Run starts and what the executable is called, so it must reach idearm.toml, and the module list
     * must stay consistent with it.
     */
    @Test
    void savingTheMainFileKeepsThePatternThatAlreadyCoversIt() throws IOException {
        Files.writeString(tempDir.resolve("idearm.toml"), "schema = 1");
        var saved = new java.util.ArrayList<Project>();
        Project opened = withModules(Project.hello("MANZANA"), List.of("src/*.asm"));
        var statusBar = new StatusBarViewModel();
        var vm = workbench(new EditorAreaViewModel(FakeEditorComponent::new), statusBar, recording(opened, saved));
        try {
            vm.openProject(tempDir);

            vm.updateEntry("src/ejercicio2.asm", false);

            assertEquals(1, saved.size());
            assertEquals("src/ejercicio2.asm", saved.getFirst().sources().entry());
            assertEquals(List.of("src/*.asm"), saved.getFirst().sources().modules());
            assertEquals("status.project.entryUpdated", statusBar.getStatus().key());

            // Choosing the same file again changes nothing, so idearm.toml is left alone.
            vm.updateEntry("src/ejercicio2.asm", false);
            assertEquals(1, saved.size());
        } finally { vm.dispose(); }
    }

    @Test
    void buildingOnlyTheMainFileEmptiesTheModuleList() throws IOException {
        Files.writeString(tempDir.resolve("idearm.toml"), "schema = 1");
        var saved = new java.util.ArrayList<Project>();
        Project opened = withModules(Project.hello("MANZANA"), List.of("src/*.asm"));
        var vm = workbench(new EditorAreaViewModel(FakeEditorComponent::new), new StatusBarViewModel(),
                recording(opened, saved));
        try {
            vm.openProject(tempDir);

            vm.updateEntry("src/ejercicio2.asm", true);
            assertEquals(List.of(), saved.getFirst().sources().modules());

            vm.updateEntry("src/ejercicio2.asm", false);
            assertEquals(List.of("src/*.asm"), saved.get(1).sources().modules());
        } finally { vm.dispose(); }
    }

    /** The chooser must offer the project's own sources, not what a previous build left behind. */
    @Test
    void listingAssemblySourcesSkipsGeneratedFolders() throws IOException {
        Files.writeString(tempDir.resolve("idearm.toml"), "schema = 1");
        for (String relative : List.of("src/main.asm", "src/video.asm", "src/macros.inc",
                "build/debug/obj/main.asm", "dist/main.asm")) {
            Path file = tempDir.resolve(relative);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "; test");
        }
        var vm = workbench(new EditorAreaViewModel(FakeEditorComponent::new), new StatusBarViewModel(),
                repositoryReturning(Project.hello("MANZANA")));
        try {
            vm.openProject(tempDir);

            assertEquals(List.of("src/main.asm", "src/video.asm"), vm.listAssemblySources());
        } finally { vm.dispose(); }
    }

    private static Project withModules(Project project, List<String> modules) {
        var sources = project.sources();
        return new Project(project.schema(), project.info(), project.target(), project.toolchain(),
                new io.github.dinamo541.idearm.domain.model.Sources(sources.entry(), modules, sources.include(),
                        sources.exclude()),
                project.resources(), project.build(), project.run(), project.debug(), project.dist());
    }

    private static ProjectRepository recording(Project opened, List<Project> saved) {
        return new ProjectRepository() {
            @Override public Project load(Path projectRoot) { return opened; }
            @Override public void save(Path projectRoot, Project project) { saved.add(project); }
        };
    }

    private WorkbenchViewModel workbench(EditorAreaViewModel editorArea, StatusBarViewModel statusBar,
                                         ProjectRepository repository) {
        return new WorkbenchViewModel(new ProjectExplorerViewModel(), editorArea, new BottomPanelViewModel(),
                statusBar, services(repository));
    }

    private WorkbenchServices services(ProjectRepository repository) {
        return new WorkbenchServices(repository, emptyRegistry, new FileBuildWorkspace(), new NoToolRunner(),
                tempDir.resolve("staging"), List.of(), List.of(), List.of(), List.of(), inMemoryBreakpointStore, Duration.ofSeconds(5),
                new io.github.dinamo541.idearm.infrastructure.workspace.LocalProjectFiles());
    }

    private static ProjectRepository repositoryReturning(Project project) {
        return new ProjectRepository() {
            @Override
            public Project load(Path projectRoot) {
                return project;
            }

            @Override
            public void save(Path projectRoot, Project project2) {
            }
        };
    }

    /** No tool is installed in a unit test, so a build never reaches the runner. */
    private static final class NoToolRunner implements ToolRunner {
        @Override
        public ToolRunResult run(BuildPlan plan, Path projectRoot, ResolvedToolchain tools,
                                 CancellationToken cancellation, Duration timeout) {
            return new ToolRunResult(BuildStatus.FAILED, List.of(), null, "no toolchain in tests");
        }

        @Override
        public void release(ToolRunResult result) {
        }
    }
}
