package io.github.dinamo541.idearm.app;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import io.github.dinamo541.idearm.app.bootstrap.WorkbenchBootstrap;
import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.ui.WorkbenchSession;
import io.github.dinamo541.idearm.app.view.WorkbenchView;
import io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel;
import io.github.dinamo541.idearm.app.viewmodel.EditorAreaViewModel;
import io.github.dinamo541.idearm.app.viewmodel.EditorDocumentViewModel;
import io.github.dinamo541.idearm.app.viewmodel.ProjectExplorerViewModel;
import io.github.dinamo541.idearm.app.viewmodel.StatusBarViewModel;
import io.github.dinamo541.idearm.app.viewmodel.WorkbenchViewModel;
import io.github.dinamo541.idearm.domain.IdearmInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * IDEARM main desktop application entry point.
 *
 * <p>Phase 3: Launches the full VS Code / NetBeans-inspired workbench (MVVM)
 * with project explorer, RichTextFX editor, Problems / Output / Build panels,
 * status bar, and bilingual EN/ES live localization.
 */
public class App extends Application {

    /** When set, the window closes itself right after being shown (automated smoke test). */
    private static final String SMOKE_ENV = "IDEARM_SMOKE";
    /** A folder for screenshots of a scripted explorer walk-through (see SmokeSnapshot). */
    private static final String SNAPSHOT_ENV = "IDEARM_SMOKE_SNAPSHOT";
    /** The project the smoke run opens instead of examples/hello. */
    private static final String PROJECT_ENV = "IDEARM_SMOKE_PROJECT";

    private final Localization localization = new Localization();
    private WorkbenchViewModel viewModel;
    private WorkbenchView workbenchView;

    @Override
    public void start(Stage stage) {
        // The instruction corpus loads while the window is built, not on the JavaFX thread at the first keystroke
        // or the first opening of the Academic Center (ADR-013).
        io.github.dinamo541.idearm.language.knowledge.Corpus.warmUp();

        Path sessionFile = WorkbenchBootstrap.userDataDirectory().resolve("session.properties");
        var session = WorkbenchSession.read(sessionFile);

        if ("es".equalsIgnoreCase(session.language())) {
            localization.localeProperty().set(Localization.SPANISH);
        } else {
            localization.localeProperty().set(Localization.ENGLISH);
        }

        // The bundled typefaces must exist before the first style sheet names them.
        io.github.dinamo541.idearm.app.ui.WorkbenchFonts.load();
        boolean isLight = "light".equalsIgnoreCase(session.theme());
        Application.setUserAgentStylesheet(isLight
                ? new PrimerLight().getUserAgentStylesheet()
                : new PrimerDark().getUserAgentStylesheet());
        io.github.dinamo541.idearm.app.ui.WorkbenchTheme.install();
        io.github.dinamo541.idearm.app.ui.BrandLogo.install();
        io.github.dinamo541.idearm.app.ui.BrandLogo.apply(stage);

        io.github.dinamo541.idearm.app.viewmodel.WorkbenchServices services;
        try {
            services = WorkbenchBootstrap.system();
        } catch (RuntimeException failure) {
            // Without this the window simply never appeared, and the reason only reached a console nobody sees.
            failure.printStackTrace();
            showStartupFailure(failure);
            Platform.exit();
            return;
        }
        this.viewModel = new WorkbenchViewModel(
                new ProjectExplorerViewModel(),
                new EditorAreaViewModel(),
                new BottomPanelViewModel(services.terminalRunner()),
                new StatusBarViewModel(),
                services);
        this.workbenchView = new WorkbenchView(viewModel, localization);
        if (isLight) {
            workbenchView.setLightTheme(true);
        }

        workbenchView.setSidebarVisible(session.sidebarVisible());
        workbenchView.setSidebarPosition(session.sidebarPosition());
        workbenchView.selectBottomTab(session.activePanelTab());
        workbenchView.setBottomPanelVisible(session.panelVisible());
        workbenchView.setBottomPanelPosition(session.panelPosition());
        workbenchView.setMinimapVisible(session.minimapVisible());

        // Check for project path from CLI args, or default to lastProject if saved, or examples/hello
        List<String> rawArgs = getParameters().getRaw();
        String smokeProject = System.getenv(PROJECT_ENV);
        if (smokeProject != null && !smokeProject.isBlank()) {
            viewModel.openProject(Path.of(smokeProject));
        } else if (!rawArgs.isEmpty() && !rawArgs.getFirst().isBlank()) {
            Path projectPath = Path.of(rawArgs.getFirst());
            if (Files.exists(projectPath)) {
                viewModel.openProject(projectPath);
            }
        } else if (session.lastProject() != null && Files.isDirectory(session.lastProject())) {
            viewModel.openProject(session.lastProject());
        } else {
            Path defaultHello = Path.of("examples", "hello");
            if (Files.exists(defaultHello)) {
                viewModel.openProject(defaultHello);
            } else if (!session.openFiles().isEmpty()) {
                restoreStandaloneFiles(session);
            }
        }

        stage.titleProperty().bind(localization.text("app.title", IdearmInfo.NAME, IdearmInfo.version()));
        stage.initStyle(io.github.dinamo541.idearm.app.ui.WindowChrome.stageStyle());
        stage.setScene(new Scene(workbenchView, 1280, 800));
        workbenchView.installWindowChrome(stage);
        io.github.dinamo541.idearm.app.ui.WindowSession.install(stage,
                WorkbenchBootstrap.userDataDirectory().resolve("window.properties"));
        WorkbenchSession.install(stage, this::saveSession);
        stage.show();

        String snapshots = System.getenv(SNAPSHOT_ENV);
        String visualSmoke = System.getenv("IDEARM_VISUAL_SMOKE");
        String designSmoke = System.getenv("IDEARM_DESIGN_SMOKE");
        String windowSmoke = System.getenv("IDEARM_WINDOW_SMOKE");
        if (windowSmoke != null && !windowSmoke.isBlank()) {
            SmokeSnapshot.windowLayouts(stage, workbenchView, Path.of(windowSmoke));
        } else if (designSmoke != null && !designSmoke.isBlank()) {
            SmokeSnapshot.design(stage, workbenchView, Path.of(designSmoke));
        } else if (System.getenv("IDEARM_DRAG_SMOKE") != null) {
            SmokeSnapshot.dragWindow(stage, workbenchView);
        } else if (visualSmoke != null && !visualSmoke.isBlank()) {
            SmokeSnapshot.visual(stage, workbenchView, Path.of(visualSmoke));
        } else if (snapshots != null && !snapshots.isBlank()) {
            SmokeSnapshot.run(stage, workbenchView, Path.of(snapshots));
        } else if (System.getenv(SMOKE_ENV) != null) {
            String enTitle = stage.getTitle();
            localization.localeProperty().set(Localization.SPANISH);
            String esTitle = stage.getTitle();
            System.out.printf("SMOKE OK: %s %s | en=\"%s\" es=\"%s\"%n",
                    IdearmInfo.NAME, IdearmInfo.version(), enTitle, esTitle);
            Platform.exit();
        }
    }

