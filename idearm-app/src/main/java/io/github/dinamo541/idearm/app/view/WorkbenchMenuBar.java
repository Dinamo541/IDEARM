package io.github.dinamo541.idearm.app.view;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.viewmodel.WorkbenchViewModel;
import io.github.dinamo541.idearm.domain.IdearmInfo;
import java.io.File;
import java.io.IOException;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/**
 * Main menu bar of the workbench with keyboard accelerators.
 */
public final class WorkbenchMenuBar extends MenuBar {

    private final WorkbenchViewModel viewModel;
    private final Localization localization;
    private Runnable onCommandPaletteRequested;
    private Runnable onNewFileRequested = () -> { };
    private Runnable onNewFolderRequested = () -> { };

    public WorkbenchMenuBar(WorkbenchViewModel viewModel, Localization localization) {
        this.viewModel = viewModel;
        this.localization = localization;

        getMenus().addAll(
                createFileMenu(),
                createEditMenu(),
                createViewMenu(),
                createProjectMenu(),
                createRunMenu(),
                createHelpMenu()
        );
    }

    public void setOnCommandPaletteRequested(Runnable onCommandPaletteRequested) {
        this.onCommandPaletteRequested = onCommandPaletteRequested;
    }

    /** File > New File creates the entry inline in the explorer, as in VS Code. */
    public void setOnNewFileRequested(Runnable listener) {
        this.onNewFileRequested = listener;
    }

    public void setOnNewFolderRequested(Runnable listener) {
        this.onNewFolderRequested = listener;
    }

    /** Asks for a folder and opens it as the current project. */
    public void openProjectDialog() {
        Window window = getScene() != null ? getScene().getWindow() : null;
        var chooser = new DirectoryChooser();
        chooser.setTitle(localization.get("dialog.openProject.title"));
        File selected = chooser.showDialog(window);
        if (selected != null) {
            viewModel.openProject(selected.toPath());
        }
    }

    /** Asks for a folder and imports it as a project. */
    public void importProjectDialog() {
        Window window = getScene() != null ? getScene().getWindow() : null;
        var chooser = new DirectoryChooser();
        chooser.setTitle(localization.get("dialog.importProject.title"));
        File selected = chooser.showDialog(window);
        if (selected != null) {
            try {
                viewModel.importProject(selected.toPath());
            } catch (RuntimeException ex) {
                showError(localization.get("dialog.error.import"), ex.getMessage());
            }
        }
    }

