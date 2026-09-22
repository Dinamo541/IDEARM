package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.app.i18n.Message;
import io.github.dinamo541.idearm.app.i18n.Problem;
import io.github.dinamo541.idearm.application.BuildEvent;
import io.github.dinamo541.idearm.application.BuildProject;
import io.github.dinamo541.idearm.application.BuildResult;
import io.github.dinamo541.idearm.application.CleanProject;
import io.github.dinamo541.idearm.application.CreateProject;
import io.github.dinamo541.idearm.application.DebugProject;
import io.github.dinamo541.idearm.application.DebugResult;
import io.github.dinamo541.idearm.application.ImportProject;
import io.github.dinamo541.idearm.application.LintProject;
import io.github.dinamo541.idearm.application.ManageBreakpoints;
import io.github.dinamo541.idearm.application.ManageProjectFiles;
import io.github.dinamo541.idearm.application.PackageProject;
import io.github.dinamo541.idearm.application.RunEvent;
import io.github.dinamo541.idearm.application.RunProject;
import io.github.dinamo541.idearm.application.RunResult;
import io.github.dinamo541.idearm.application.editor.CompletionItem;
import io.github.dinamo541.idearm.application.editor.HoverInfo;
import io.github.dinamo541.idearm.application.editor.OutlineItem;
import io.github.dinamo541.idearm.application.editor.QueryCompletion;
import io.github.dinamo541.idearm.application.editor.QueryDefinition;
import io.github.dinamo541.idearm.application.editor.QueryHover;
import io.github.dinamo541.idearm.application.editor.QueryOutline;
import io.github.dinamo541.idearm.application.editor.QueryReferences;
import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.DebugEvent;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.dist.DistResult;
import io.github.dinamo541.idearm.domain.execution.SessionState;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.RunConfiguration;
import io.github.dinamo541.idearm.domain.model.Sources;
import io.github.dinamo541.idearm.domain.model.TargetProfileCatalog;
import io.github.dinamo541.idearm.domain.port.ProjectRepository;
import io.github.dinamo541.idearm.domain.port.ToolRegistry;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import java.io.File;
import java.io.IOException;
import io.github.dinamo541.idearm.domain.files.TextDecoding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;

/**
 * Top-level ViewModel coordinating the entire IDE workbench.
 *
 * <p>It owns no adapter: every port arrives in {@link WorkbenchServices}, built by the composition root, so this
 * class can be tested headless and stays independent of infrastructure (ADR-007). Use cases run on virtual
 * threads and report progress through the sub view models, which marshal to the JavaFX thread themselves.
 *
 * <p>Status text is published as a {@link Message} rather than a finished sentence, so switching the language
 * also retranslates what is already on screen.
 */
public final class WorkbenchViewModel {

    private static final String DEFAULT_CONFIGURATION = "debug";

    private final ProjectExplorerViewModel explorer;
    private final EditorAreaViewModel editorArea;
    private final BottomPanelViewModel bottomPanel;
    private final StatusBarViewModel statusBar;

    private final ObjectProperty<Project> currentProject = new SimpleObjectProperty<>();
    private final ObjectProperty<Path> currentProjectPath = new SimpleObjectProperty<>();
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    private final RecentItemsViewModel recentItems;
    private final WorkbenchServices services;
    private final BuildProject buildProject;
    private final RunProject runProject;
    private final DebugProject debugProject;
    private final PackageProject packageProject;
    private final CleanProject cleanProject;
    private final ManageBreakpoints manageBreakpoints;

    private final ProjectSymbolIndex symbolIndex = new ProjectSymbolIndex();
    private final QueryDefinition queryDefinition = new QueryDefinition();
    private final QueryReferences queryReferences = new QueryReferences();
    private final QueryCompletion queryCompletion = new QueryCompletion();
    private final QueryHover queryHover = new QueryHover();
    private final QueryOutline queryOutline = new QueryOutline();
    private final LintProject lintProject = new LintProject();

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    /** Guards the single-task rule: the busy property alone is set on the JavaFX thread and can lag behind. */
    private final AtomicBoolean taskRunning = new AtomicBoolean(false);
    private volatile CancellableToken activeCancellation;
    private volatile Function<Problem, String> problemText = Problem::fallback;
    /** The task started last, so Restart can wait for it to end before starting again. */
    private volatile CompletableFuture<?> activeTask;

