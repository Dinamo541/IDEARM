package io.github.dinamo541.idearm.app;

import atlantafx.base.theme.PrimerDark;
import io.github.dinamo541.idearm.app.bootstrap.WorkbenchBootstrap;
import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.view.WorkbenchView;
import io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel;
import io.github.dinamo541.idearm.app.viewmodel.EditorAreaViewModel;
import io.github.dinamo541.idearm.app.viewmodel.ProjectExplorerViewModel;
import io.github.dinamo541.idearm.app.viewmodel.StatusBarViewModel;
import io.github.dinamo541.idearm.app.viewmodel.WorkbenchViewModel;
import io.github.dinamo541.idearm.domain.IdearmInfo;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

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

        // Check for project path from CLI args, or default to examples/hello if present
        List<String> rawArgs = getParameters().getRaw();
        String smokeProject = System.getenv(PROJECT_ENV);
        if (smokeProject != null && !smokeProject.isBlank()) {
            viewModel.openProject(Path.of(smokeProject));
        } else if (!rawArgs.isEmpty() && !rawArgs.getFirst().isBlank()) {
            Path projectPath = Path.of(rawArgs.getFirst());
            if (Files.exists(projectPath)) {
                viewModel.openProject(projectPath);
            }
        } else {
            Path defaultHello = Path.of("examples", "hello");
            if (Files.exists(defaultHello)) {
                viewModel.openProject(defaultHello);
            }
        }

        stage.titleProperty().bind(localization.text("app.title", IdearmInfo.NAME, IdearmInfo.version()));
        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);
        stage.getIcons().addAll(io.github.dinamo541.idearm.app.ui.BrandLogo.image(32),
                io.github.dinamo541.idearm.app.ui.BrandLogo.image(256));
        stage.setScene(new Scene(workbenchView, 1280, 800));
        workbenchView.installWindowChrome(stage);
        stage.setMinWidth(800);
        stage.setMinHeight(500);
        stage.show();

        String snapshots = System.getenv(SNAPSHOT_ENV);
        String visualSmoke = System.getenv("IDEARM_VISUAL_SMOKE");
        if (System.getenv("IDEARM_DRAG_SMOKE") != null) {
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
        if (viewModel != null) {
            viewModel.dispose();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