    public void openFileDialog() {
        var chooser = new javafx.stage.FileChooser();
        chooser.setTitle(localization.get("menu.file.openFile"));
        var file = chooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            try { viewModel.getEditorArea().openFile(file.toPath()).getEditor().requestFocus(); }
            catch (IOException ex) { showError(localization.get("menu.file.openFile"), ex.getMessage()); }
        }
    }

    public void saveAsDialog() {
        var doc = viewModel.getEditorArea().getActiveDocument();
        if (doc == null) return;
        var chooser = new javafx.stage.FileChooser();
        chooser.setTitle(localization.get("menu.file.saveAs"));
        chooser.setInitialFileName(doc.getFilePath().getFileName().toString());
        var file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;
        var target = file.toPath().toAbsolutePath().normalize();
        if (viewModel.getEditorArea().getDocuments().stream().anyMatch(other -> other != doc && other.getFilePath().equals(target))) {
            showError(localization.get("dialog.error.save"), localization.get("editor.destinationOpen"));
            return;
        }
        var before = doc.getFilePath();
        try {
            doc.filePathProperty().set(target); doc.save();
            viewModel.getRecentItems().remember(io.github.dinamo541.idearm.domain.model.RecentItem.Kind.FILE, target);
        }
        catch (IOException ex) {
            doc.filePathProperty().set(before);
            showError(localization.get("dialog.error.save"), ex.getMessage());
        }
    }

    public void openRecentDialog() {
        new OpenRecentDialog(getScene().getWindow(), viewModel, localization).show();
    }

    private Menu createFileMenu() {
        var menu = new Menu();
        menu.textProperty().bind(localization.text("menu.file"));

        var newProject = new MenuItem();
        newProject.textProperty().bind(localization.text("menu.file.newProject"));
        newProject.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        newProject.setOnAction(e -> {
            Window window = getScene() != null ? getScene().getWindow() : null;
            new NewProjectDialog(window, viewModel, localization).showAndWait();
        });

        var openProject = new MenuItem();
        openProject.textProperty().bind(localization.text("menu.file.openProject"));

        openProject.setOnAction(e -> openProjectDialog());

        var openFile = new MenuItem();
        openFile.textProperty().bind(localization.text("menu.file.openFile"));
        openFile.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        openFile.setOnAction(e -> openFileDialog());

        var recent = new MenuItem();
        recent.textProperty().bind(localization.text("recent.title"));
        recent.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.HISTORY.create());
        recent.setAccelerator(new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN));
        recent.setOnAction(e -> openRecentDialog());

        var newFile = new MenuItem();
        newFile.textProperty().bind(localization.text("menu.file.newFile"));
        newFile.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        newFile.setOnAction(e -> onNewFileRequested.run());
        newFile.disableProperty().bind(viewModel.getExplorer().projectRootProperty().isNull());

        var newFolder = new MenuItem();
        newFolder.textProperty().bind(localization.text("menu.file.newFolder"));
        newFolder.setOnAction(e -> onNewFolderRequested.run());
        newFolder.disableProperty().bind(viewModel.getExplorer().projectRootProperty().isNull());

        var importProject = new MenuItem();
        importProject.textProperty().bind(localization.text("menu.file.importProject"));
        importProject.setOnAction(e -> importProjectDialog());

        var save = new MenuItem();
        save.textProperty().bind(localization.text("menu.file.save"));
        save.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        save.setOnAction(e -> {
            try {
                viewModel.getEditorArea().saveActive();
            } catch (IOException ex) {
                showError(localization.get("dialog.error.save"), ex.getMessage());
            }
        });

        var saveAll = new MenuItem();
        saveAll.textProperty().bind(localization.text("menu.file.saveAll"));

        saveAll.setOnAction(e -> {
            try {
                viewModel.getEditorArea().saveAll();
            } catch (IOException ex) {
                showError(localization.get("dialog.error.save"), ex.getMessage());
            }
        });

        var saveAs = new MenuItem();
        saveAs.textProperty().bind(localization.text("menu.file.saveAs"));
        saveAs.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        saveAs.setOnAction(e -> saveAsDialog());
        saveAs.disableProperty().bind(viewModel.getEditorArea().activeDocumentProperty().isNull());

        var exit = new MenuItem();
        exit.textProperty().bind(localization.text("menu.file.exit"));
        exit.setAccelerator(new KeyCodeCombination(KeyCode.F4, KeyCombination.ALT_DOWN));
        exit.setOnAction(e -> io.github.dinamo541.idearm.app.ui.WindowChrome.requestClose((javafx.stage.Stage) getScene().getWindow()));

        menu.getItems().addAll(newFile, newFolder, new SeparatorMenuItem(), newProject, openFile, openProject, recent, importProject,
                new SeparatorMenuItem(), save, saveAs, saveAll, new SeparatorMenuItem(), exit);
        return menu;
    }

    private Menu createEditMenu() {
        var menu = new Menu();
        menu.textProperty().bind(localization.text("menu.edit"));

        for (var command : io.github.dinamo541.idearm.app.editor.EditorCommand.values()) {
            var item = new MenuItem();
            item.textProperty().bind(localization.text(command.key()));
            // The editor handles the keys locally: menu accelerators must not steal terminal/text-field input.
            item.setOnAction(e -> {
                var doc = viewModel.getEditorArea().getActiveDocument();
                if (doc != null && doc.getEditor() instanceof io.github.dinamo541.idearm.app.editor.RichTextFxEditorComponent editor) {
                    editor.execute(command);
                }
            });
            item.disableProperty().bind(viewModel.getEditorArea().activeDocumentProperty().isNull());
            var hint = new javafx.scene.control.Label(command.shortcut().getDisplayText());
            hint.getStyleClass().add("shortcut-hint");
            item.setGraphic(hint);
            menu.getItems().add(item);
        }
        return menu;
    }

    private Menu createViewMenu() {
        var menu = new Menu();
        menu.textProperty().bind(localization.text("menu.view"));

        var palette = new MenuItem();
        palette.textProperty().bind(localization.text("menu.view.commandPalette"));
        palette.setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        palette.setOnAction(e -> {
            if (onCommandPaletteRequested != null) {
                onCommandPaletteRequested.run();
            }
        });

        var terminal = new MenuItem();
        terminal.textProperty().bind(localization.text("menu.view.terminal"));
        terminal.setAccelerator(new KeyCodeCombination(KeyCode.BACK_QUOTE, KeyCombination.CONTROL_DOWN));
        terminal.setOnAction(e -> viewModel.openTerminal());

        menu.getItems().addAll(palette, new SeparatorMenuItem(), terminal);
        return menu;
    }

    private Menu createProjectMenu() {
        var menu = new Menu();
        menu.textProperty().bind(localization.text("menu.project"));

        var build = new MenuItem();
        build.textProperty().bind(localization.text("menu.project.build"));
        build.setAccelerator(new KeyCodeCombination(KeyCode.F7));
        build.setOnAction(e -> viewModel.build());
        build.disableProperty().bind(viewModel.busyProperty().or(viewModel.currentProjectProperty().isNull()));

        var cleanAndBuild = new MenuItem();
        cleanAndBuild.textProperty().bind(localization.text("menu.project.cleanAndBuild"));
        cleanAndBuild.setAccelerator(new KeyCodeCombination(KeyCode.F7, KeyCombination.SHIFT_DOWN));
        cleanAndBuild.setOnAction(e -> viewModel.cleanAndBuild());
        cleanAndBuild.disableProperty().bind(viewModel.busyProperty().or(viewModel.currentProjectProperty().isNull()));

        var clean = new MenuItem();
        clean.textProperty().bind(localization.text("menu.project.clean"));
        clean.setOnAction(e -> viewModel.clean());
        clean.disableProperty().bind(viewModel.busyProperty().or(viewModel.currentProjectProperty().isNull()));

        var dist = new MenuItem();
        dist.textProperty().bind(localization.text("menu.project.package"));
        dist.setOnAction(e -> viewModel.packageDist());
        dist.disableProperty().bind(viewModel.busyProperty().or(viewModel.currentProjectProperty().isNull()));

        var properties = new MenuItem();
        properties.textProperty().bind(localization.text("menu.project.properties"));
        properties.setOnAction(e -> {
            Window window = getScene() != null ? getScene().getWindow() : null;
            new ProjectPropertiesDialog(window, viewModel, localization).showAndWait();
        });

        menu.getItems().addAll(build, cleanAndBuild, clean, dist, new SeparatorMenuItem(), properties);
        return menu;
    }

    private Menu createRunMenu() {
        var menu = new Menu();
        menu.textProperty().bind(localization.text("menu.run"));

        var debug = new MenuItem();
        debug.textProperty().bind(localization.text("menu.run.debug"));
        debug.setAccelerator(new KeyCodeCombination(KeyCode.F5));
        var paused = viewModel.getBottomPanel().getDebugViewModel().pausedProperty();
        debug.setOnAction(e -> {
            if (paused.get()) {
                viewModel.resumeDebug();
            } else {
                viewModel.debug();
            }
        });
        debug.disableProperty().bind(viewModel.currentProjectProperty().isNull()
                .or(viewModel.busyProperty().and(paused.not())));

        var restartDebug = new MenuItem();
        restartDebug.textProperty().bind(localization.text("menu.run.restartDebug"));
        restartDebug.setAccelerator(new KeyCodeCombination(KeyCode.F5, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        restartDebug.setOnAction(e -> viewModel.restartDebug());
        restartDebug.disableProperty().bind(viewModel.getBottomPanel().getDebugViewModel().activeProperty().not());

        var resume = new MenuItem();
        resume.textProperty().bind(localization.text("menu.run.resume"));
        resume.setOnAction(e -> viewModel.resumeDebug());
        resume.disableProperty().bind(viewModel.getBottomPanel().getDebugViewModel().pausedProperty().not());

        var stepOver = new MenuItem();
        stepOver.textProperty().bind(localization.text("menu.run.stepOver"));
        stepOver.setAccelerator(new KeyCodeCombination(KeyCode.F10));
        stepOver.setOnAction(e -> viewModel.stepOver());
        stepOver.disableProperty().bind(viewModel.getBottomPanel().getDebugViewModel().pausedProperty().not());

        var stepInto = new MenuItem();
        stepInto.textProperty().bind(localization.text("menu.run.stepInto"));
        stepInto.setAccelerator(new KeyCodeCombination(KeyCode.F11));
        stepInto.setOnAction(e -> viewModel.stepInto());
        stepInto.disableProperty().bind(viewModel.getBottomPanel().getDebugViewModel().pausedProperty().not());

        var stepOut = new MenuItem();
        stepOut.textProperty().bind(localization.text("menu.run.stepOut"));
        stepOut.setAccelerator(new KeyCodeCombination(KeyCode.F11, KeyCombination.SHIFT_DOWN));
        stepOut.setOnAction(e -> viewModel.stepOut());
        stepOut.disableProperty().bind(viewModel.getBottomPanel().getDebugViewModel().pausedProperty().not());

        var run = new MenuItem();
        run.textProperty().bind(localization.text("menu.run.run"));
        run.setAccelerator(new KeyCodeCombination(KeyCode.F5, KeyCombination.CONTROL_DOWN));
        run.setOnAction(e -> viewModel.run(false));
        run.disableProperty().bind(viewModel.busyProperty().or(viewModel.currentProjectProperty().isNull()));

        var runKeepOpen = new MenuItem();
        runKeepOpen.textProperty().bind(localization.text("menu.run.runKeepOpen"));
        runKeepOpen.setOnAction(e -> viewModel.run(true));
        runKeepOpen.disableProperty().bind(viewModel.busyProperty().or(viewModel.currentProjectProperty().isNull()));

        var toggleBp = new MenuItem();
        toggleBp.textProperty().bind(localization.text("menu.run.toggleBreakpoint"));
        toggleBp.setAccelerator(new KeyCodeCombination(KeyCode.F9));
        toggleBp.setOnAction(e -> viewModel.toggleBreakpointAtCaret());

        var clearBps = new MenuItem();
        clearBps.textProperty().bind(localization.text("menu.run.clearBreakpoints"));
        clearBps.setOnAction(e -> viewModel.clearAllBreakpoints());
        clearBps.disableProperty().bind(viewModel.currentProjectProperty().isNull());

        var stop = new MenuItem();
        stop.textProperty().bind(localization.text("menu.run.stop"));
        stop.setAccelerator(new KeyCodeCombination(KeyCode.F5, KeyCombination.SHIFT_DOWN));
        stop.setOnAction(e -> viewModel.stop());
        stop.disableProperty().bind(viewModel.busyProperty().not());

        menu.getItems().addAll(debug, restartDebug, resume, stepOver, stepInto, stepOut, new SeparatorMenuItem(), run, runKeepOpen, new SeparatorMenuItem(), toggleBp, clearBps, new SeparatorMenuItem(), stop);
        return menu;
    }

    private Menu createHelpMenu() {
        var menu = new Menu();
        menu.textProperty().bind(localization.text("menu.help"));

        var doctor = new MenuItem();
        doctor.textProperty().bind(localization.text("menu.help.doctor"));
        doctor.setOnAction(e -> {
            Window window = getScene() != null ? getScene().getWindow() : null;
            new DoctorDialog(window, localization, viewModel.getToolRegistry()).showAndWait();
        });

        var about = new MenuItem();
        about.textProperty().bind(localization.text("menu.help.about"));
        about.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(IdearmInfo.NAME);
            alert.setHeaderText(IdearmInfo.NAME + " v" + IdearmInfo.version());
            alert.setContentText(localization.get("dialog.about.content"));
            alert.showAndWait();
        });

        menu.getItems().addAll(doctor, new SeparatorMenuItem(), about);
        return menu;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