    public WorkbenchViewModel(ProjectExplorerViewModel explorer,
                              EditorAreaViewModel editorArea,
                              BottomPanelViewModel bottomPanel,
                              StatusBarViewModel statusBar,
                              WorkbenchServices services) {

        this.explorer = Objects.requireNonNull(explorer, "explorer cannot be null");
        this.editorArea = Objects.requireNonNull(editorArea, "editorArea cannot be null");
        this.bottomPanel = Objects.requireNonNull(bottomPanel, "bottomPanel cannot be null");
        this.statusBar = Objects.requireNonNull(statusBar, "statusBar cannot be null");
        this.services = Objects.requireNonNull(services, "services cannot be null");

        this.recentItems = new RecentItemsViewModel(services.recentItemsStore());
        this.editorArea.setOnFileOpened(path -> recentItems.remember(
                io.github.dinamo541.idearm.domain.model.RecentItem.Kind.FILE, path));

        this.buildProject = new BuildProject(services.projectRepository(), services.toolRegistry(),
                services.toolchainProviders(), services.workspace(), services.toolRunner(),
                services.buildTimeout());
        this.runProject = new RunProject(services.projectRepository(), services.toolRegistry(),
                services.executionProviders(), services.toolchainProviders(), services.workspace(),
                buildProject, services.stagingRoot());
        this.packageProject = new PackageProject(services.projectRepository(), services.workspace(),
                services.distPackagers(), services.toolchainProviders(), buildProject);
        this.cleanProject = new CleanProject(services.workspace());
        this.manageBreakpoints = new ManageBreakpoints(services.breakpointStore());
        this.debugProject = new DebugProject(services.projectRepository(), services.toolRegistry(),
                services.debugProviders(), services.toolchainProviders(), services.workspace(),
                buildProject, services.breakpointStore(), services.stagingRoot());

        this.editorArea.getDocuments().addListener((ListChangeListener<EditorDocumentViewModel>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (EditorDocumentViewModel doc : change.getAddedSubList()) {
                        initDocumentBreakpoints(doc);
                        followCaret(doc);
                        followFormat(doc);
                    }
                }
            }
        });

        this.editorArea.activeDocumentProperty().addListener((observable, previous, active) -> {
            if (active != null) {
                statusBar.setCaretPosition(active.caretLineProperty().get(), active.caretColumnProperty().get());
            } else {
                statusBar.setCaretPosition(1, 1);
            }
            showEncoding(active);
        });

        if (bottomPanel.getTerminalViewModel() == null && services.terminalRunner() != null) {
            bottomPanel.initTerminal(services.terminalRunner());
        }

        this.explorer.connect(new ManageProjectFiles(services.projectFiles()), this::isDosTarget);
        this.explorer.addListener(new ExplorerChanges());
    }

    public void openTerminal() {
        bottomPanel.setActiveTab(BottomPanelViewModel.BottomTab.TERMINAL);
        var tvm = bottomPanel.getTerminalViewModel();
        if (tvm != null && !tvm.isRunning()) {
            tvm.startSession(currentProjectPath.get());
        }
    }

    public ProjectExplorerViewModel getExplorer() {
        return explorer;
    }

    public RecentItemsViewModel getRecentItems() { return recentItems; }

    /** Reject stale entries before any project or editor state is changed. */
    public boolean openRecent(io.github.dinamo541.idearm.domain.model.RecentItem item) throws IOException {
        if (item.kind() == io.github.dinamo541.idearm.domain.model.RecentItem.Kind.PROJECT) {
            if (!Files.isDirectory(item.path()) || !Files.isReadable(item.path())) return false;
            openProject(item.path());
        } else {
            if (!Files.isRegularFile(item.path()) || !Files.isReadable(item.path())) return false;
            editorArea.openFile(item.path()).getEditor().requestFocus();
        }
        return true;
    }

    public EditorAreaViewModel getEditorArea() {
        return editorArea;
    }

    public BottomPanelViewModel getBottomPanel() {
        return bottomPanel;
    }

    public StatusBarViewModel getStatusBar() {
        return statusBar;
    }

    public ObjectProperty<Project> currentProjectProperty() {
        return currentProject;
    }

    public Project getCurrentProject() {
        return currentProject.get();
    }

    public Path getCurrentProjectPath() {
        return currentProjectPath.get();
    }

    public BooleanProperty busyProperty() {
        return busy;
    }

    public boolean isBusy() {
        return busy.get();
    }

    public ProjectRepository getProjectRepository() {
        return services.projectRepository();
    }

    public ToolRegistry getToolRegistry() {
        return services.toolRegistry();
    }

    /**
     * Changes which DOSBox dialect the project runs, builds and debugs in (or {@code "dosbox"} for auto), saving
     * {@code idearm.toml} and refreshing the open project so the next Run, Build or Debug honours the choice.
     */
    public void updateRunEnvironment(String environment) {
        Project project = currentProject.get();
        Path root = currentProjectPath.get();
        if (project == null || root == null || environment.equals(project.run().environment())) {
            return;
        }
        RunConfiguration run = project.run();
        RunConfiguration updatedRun = new RunConfiguration(environment, run.isolation(), run.keepOpen(),
                run.cycles(), run.memsize(), run.args());
        Project updated = new Project(project.schema(), project.info(), project.target(), project.toolchain(),
                project.sources(), project.resources(), project.build(), updatedRun, project.debug(), project.dist());
        try {
            services.projectRepository().save(root, updated);
            currentProject.set(updated);
            statusBar.setStatus(Message.of("status.project.runEnvironmentUpdated", environment));
        } catch (RuntimeException failure) {
            statusBar.setStatus(Message.of("status.task.failed", Problem.of(failure)));
        }
    }

    /**
     * Opens a project folder.
     *
     * <p>The folder is always shown in the explorer, but a project is only considered open when its description
     * could be read: inventing a default configuration for an unreadable one would build something the user never
     * described. When it is missing or invalid the reason is reported and the project actions stay disabled until
     * it is fixed or the folder is imported.
     */
    public void openProject(Path projectDirectory) {
        Path directory = projectDirectory.toAbsolutePath().normalize();
        currentProjectPath.set(directory);
        currentProject.set(null);
        explorer.openProject(directory);
        if (Files.isDirectory(directory) && Files.isReadable(directory)) {
            recentItems.remember(io.github.dinamo541.idearm.domain.model.RecentItem.Kind.PROJECT, directory);
        }
        // Symbols of the previous project must not answer "go to definition" in this one.
        symbolIndex.clear();
        indexProjectFiles(directory);

        bottomPanel.getDebugViewModel().setBreakpoints(manageBreakpoints.getBreakpoints(directory));
        for (EditorDocumentViewModel doc : editorArea.getDocuments()) {
            initDocumentBreakpoints(doc);
        }

        ProjectRepository repository = services.projectRepository();
        if (!repository.exists(directory)) {
            currentProject.set(null);
            openMainSource(directory, null);
            statusBar.setStatus(Message.of("status.project.notAProject", String.valueOf(directory.getFileName())));
            return;
        }
        try {
            Project project = repository.load(directory);
            currentProject.set(project);
            openMainSource(directory, project);
            statusBar.setTargetProfile(project.target().profile() + " · " + project.target().cpu());
            statusBar.setToolchain(project.toolchain().id());
            statusBar.setStatus(Message.of("status.project.opened", project.info().name()));
        } catch (RuntimeException failure) {
            currentProject.set(null);
            openMainSource(directory, null);
            String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            statusBar.setStatus(Message.of("status.project.invalid", Problem.of(failure)));
            bottomPanel.appendBuildLine("[PROJECT] " + directory + ": " + reason);
            bottomPanel.setActiveTab(BottomPanelViewModel.BottomTab.BUILD);
        }
    }

    /** Builds the current project. */
    public CompletableFuture<BuildResult> build() {
        return startTask(Message.of("status.build.running", projectName()), true, token -> {
            bottomPanel.clearBuild();
            bottomPanel.setActiveTab(BottomPanelViewModel.BottomTab.BUILD);
            return runBuild(token);
        });
    }

    /** Runs the current project in isolated staging. */
    public CompletableFuture<RunResult> run(boolean keepOpen) {
        return startTask(Message.of("status.run.running", projectName()), true, token -> {
            bottomPanel.clearOutput();
            bottomPanel.setActiveTab(BottomPanelViewModel.BottomTab.OUTPUT);
            return runProgram(token, keepOpen);
        });
    }

    /** Starts an interactive or real-mode debug session for the current project. */
    public CompletableFuture<DebugResult> debug() {
        return startTask(Message.of("status.debug.running", projectName()), true, token -> {
            bottomPanel.clearOutput();
            bottomPanel.setActiveTab(BottomPanelViewModel.BottomTab.DEBUG);
            bottomPanel.getDebugViewModel().reset();
            bottomPanel.getDebugViewModel().setActive(true);
            return runDebug(token);
        });
    }

    /** Cleans build and dist directories. */
    public CompletableFuture<Void> clean() {
        return startTask(Message.of("status.clean.running", projectName()), false, token -> {
            runClean();
            return null;
        });
    }

    /**
     * Cleans and then builds inside a single task, so the build cannot be dropped while the busy flag is still
     * settling from the clean.
     */
    public CompletableFuture<BuildResult> cleanAndBuild() {
        return startTask(Message.of("status.clean.running", projectName()), true, token -> {
            runClean();
            if (token.cancelled()) {
                statusBar.setStatus(Message.of("status.task.cancelled"));
                return null;
            }
            bottomPanel.clearBuild();
            bottomPanel.setActiveTab(BottomPanelViewModel.BottomTab.BUILD);
            statusBar.setStatus(Message.of("status.build.running", projectName()));
            return runBuild(token);
        });
    }

    /** Packages the project into its dist/ folder. */
    public CompletableFuture<DistResult> packageDist() {
        return startTask(Message.of("status.package.running", projectName()), true, token -> {
            bottomPanel.clearBuild();
            bottomPanel.setActiveTab(BottomPanelViewModel.BottomTab.BUILD);
            return runPackage(token);
        });
    }

    /** Creates a new project using the CreateProject use case and opens it. */
    public Project createProject(Path parentDirectory, String name, String targetProfile, String cpu,
                                 String toolchainId, String toolchainVersion) {
        Project project = new CreateProject(services.projectRepository())
                .execute(parentDirectory, name, targetProfile, cpu, toolchainId, toolchainVersion);
        openProject(parentDirectory.resolve(name));
        return project;
    }

    /** Imports an existing assembly project folder without idearm.toml and opens it. */
    public Project importProject(Path directory) {
        Project project = new ImportProject(services.projectRepository()).execute(directory);
        openProject(directory);
        return project;
    }

    /** Stops the running task. */
    public void stop() {
        CancellableToken token = activeCancellation;
        if (token != null) {
            token.cancel();
            statusBar.setStatus(Message.of("status.task.stopping"));
        }
    }

    private BuildResult runBuild(CancellableToken token) {
        BuildResult result = buildProject.execute(currentProjectPath.get(), DEFAULT_CONFIGURATION, token,
                this::reportBuildEvent);
        // Build problems come first; the educational warnings follow them.
        var problems = new ArrayList<Diagnostic>(result.diagnostics());
        if (result.status() != BuildStatus.CANCELLED) {
            problems.addAll(lintWarnings());
        }
        bottomPanel.setDiagnostics(problems, currentProjectPath.get());
        if (result.succeeded()) {
            statusBar.setStatus(Message.of("status.build.succeeded"));
        } else {
            long errors = result.diagnostics().stream()
                    .filter(d -> d.severity() == Severity.ERROR || d.severity() == Severity.FATAL)
                    .count();
            statusBar.setStatus(Message.of("status.build.failed", errors));
            if (!result.diagnostics().isEmpty()) {
                bottomPanel.setActiveTab(BottomPanelViewModel.BottomTab.PROBLEMS);
            }
        }
        explorer.refresh();
        return result;
    }

    private List<Diagnostic> lintWarnings() {
        Path root = currentProjectPath.get();
        Project project = currentProject.get();
        if (root == null || project == null) {
            return List.of();
        }
        try {
            return lintProject.execute(root, project, symbolIndex);
        } catch (RuntimeException unavailable) {
            return List.of();
        }
    }

    private RunResult runProgram(CancellableToken token, boolean keepOpen) {
        RunResult result = runProject.execute(currentProjectPath.get(), DEFAULT_CONFIGURATION, keepOpen, token,
                this::reportRunEvent);
        bottomPanel.setProgramInput(BottomPanelViewModel.ProgramInput.NONE, null);
        int exitCode = result.exitInfo() != null ? result.exitInfo().exitCode() : 0;
        if (result.state() == SessionState.EXITED) {
            statusBar.setStatus(Message.of("status.run.finished", exitCodeText(exitCode)));
            // A run that worked may still carry advice, such as a long resource name DOSBox 0.74-3 cannot open.
            if (!result.diagnostics().isEmpty()) {
                bottomPanel.setDiagnostics(result.diagnostics(), currentProjectPath.get());
            }
        } else if (result.state() == SessionState.STOPPED) {
            statusBar.setStatus(Message.of("status.run.stopped"));
        } else {
            statusBar.setStatus(Message.of("status.run.failed", describe(result.diagnostics(), result.exitInfo())));
            bottomPanel.setDiagnostics(result.diagnostics(), currentProjectPath.get());
            if (!result.diagnostics().isEmpty()) {
                bottomPanel.setActiveTab(BottomPanelViewModel.BottomTab.PROBLEMS);
            }
        }
        explorer.refresh();
        return result;
    }

    private DebugResult runDebug(CancellableToken token) {
        Path projectRoot = currentProjectPath.get();
        if (projectRoot != null) {
            bottomPanel.getDebugViewModel().setBreakpoints(manageBreakpoints.getBreakpoints(projectRoot));
        }

        DebugResult result = debugProject.execute(projectRoot, token, this::reportDebugEvent);
        int exitCode = result.exitInfo() != null ? result.exitInfo().exitCode() : 0;
        bottomPanel.getDebugViewModel().setActive(false);
        bottomPanel.getDebugViewModel().setPaused(false);
        clearExecutionLineInEditors();

        if (result.state() == SessionState.EXITED) {
            statusBar.setStatus(Message.of("status.debug.finished", exitCodeText(exitCode)));
        } else if (result.state() == SessionState.STOPPED) {
            statusBar.setStatus(Message.of("status.debug.stopped"));
        } else {
            statusBar.setStatus(Message.of("status.debug.failed", describe(result.diagnostics(), result.exitInfo())));
            bottomPanel.setDiagnostics(result.diagnostics(), currentProjectPath.get());
            if (!result.diagnostics().isEmpty()) {
                bottomPanel.setActiveTab(BottomPanelViewModel.BottomTab.PROBLEMS);
            }
        }
        explorer.refresh();
        return result;
    }

    private void reportDebugEvent(DebugEvent event) {
        switch (event) {
            case DebugEvent.Started started -> {
                bottomPanel.appendOutputLine("[DEBUG] " + started.debugger());
                bottomPanel.getDebugViewModel().setActive(true);
            }
            case DebugEvent.SessionAttached attached -> {
                bottomPanel.getDebugViewModel().attachSession(attached.session());
            }
            case DebugEvent.Paused paused -> {
                bottomPanel.appendOutputLine("[DEBUG] Paused at " + paused.file() + ":" + paused.line());
                bottomPanel.getDebugViewModel().setPaused(true);
                bottomPanel.getDebugViewModel().updateRegisters(paused.registers());
                bottomPanel.getDebugViewModel().refreshMemoryDump();
                bottomPanel.getDebugViewModel().refreshInstructionsExecuted();
                highlightExecutionLine(paused.file(), paused.line());
            }
            case DebugEvent.Resumed resumed -> {
                bottomPanel.getDebugViewModel().setPaused(false);
                clearExecutionLineInEditors();
            }
            // The program's own text, exactly as it wrote it (one INT 21h/02h call may print a single character).
            case DebugEvent.Output output -> bottomPanel.appendOutputText(output.text());
            case DebugEvent.WaitingForInput waiting -> {
                // The student types into the Output panel; each key goes to the program as a real keyboard would.
                bottomPanel.setProgramInput(BottomPanelViewModel.ProgramInput.KEYS,
                        bottomPanel.getDebugViewModel()::sendProgramInput);
                bottomPanel.setActiveTab(BottomPanelViewModel.BottomTab.OUTPUT);
                statusBar.setStatus(Message.of("status.debug.waitingInput"));
            }
            case DebugEvent.Problem problem -> reportDebugProblem(problem.diagnostic());
            case DebugEvent.Stopped stopped -> {
                bottomPanel.setProgramInput(BottomPanelViewModel.ProgramInput.NONE, null);
                bottomPanel.getDebugViewModel().detachSession();
                bottomPanel.getDebugViewModel().reset();
                clearExecutionLineInEditors();
            }
            case DebugEvent.Exited exited -> {
                bottomPanel.setProgramInput(BottomPanelViewModel.ProgramInput.NONE, null);
                bottomPanel.appendOutputLine("[DEBUG] exit code "
                        + (exited.exitInfo() != null ? exited.exitInfo().exitCode() : 0));
                bottomPanel.getDebugViewModel().detachSession();
                bottomPanel.getDebugViewModel().reset();
                clearExecutionLineInEditors();
            }
            default -> {}
        }
    }

    /**
     * Something the debugged program did wrong, such as dividing by zero: it goes to the log (Output), to Problems at
     * its line, and, when the program had to stop, to the status bar. The session is already paused on that line.
     */
    private void reportDebugProblem(Diagnostic diagnostic) {
        String where = diagnostic.location() == null ? ""
                : " (" + diagnostic.location().path() + (diagnostic.location().line() == null ? ""
                        : ":" + diagnostic.location().line()) + ")";
        bottomPanel.appendOutputLine("[DEBUG] " + problemText.apply(Problem.of(diagnostic)) + where);
        bottomPanel.addDiagnostic(diagnostic, currentProjectPath.get());
        if (diagnostic.severity() == Severity.ERROR || diagnostic.severity() == Severity.FATAL) {
            statusBar.setStatus(Message.of("status.debug.fault", Problem.of(diagnostic)));
        }
    }

    /**
     * How a problem reads in the user's language; the view supplies it. The log keeps the text it was written with,
     * while Problems and the status bar follow later language changes.
     */
    public void setProblemFormatter(Function<Problem, String> formatter) {
        this.problemText = Objects.requireNonNull(formatter, "formatter");
    }

    public void stepInto() {
        bottomPanel.getDebugViewModel().stepInto();
    }

    public void stepOver() {
        bottomPanel.getDebugViewModel().stepOver();
    }

    public void stepOut() {
        bottomPanel.getDebugViewModel().stepOut();
    }

    public void resumeDebug() {
        bottomPanel.getDebugViewModel().resume();
    }

    /**
     * Stops the debug session and starts a new one. Stop only asks the running task to end; starting right away was
     * refused because that task still held the workbench's single task slot, so Restart did nothing.
     */
    public void restartDebug() {
        CompletableFuture<?> running = activeTask;
        stop();
        if (running == null || running.isDone()) {
            debug();
        } else {
            running.whenComplete((result, failure) -> FxDispatch.run(this::debug));
        }
    }

    private void highlightExecutionLine(String fileStr, int line) {
        Path target = resolvePath(fileStr);
        if (target != null && Files.exists(target)) {
            FxDispatch.run(() -> {
                try {
                    EditorDocumentViewModel doc = editorArea.openFile(target);
                    doc.getEditor().setExecutionLine(line);
                } catch (IOException ignored) {}
            });
        }
    }

    private void clearExecutionLineInEditors() {
        FxDispatch.run(() -> {
            for (var doc : editorArea.getDocuments()) {
                doc.getEditor().setExecutionLine(null);
            }
        });
    }

    private void runClean() {
        cleanProject.execute(currentProjectPath.get());
        bottomPanel.clearBuild();
        bottomPanel.clearDiagnostics();
        statusBar.setStatus(Message.of("status.clean.done"));
        explorer.refresh();
    }

    private DistResult runPackage(CancellableToken token) {
        DistResult result = packageProject.execute(currentProjectPath.get(), token);
        bottomPanel.appendBuildLine("[DIST] " + result.distDirectory());
        statusBar.setStatus(Message.of("status.package.done"));
        explorer.refresh();
        return result;
    }

    private void reportBuildEvent(BuildEvent event) {
        switch (event) {
            case BuildEvent.Started started ->
                    bottomPanel.appendBuildLine("[BUILD] " + started.configuration());
            case BuildEvent.Output output -> bottomPanel.appendBuildLine("    " + output.text());
            case BuildEvent.DiagnosticsPublished published ->
                    bottomPanel.setDiagnostics(published.diagnostics(), currentProjectPath.get());
            case BuildEvent.Finished finished ->
                    bottomPanel.appendBuildLine("[BUILD] " + (finished.result().succeeded() ? "SUCCESS" : "FAILED"));
            default -> { }
        }
    }

    private void reportRunEvent(RunEvent event) {
        switch (event) {
            case RunEvent.Started started -> bottomPanel.appendOutputLine("[RUN] " + started.environment());
            case RunEvent.Output output -> bottomPanel.appendOutputLine(output.text());
            // A program without a console window of its own (Linux) reads whole lines typed in the Output panel.
            case RunEvent.SessionAttached attached -> {
                bottomPanel.setProgramInput(BottomPanelViewModel.ProgramInput.LINE, attached.session()::sendInput);
                bottomPanel.setActiveTab(BottomPanelViewModel.BottomTab.OUTPUT);
            }
            case RunEvent.ProgramOutput programOutput -> bottomPanel.appendOutputText(programOutput.text());
            case RunEvent.Exited exited -> {
                bottomPanel.setProgramInput(BottomPanelViewModel.ProgramInput.NONE, null);
                bottomPanel.appendOutputLine("[RUN] exit code "
                        + (exited.exitInfo() != null ? exited.exitInfo().exitCode() : 0)
                        + (exited.exitInfo() != null ? " (" + exited.exitInfo().duration().toMillis() + " ms)" : ""));
            }
        }
    }

    /**
     * Starts the one task the workbench allows at a time.
     *
     * @param cancellable whether Stop applies; a clean is too short to interrupt safely.
     */
    private <T> CompletableFuture<T> startTask(Message status, boolean cancellable,
                                               Function<CancellableToken, T> work) {
        Path projectPath = currentProjectPath.get();
        if (projectPath == null || currentProject.get() == null || !taskRunning.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        saveAllQuietly();
        FxDispatch.run(() -> busy.set(true));
        statusBar.setStatus(status);

        CancellableToken token = new CancellableToken();
        if (cancellable) {
            activeCancellation = token;
        }

        CompletableFuture<T> task = CompletableFuture.supplyAsync(() -> {
            try {
                return work.apply(token);
            } catch (RuntimeException failure) {
                reportFailure(failure);
                return null;
            } finally {
                activeCancellation = null;
                taskRunning.set(false);
                FxDispatch.run(() -> busy.set(false));
            }
        }, executor);
        activeTask = task;
        return task;
    }

    private void reportFailure(RuntimeException failure) {
        String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        bottomPanel.appendBuildLine("[ERROR] " + reason);
        statusBar.setStatus(Message.of("status.task.failed", Problem.of(failure)));
    }

    /**
     * An exit code as text, so it is not shown with thousands separators; a negative code is a Windows status such
     * as 0xC0000005 (access violation), which is how it is documented and searched for.
     */
    static String exitCodeText(int exitCode) {
        return exitCode >= 0 ? String.valueOf(exitCode) : exitCode + String.format(" (0x%08X)", exitCode);
    }

    /** Why a run or debug session failed: its first problem, translated when shown, or the exit message. */
    private static Object describe(List<Diagnostic> diagnostics, io.github.dinamo541.idearm.domain.execution.ExitInfo exit) {
        if (!diagnostics.isEmpty()) {
            return Problem.of(diagnostics.getFirst());
        }
        return exit != null ? exit.message() : "";
    }

    private String projectName() {
        Project project = currentProject.get();
        if (project != null) {
            return project.info().name();
        }
        Path path = currentProjectPath.get();
        return path == null ? "" : String.valueOf(path.getFileName());
    }

    /**
     * Opens the entry source of a freshly opened folder: the one {@code idearm.toml} names, or the conventional
     * {@code src/main.asm} when there is no readable description.
     */
    private void openMainSource(Path directory, Project project) {
        Path source = project != null
                ? directory.resolve(project.sources().entry())
                : directory.resolve("src").resolve("main.asm");
        if (Files.isRegularFile(source)) {
            try {
                editorArea.openFile(source);
            } catch (IOException unreadable) {
                bottomPanel.appendBuildLine("[PROJECT] " + source + ": " + unreadable.getMessage());
            }
        }
    }

    private void saveAllQuietly() {
        try {
            editorArea.saveAll();
            for (var doc : editorArea.getDocuments()) {
                indexSingleFile(doc.getFilePath(), doc.getEditor().getText());
            }
        } catch (IOException failure) {
            bottomPanel.appendBuildLine("[EDITOR] " + failure.getMessage());
        }
    }

    public ProjectSymbolIndex getSymbolIndex() {
        return symbolIndex;
    }

    public void indexProjectFiles(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        executor.submit(() -> {
            try (var stream = Files.walk(directory)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> !isGenerated(directory, p))
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                            return name.endsWith(".asm") || name.endsWith(".inc");
                        })
                        .forEach(p -> {
                            try {
                                String content = TextDecoding.decode(Files.readAllBytes(p)).text();
                                String rel = directory.relativize(p).toString().replace('\\', '/');
                                symbolIndex.updateFile(rel, content);
                            } catch (IOException ignored) {}
                        });
            } catch (IOException ignored) {}
        });
    }

    public void indexSingleFile(Path file, String content) {
        if (file == null) return;
        Path root = currentProjectPath.get();
        String rel = root != null && file.startsWith(root)
                ? root.relativize(file).toString().replace('\\', '/')
                : file.toString().replace('\\', '/');
        symbolIndex.updateFile(rel, content);
    }

    public void goToDefinition(String symbolName) {
        if (symbolName == null || symbolName.isBlank()) return;
        queryDefinition.execute(symbolName, symbolIndex).ifPresent(loc -> {
            Path target = resolvePath(loc.path());
            if (target != null && Files.exists(target)) {
                try {
                    EditorDocumentViewModel doc = editorArea.openFile(target);
                    if (loc.line() > 0) {
                        doc.getEditor().goToLine(loc.line());
                    }
                } catch (IOException ignored) {}
            }
        });
    }

    public void findReferences(String symbolName) {
        if (symbolName == null || symbolName.isBlank()) return;
        List<Location> locations = queryReferences.execute(symbolName, symbolIndex);
        List<ReferenceItemViewModel> items = new ArrayList<>();
        for (Location loc : locations) {
            Path target = resolvePath(loc.path());
            String preview = extractLineSnippet(target, loc.line());
            items.add(new ReferenceItemViewModel(loc.path(), loc.line(), loc.column(), preview));
        }
        bottomPanel.setReferences(items);
        bottomPanel.setActiveTab(BottomPanelViewModel.BottomTab.REFERENCES);
    }

    public Optional<HoverInfo> getHover(String word, String locale) {
        return queryHover.execute(word, locale, symbolIndex, (filePath, line, size) -> {
            if (bottomPanel.getDebugViewModel().isPaused()) {
                return bottomPanel.getDebugViewModel().evaluateVariable(filePath, line, size);
            }
            return Optional.empty();
        });
    }

    public List<CompletionItem> getCompletions(String prefix) {
        String cpu = currentProject.get() != null ? currentProject.get().target().cpu() : "8086";
        return queryCompletion.execute(prefix, cpu, symbolIndex);
    }

    public List<OutlineItem> getOutline(String sourceText) {
        return queryOutline.execute(sourceText);
    }

    public Path resolvePath(String fileStr) {
        if (fileStr == null || fileStr.isBlank()) return null;
        String normalizedStr = fileStr.replace('/', File.separatorChar).replace('\\', File.separatorChar);
        Path p = Path.of(normalizedStr);
        if (p.isAbsolute() && Files.exists(p)) return p;
        Path root = currentProjectPath.get();
        if (root != null) {
            Path resolved = root.resolve(normalizedStr).normalize();
            if (Files.exists(resolved)) return resolved;
            Path inSrc = root.resolve("src").resolve(normalizedStr).normalize();
            if (Files.exists(inSrc)) return inSrc;
            if (!normalizedStr.contains(File.separator)) {
                Project prj = currentProject.get();
                if (prj != null && prj.sources().entry() != null) {
                    Path entryPath = root.resolve(prj.sources().entry()).normalize();
                    if (Files.exists(entryPath) && entryPath.getFileName().toString().equalsIgnoreCase(normalizedStr)) {
                        return entryPath;
                    }
                }
            }
        }
        return p;
    }

    private String extractLineSnippet(Path path, int line) {
        if (path != null && Files.exists(path)) {
            try {
                String text = TextDecoding.decode(Files.readAllBytes(path)).text();
                return text.lines().skip(Math.max(0, line - 1)).findFirst().orElse("").trim();
            } catch (Exception ignored) {}
        }
        return "";
    }

    public void toggleBreakpoint(Path file, int line) {
        Path projectRoot = currentProjectPath.get();
        if (projectRoot == null || file == null) return;
        Path normFile = file.toAbsolutePath().normalize();
        String rel = projectRoot.relativize(normFile).toString().replace('\\', '/');
        manageBreakpoints.toggleBreakpoint(projectRoot, rel, line);
        bottomPanel.getDebugViewModel().setBreakpoints(manageBreakpoints.getBreakpoints(projectRoot));
        syncEditorBreakpoints(normFile);
    }

    public void toggleBreakpointAtCaret() {
        EditorDocumentViewModel active = editorArea.getActiveDocument();
        if (active != null) {
            int line = active.caretLineProperty().get();
            active.getEditor().toggleBreakpoint(line);
        }
    }

    public void clearAllBreakpoints() {
        Path projectRoot = currentProjectPath.get();
        if (projectRoot != null) {
            manageBreakpoints.clearBreakpoints(projectRoot);
            bottomPanel.getDebugViewModel().setBreakpoints(List.of());
            for (var doc : editorArea.getDocuments()) {
                doc.getEditor().setBreakpoints(Set.of());
            }
        }
    }

    private void initDocumentBreakpoints(EditorDocumentViewModel doc) {
        Path projectRoot = currentProjectPath.get();
        if (projectRoot != null && doc.getFilePath().startsWith(projectRoot)) {
            String rel = projectRoot.relativize(doc.getFilePath()).toString().replace('\\', '/');
            var bps = manageBreakpoints.getBreakpointsForFile(projectRoot, rel);
            Set<Integer> lines = bps.stream().map(Breakpoint::line).collect(Collectors.toSet());
            doc.getEditor().setBreakpoints(lines);
        }
        doc.getEditor().setOnBreakpointToggled(line -> {
            toggleBreakpointFromEditor(doc.getFilePath(), line);
        });
    }

    private void toggleBreakpointFromEditor(Path file, int line) {
        Path projectRoot = currentProjectPath.get();
        if (projectRoot == null || file == null) return;
        Path normFile = file.toAbsolutePath().normalize();
        String rel = projectRoot.relativize(normFile).toString().replace('\\', '/');
        manageBreakpoints.toggleBreakpoint(projectRoot, rel, line);
        bottomPanel.getDebugViewModel().setBreakpoints(manageBreakpoints.getBreakpoints(projectRoot));
    }

    private void syncEditorBreakpoints(Path file) {
        Path projectRoot = currentProjectPath.get();
        if (projectRoot == null || file == null) return;
        String rel = projectRoot.relativize(file).toString().replace('\\', '/');
        var bps = manageBreakpoints.getBreakpointsForFile(projectRoot, rel);
        Set<Integer> lines = bps.stream().map(Breakpoint::line).collect(Collectors.toSet());
        for (var doc : editorArea.getDocuments()) {
            if (doc.getFilePath().equals(file)) {
                doc.getEditor().setBreakpoints(lines);
            }
        }
    }

    public ManageBreakpoints getManageBreakpoints() {
        return manageBreakpoints;
    }

    public DebugProject getDebugProject() {
        return debugProject;
    }

    /** Stops background work; the application calls it when the window closes. */
    public void dispose() {
        recentItems.close();
        stop();
        explorer.dispose();
        executor.shutdownNow();
    }

    /** Whether the open project builds for DOS, whose tools only see 8.3 names. */
    public boolean isDosTarget() {
        Project project = currentProject.get();
        if (project == null) {
            return true;
        }
        try {
            return "DOS".equalsIgnoreCase(TargetProfileCatalog.require(project.target().profile()).platform());
        } catch (RuntimeException unknownProfile) {
            return true;
        }
    }

    /** The status bar shows how the active file is stored, as editors do ("windows-1252 · CRLF"). */
    private void showEncoding(EditorDocumentViewModel doc) {
        statusBar.setEncoding(doc == null ? "" : describe(doc.getFormat()));
    }

    static String describe(TextDecoding.TextFormat format) {
        String name = format.charset().name();
        return (format.bom() ? name + " BOM" : name) + " · " + format.lineSeparatorName();
    }

    /** A save that had to switch a file to UTF-8 says so, because other tools may now read it differently. */
    private void followFormat(EditorDocumentViewModel doc) {
        doc.formatProperty().addListener((o, before, after) -> {
            if (editorArea.getActiveDocument() == doc) {
                showEncoding(doc);
            }
            if (before != null && after != null && !before.charset().equals(after.charset())) {
                statusBar.setStatus(Message.of("status.editor.savedAsUtf8",
                        String.valueOf(doc.getFilePath().getFileName())));
            }
        });
    }

    private void followCaret(EditorDocumentViewModel doc) {
        doc.caretLineProperty().addListener((o, oldLine, line) -> {
            if (editorArea.getActiveDocument() == doc) {
                statusBar.setCaretPosition(line.intValue(), doc.caretColumnProperty().get());
            }
        });
        doc.caretColumnProperty().addListener((o, oldColumn, column) -> {
            if (editorArea.getActiveDocument() == doc) {
                statusBar.setCaretPosition(doc.caretLineProperty().get(), column.intValue());
            }
        });
    }

    /** Build output, tool state and version-control folders hold nothing to index. */
    private static boolean isGenerated(Path root, Path file) {
        Path relative = root.relativize(file);
        if (relative.getNameCount() < 2) {
            return false;
        }
        String top = relative.getName(0).toString();
        return top.equals("build") || top.equals("dist") || top.equals(".git") || top.equals(".idearm");
    }

    private String relativeToProject(Path path) {
        Path root = currentProjectPath.get();
        return root == null ? path.toString() : root.relativize(path).toString().replace('\\', '/');
    }

    private static boolean isAssemblySource(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".asm") || name.endsWith(".inc");
    }

    /**
     * Keeps the rest of the workbench attached to files the user creates, renames or deletes in the explorer:
     * editor tabs, the symbol index, breakpoints and the paths {@code idearm.toml} names.
     */
    private final class ExplorerChanges implements ProjectExplorerViewModel.Listener {

        @Override
        public void created(Path entry, boolean directory) {
            if (directory) {
                return;
            }
            // VS Code opens a file as soon as it is created.
            FxDispatch.run(() -> {
                try {
                    editorArea.openFile(entry);
                } catch (IOException unreadable) {
                    bottomPanel.appendBuildLine("[EXPLORER] " + unreadable.getMessage());
                }
            });
            if (isAssemblySource(entry)) {
                indexSingleFile(entry, "");
            }
        }

        @Override
        public void renamed(Path from, Path to) {
            Path root = currentProjectPath.get();
            if (root == null) {
                return;
            }
            String oldPath = relativeToProject(from);
            String newPath = relativeToProject(to);
            editorArea.pathRenamed(from, to);
            manageBreakpoints.movePath(root, oldPath, newPath);
            bottomPanel.getDebugViewModel().setBreakpoints(manageBreakpoints.getBreakpoints(root));
            followInProjectDescription(root, oldPath, newPath);
            reindex(root);
        }

        @Override
        public void deleted(Path entry) {
            Path root = currentProjectPath.get();
            if (root == null) {
                return;
            }
            String path = relativeToProject(entry);
            editorArea.pathDeleted(entry);
            manageBreakpoints.removePath(root, path);
            bottomPanel.getDebugViewModel().setBreakpoints(manageBreakpoints.getBreakpoints(root));
            Project project = currentProject.get();
            if (project != null && within(project.sources().entry(), path)) {
                statusBar.setStatus(Message.of("status.project.entryDeleted", project.sources().entry()));
            }
            if (path.equalsIgnoreCase("idearm.toml")) {
                openProject(root);
                return;
            }
            reindex(root);
        }

        private void reindex(Path root) {
            symbolIndex.clear();
            indexProjectFiles(root);
        }

        /**
         * Renaming the entry source, an explicit module or an include folder updates {@code idearm.toml}, the
         * way an IDE keeps its project in step with a refactoring; otherwise the next build would fail.
         */
        private void followInProjectDescription(Path root, String oldPath, String newPath) {
            Project project = currentProject.get();
            if (project == null) {
                return;
            }
            if (oldPath.equalsIgnoreCase("idearm.toml")) {
                openProject(root);
                return;
            }
            Sources sources = project.sources();
            String entry = relocate(sources.entry(), oldPath, newPath);
            List<String> modules = sources.modules().stream().map(m -> relocate(m, oldPath, newPath)).toList();
            List<String> include = sources.include().stream().map(i -> relocate(i, oldPath, newPath)).toList();
            if (entry.equals(sources.entry()) && modules.equals(sources.modules()) && include.equals(sources.include())) {
                return;
            }
            Project updated = new Project(project.schema(), project.info(), project.target(), project.toolchain(),
                    new Sources(entry, modules, include, sources.exclude()), project.resources(), project.build(),
                    project.run(), project.debug(), project.dist());
            try {
                services.projectRepository().save(root, updated);
                currentProject.set(updated);
                statusBar.setStatus(Message.of("status.project.updated", newPath));
            } catch (RuntimeException failure) {
                statusBar.setStatus(Message.of("status.task.failed", Problem.of(failure)));
            }
        }

        private static String relocate(String path, String from, String to) {
            if (path.equalsIgnoreCase(from)) {
                return to;
            }
            if (within(path, from)) {
                return to + path.substring(from.length());
            }
            return path;
        }

        private static boolean within(String path, String folderOrFile) {
            return path.equalsIgnoreCase(folderOrFile)
                    || path.regionMatches(true, 0, folderOrFile + "/", 0, folderOrFile.length() + 1);
        }
    }

    public static final class CancellableToken implements CancellationToken {
        private volatile boolean isCancelled;

        @Override
        public boolean cancelled() {
            return isCancelled;
        }

        public void cancel() {
            this.isCancelled = true;
        }
    }
}