    private void saveSession() {
        if (viewModel == null || workbenchView == null) return;
        Path project = viewModel.getCurrentProjectPath();
        if (project != null && Files.isDirectory(project)) {
            viewModel.saveWorkspaceState(project);
        }
        List<WorkbenchSession.FileState> openFiles = new ArrayList<>();
        for (var doc : viewModel.getEditorArea().getDocuments()) {
            openFiles.add(new WorkbenchSession.FileState(
                    doc.getFilePath(),
                    doc.caretLineProperty().get(),
                    doc.caretColumnProperty().get()));
        }
        var active = viewModel.getEditorArea().getActiveDocument();
        Path activePath = active != null ? active.getFilePath() : null;
        var tab = viewModel.getBottomPanel().activeTabProperty().get();

        var state = new WorkbenchSession.WorkbenchSessionState(
                project,
                openFiles,
                activePath,
                workbenchView.isSidebarVisible(),
                workbenchView.getSidebarPosition(),
                workbenchView.isBottomPanelVisible(),
                workbenchView.getBottomPanelPosition(),
                tab != null ? tab.name() : "OUTPUT",
                workbenchView.isLightTheme() ? "light" : "dark",
                localization.localeProperty().get().getLanguage(),
                workbenchView.isMinimapVisible()
        );
        WorkbenchSession.write(
                WorkbenchBootstrap.userDataDirectory().resolve("session.properties"), state);
    }

    private void restoreStandaloneFiles(WorkbenchSession.WorkbenchSessionState session) {
        EditorDocumentViewModel activeDoc = null;
        for (var fs : session.openFiles()) {
            if (Files.isRegularFile(fs.path())) {
                try {
                    var doc = viewModel.getEditorArea().openFile(fs.path());
                    if (fs.line() > 0) {
                        doc.getEditor().goToLine(fs.line());
                    }
                    if (session.activeFile() != null && fs.path().equals(session.activeFile())) {
                        activeDoc = doc;
                    }
                } catch (IOException ignored) {}
            }
        }
        if (activeDoc != null) {
            viewModel.getEditorArea().setActiveDocument(activeDoc);
            activeDoc.getEditor().requestFocus();
        }
    }

    private void showStartupFailure(RuntimeException failure) {
        var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(IdearmInfo.NAME);
        alert.setHeaderText(localization.get("app.startup.failed"));
        alert.setContentText(localization.describe(io.github.dinamo541.idearm.app.i18n.Problem.of(failure)));
        alert.showAndWait();
    }

    /** Stops the file watcher and background tasks when the window closes. */
    @Override
    public void stop() {
        saveSession();
        if (viewModel != null) {
            viewModel.dispose();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
