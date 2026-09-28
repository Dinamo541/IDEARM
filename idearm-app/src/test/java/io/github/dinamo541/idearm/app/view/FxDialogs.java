package io.github.dinamo541.idearm.app.view;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.app.editor.FakeEditorComponent;
import io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel;
import io.github.dinamo541.idearm.app.viewmodel.EditorAreaViewModel;
import io.github.dinamo541.idearm.app.viewmodel.ProjectExplorerViewModel;
import io.github.dinamo541.idearm.app.viewmodel.StatusBarViewModel;
import io.github.dinamo541.idearm.app.viewmodel.WorkbenchServices;
import io.github.dinamo541.idearm.app.viewmodel.WorkbenchViewModel;
import io.github.dinamo541.idearm.domain.build.BuildPlan;
import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.build.ToolRunResult;
import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.ResolvedToolchain;
import io.github.dinamo541.idearm.domain.model.Sources;
import io.github.dinamo541.idearm.domain.port.BreakpointStore;
import io.github.dinamo541.idearm.domain.port.ProjectRepository;
import io.github.dinamo541.idearm.domain.port.ToolRegistry;
import io.github.dinamo541.idearm.domain.port.ToolRunner;
import io.github.dinamo541.idearm.infrastructure.workspace.FileBuildWorkspace;
import io.github.dinamo541.idearm.infrastructure.workspace.LocalProjectFiles;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.stage.Stage;

/** Shared plumbing for the dialog tests: one JavaFX toolkit, one workbench, one way to run on the FX thread. */
final class FxDialogs {

    private FxDialogs() {
    }

    static void startJavaFx() throws InterruptedException {
        var latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS), "the JavaFX toolkit did not start");
    }

    /** Scenes must be built on the JavaFX thread, and a failure there has to reach the test rather than a log. */
    static <T> T onFxThread(FxCall<T> call) throws InterruptedException {
        var latch = new CountDownLatch(1);
        var value = new AtomicReference<T>();
        var error = new AtomicReference<Throwable>();
        Platform.runLater(() -> {
            try {
                value.set(call.get());
            } catch (Throwable failure) {
                error.set(failure);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(20, TimeUnit.SECONDS), "the dialog was never built");
        if (error.get() != null) {
            throw new IllegalStateException(error.get());
        }
        return value.get();
    }

    /**
     * A dialog is only right when the window is big enough to show its last control: the Create button vanished
     * from New Project because a row was added to a window whose height was written into the source.
     */
    static void assertFitsInsideItsWindow(Stage dialog, Node last, String what) {
        double sceneHeight = dialog.getScene().getHeight();
        double bottom = last.localToScene(last.getBoundsInLocal()).getMaxY();
        assertTrue(sceneHeight > 0, what + ": the window has no height");
        assertTrue(bottom <= sceneHeight + 1,
                what + " reaches " + bottom + " in a window " + sceneHeight + " tall");
        assertTrue(last.isVisible() && last.getBoundsInLocal().getHeight() > 0, what + " has no size");
    }

    static WorkbenchViewModel workbench(Path scratch, Project project, List<Project> saved) {
        ProjectRepository repository = new ProjectRepository() {
            @Override public Project load(Path projectRoot) { return project; }
            @Override public void save(Path projectRoot, Project updated) { saved.add(updated); }
        };
        ToolRegistry registry = id -> Optional.empty();
        ToolRunner runner = new ToolRunner() {
            @Override public ToolRunResult run(BuildPlan plan, Path projectRoot, ResolvedToolchain tools,
                                               CancellationToken cancellation, Duration timeout) {
                return new ToolRunResult(BuildStatus.FAILED, List.of(), null, "no toolchain in tests");
            }
            @Override public void release(ToolRunResult result) { }
        };
        BreakpointStore breakpoints = new BreakpointStore() {
            @Override public List<Breakpoint> loadBreakpoints(Path projectRoot) { return List.of(); }
            @Override public void saveBreakpoints(Path projectRoot, List<Breakpoint> points) { }
        };
        var services = new WorkbenchServices(repository, registry, new FileBuildWorkspace(), runner,
                scratch.resolve("staging"), List.of(), List.of(), List.of(), List.of(), breakpoints,
                Duration.ofSeconds(5), new LocalProjectFiles());
        return new WorkbenchViewModel(new ProjectExplorerViewModel(),
                new EditorAreaViewModel(FakeEditorComponent::new), new BottomPanelViewModel(),
                new StatusBarViewModel(), services);
    }

    static Project withSources(Project project, String entry, List<String> modules, List<String> include) {
        Sources sources = project.sources();
        return new Project(project.schema(), project.info(), project.target(), project.toolchain(),
                new Sources(entry, modules, include, sources.exclude()), project.resources(), project.build(),
                project.run(), project.debug(), project.dist());
    }

    @FunctionalInterface
    interface FxCall<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    interface DialogCall<T> {
        T apply(Stage dialog) throws Exception;
    }
}
