package io.github.dinamo541.idearm.app.view;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.viewmodel.EditorDocumentViewModel;
import io.github.dinamo541.idearm.app.viewmodel.WorkbenchViewModel;
import io.github.dinamo541.idearm.domain.IdearmInfo;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javafx.application.Application;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/**
 * Main window layout view connecting Explorer, Editor, Bottom Panel, Toolbar, Menu,
 * Status Bar, and Command Palette.
 */
public final class WorkbenchView extends BorderPane {

    private final WorkbenchViewModel viewModel;
    private final Localization localization;

    private final WorkbenchMenuBar menuBar;
    private final WorkbenchToolBar toolBar;
    private final ProjectExplorerView explorerView;
    private final EditorAreaView editorAreaView;
    private final BottomPanelView bottomPanelView;
    private final StatusBarView statusBarView;
    private final DocumentOutlineView outlineView;
    private final SplitPane centerVerticalSplit;
    private final SplitPane mainHorizontalSplit;
    private final SplitPane sidebarVerticalSplit;
    private javafx.scene.layout.HBox windowHeader;
    private boolean lightTheme;
    private long chordStarted;
    private double sidebarPosition = 0.21;
    private double panelPosition = 0.73;

    public WorkbenchView(WorkbenchViewModel viewModel, Localization localization) {
        this.viewModel = viewModel;
        this.localization = localization;

        this.menuBar = new WorkbenchMenuBar(viewModel, localization);
        this.toolBar = new WorkbenchToolBar(viewModel, localization);
        this.explorerView = new ProjectExplorerView(viewModel.getExplorer(), localization);
        this.outlineView = new DocumentOutlineView(localization);
        this.editorAreaView = new EditorAreaView(viewModel.getEditorArea(), localization);
        this.bottomPanelView = new BottomPanelView(viewModel.getBottomPanel(), localization);
        viewModel.setProblemFormatter(localization::describe);
        this.statusBarView = new StatusBarView(viewModel.getStatusBar(), localization);

        menuBar.setOnCommandPaletteRequested(this::openCommandPalette);
        menuBar.setOnNewFileRequested(explorerView::newFile);
        menuBar.setOnNewFolderRequested(explorerView::newFolder);
        explorerView.setOnOpenFolderRequested(menuBar::openProjectDialog);
        explorerView.setOnStatus(viewModel.getStatusBar()::setStatus);
        toolBar.setOnCommandPaletteRequested(this::openCommandPalette);
        toolBar.setOnRecentRequested(menuBar::openRecentDialog);

        getStyleClass().add("workbench");
        getStylesheets().add(java.util.Objects.requireNonNull(getClass().getResource("workbench.css")).toExternalForm());
        toolBar.setOnThemeRequested(this::toggleTheme);
        var logo = io.github.dinamo541.idearm.app.ui.BrandLogo.create(25);
        var logoBox = new javafx.scene.layout.StackPane(logo);
        logoBox.getStyleClass().add("title-logo");
        var header = new javafx.scene.layout.HBox(logoBox, menuBar, toolBar);
        windowHeader = header;
        toolBar.setMinWidth(90);
        header.getStyleClass().add("workbench-header");
        javafx.scene.layout.HBox.setHgrow(toolBar, javafx.scene.layout.Priority.ALWAYS);
        setTop(header);
        setLeft(createActivityBar());

        // Center: Horizontal SplitPane ([Explorer / Outline] | [Editor / BottomPanel])
        centerVerticalSplit = new SplitPane(editorAreaView, bottomPanelView);
        centerVerticalSplit.setOrientation(Orientation.VERTICAL);
        centerVerticalSplit.setDividerPositions(panelPosition);

        sidebarVerticalSplit = new SplitPane(explorerView, outlineView);
        sidebarVerticalSplit.getStyleClass().add("sidebar");
        sidebarVerticalSplit.setMinWidth(180);
        sidebarVerticalSplit.setOrientation(Orientation.VERTICAL);
        sidebarVerticalSplit.setDividerPositions(0.78);

        mainHorizontalSplit = new SplitPane(sidebarVerticalSplit, centerVerticalSplit);
        mainHorizontalSplit.setOrientation(Orientation.HORIZONTAL);
        mainHorizontalSplit.setDividerPositions(sidebarPosition);
        SplitPane.setResizableWithParent(sidebarVerticalSplit, false);

        setCenter(mainHorizontalSplit);

        // Bottom: Status Bar
        setBottom(statusBarView);

        // Wiring interactions
        setupInteractions();

        viewModel.getBottomPanel().activeTabProperty().addListener((o, before, after) -> showPanel());
        addEventFilter(KeyEvent.KEY_PRESSED, this::workbenchKeys);

        // Global hotkeys (Ctrl+Shift+P, F1 for Command Palette)
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if ((event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.P)
                    || event.getCode() == KeyCode.F1) {
                openCommandPalette();
                event.consume();
            }
        });
    }

    public void installWindowChrome(javafx.stage.Stage stage) {
        windowHeader.getChildren().add(io.github.dinamo541.idearm.app.ui.WindowChrome.controls(stage, localization));
        io.github.dinamo541.idearm.app.ui.WindowChrome.install(stage, windowHeader, localization);
        stage.setOnCloseRequest(event -> { if (!confirmClose()) event.consume(); });
    }

    private boolean confirmClose() {
        var modified = viewModel.getEditorArea().getDocuments().stream().filter(EditorDocumentViewModel::isModified).toList();
        if (modified.isEmpty()) return true;
        var save = new javafx.scene.control.ButtonType(localization.get("menu.file.saveAll"));
        var discard = new javafx.scene.control.ButtonType(localization.get("editor.discard"));
        var cancel = new javafx.scene.control.ButtonType(localization.get("explorer.cancel"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        var dialog = new Alert(Alert.AlertType.CONFIRMATION, localization.get("window.unsaved", modified.size()), save, discard, cancel);
        dialog.initOwner(getScene().getWindow()); dialog.setHeaderText(null);
        var choice = dialog.showAndWait().orElse(cancel);
        if (choice == cancel) return false;
        if (choice == save) {
            try { viewModel.getEditorArea().saveAll(); }
            catch (IOException failure) {
                var error = new Alert(Alert.AlertType.ERROR, failure.getMessage());
                error.initOwner(getScene().getWindow()); error.showAndWait(); return false;
            }
        }
        return true;
    }

    private javafx.scene.Node createActivityBar() {
        var bar = new VBox();
        bar.getStyleClass().add("activity-bar");
        bar.getChildren().add(activity("M5 3H17V19H5Z M9 3V1H21V15H17", "explorer.title", this::toggleSidebar));
        bar.getChildren().add(activity("M16 10A6 6 0 1 1 4 10A6 6 0 1 1 16 10 M15 15L21 21", "editor.command.find", () -> edit(io.github.dinamo541.idearm.app.editor.EditorCommand.FIND)));
        bar.getChildren().add(activity("M7 3L20 12L7 21Z", "panel.debug.title", () -> selectPanel(io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel.BottomTab.DEBUG)));
        bar.getChildren().add(activity("M3 4H21V20H3Z M7 8L11 12L7 16 M13 16H18", "panel.terminal.title", () -> { showPanel(); viewModel.openTerminal(); }));
        var space = new javafx.scene.layout.Region();
        VBox.setVgrow(space, javafx.scene.layout.Priority.ALWAYS);
        bar.getChildren().add(space);
        bar.getChildren().add(activity("M12 3V7 M12 17V21 M3 12H7 M17 12H21 M6 6L9 9 M15 15L18 18 M6 18L9 15 M15 9L18 6 M17 12A5 5 0 1 1 7 12A5 5 0 1 1 17 12", "action.commandPalette", this::openCommandPalette));
        return bar;
    }
    private javafx.scene.control.Button activity(String path, String key, Runnable action) {
        var icon = new javafx.scene.shape.SVGPath(); icon.setContent(path);
        icon.getStyleClass().add("activity-icon");
        var button = new javafx.scene.control.Button(); button.setGraphic(icon);
        button.getStyleClass().add("activity-button");
        String help = switch (key) {
            case "explorer.title" -> "help.explorer";
            case "editor.command.find" -> "help.find";
            case "panel.debug.title" -> "help.debugPanel";
            case "panel.terminal.title" -> "help.terminal";
            default -> "help.commands";
        };
        io.github.dinamo541.idearm.app.ui.HoverHelp.install(button, localization, help, "");
        button.setOnAction(e -> action.run()); return button;
    }
    private void toggleTheme() {
        lightTheme = !lightTheme;
        getStyleClass().remove("light");
        if (lightTheme) getStyleClass().add("light");
        Application.setUserAgentStylesheet(lightTheme ? new PrimerLight().getUserAgentStylesheet() : new PrimerDark().getUserAgentStylesheet());
    }
    private void toggleSidebar() {
        if (mainHorizontalSplit.getItems().contains(sidebarVerticalSplit)) {
            sidebarPosition = mainHorizontalSplit.getDividerPositions()[0];
            mainHorizontalSplit.getItems().remove(sidebarVerticalSplit);
        } else {
            mainHorizontalSplit.getItems().addFirst(sidebarVerticalSplit);
            mainHorizontalSplit.setDividerPositions(sidebarPosition);
        }
    }
    private void showPanel() {
        if (!centerVerticalSplit.getItems().contains(bottomPanelView)) {
            centerVerticalSplit.getItems().add(bottomPanelView);
            centerVerticalSplit.setDividerPositions(panelPosition);
        }
    }
    private void togglePanel() {
        if (centerVerticalSplit.getItems().contains(bottomPanelView)) {
            panelPosition = centerVerticalSplit.getDividerPositions()[0];
            centerVerticalSplit.getItems().remove(bottomPanelView);
        } else showPanel();
    }
    private void selectPanel(io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel.BottomTab tab) {
        showPanel(); viewModel.getBottomPanel().setActiveTab(tab);
    }
    private void edit(io.github.dinamo541.idearm.app.editor.EditorCommand command) {
        var doc = viewModel.getEditorArea().getActiveDocument();
        if (doc != null && doc.getEditor() instanceof io.github.dinamo541.idearm.app.editor.RichTextFxEditorComponent editor) editor.execute(command);
    }
    private void workbenchKeys(KeyEvent e) {
        if (chordStarted != 0 && e.getCode().isModifierKey()) return;
        if (chordStarted != 0) {
            long elapsed = System.nanoTime() - chordStarted; chordStarted = 0;
            if (elapsed < 2_000_000_000L && e.getCode() == KeyCode.S && !e.isShiftDown() && !e.isAltDown()) {
                try { viewModel.getEditorArea().saveAll(); }
                catch (IOException ex) { viewModel.getStatusBar().setStatus(io.github.dinamo541.idearm.app.i18n.Message.of("status.task.failed", io.github.dinamo541.idearm.app.i18n.Problem.of(ex))); }
                e.consume(); return;
            }
            if (elapsed < 2_000_000_000L && e.getCode() == KeyCode.O && e.isControlDown()) {
                menuBar.openProjectDialog(); e.consume(); return;
            }
        }
        if (e.getCode() == KeyCode.F11 && !e.isControlDown() && !e.isShiftDown() && !e.isAltDown()
                && !viewModel.getBottomPanel().getDebugViewModel().activeProperty().get()) {
            if (getScene().getWindow() instanceof javafx.stage.Stage stage) stage.setFullScreen(!stage.isFullScreen());
            e.consume(); return;
        }
        if (!e.isControlDown() || e.isAltDown() || e.isMetaDown()) return;
        if (!e.isShiftDown() && e.getCode() == KeyCode.K) { chordStarted = System.nanoTime(); }
        else if (!e.isShiftDown() && e.getCode() == KeyCode.B) toggleSidebar();
        else if (!e.isShiftDown() && e.getCode() == KeyCode.J) togglePanel();
        else if (!e.isShiftDown() && (e.getCode() == KeyCode.W || e.getCode() == KeyCode.F4)) editorAreaView.closeActive();
        else if (e.getCode() == KeyCode.TAB) editorAreaView.cycle(e.isShiftDown() ? -1 : 1);
        else if (!e.isShiftDown() && e.getCode() == KeyCode.DIGIT1) {
            var doc = viewModel.getEditorArea().getActiveDocument(); if (doc != null) doc.getEditor().requestFocus();
        } else if (e.isShiftDown() && e.getCode() == KeyCode.E) {
            if (!mainHorizontalSplit.getItems().contains(sidebarVerticalSplit)) toggleSidebar();
            explorerView.requestFocus();
        } else if (e.isShiftDown() && e.getCode() == KeyCode.M) selectPanel(io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel.BottomTab.PROBLEMS);
        else if (e.isShiftDown() && e.getCode() == KeyCode.U) selectPanel(io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel.BottomTab.OUTPUT);
        else if (e.isShiftDown() && e.getCode() == KeyCode.D) selectPanel(io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel.BottomTab.DEBUG);
        else if (e.isShiftDown() && e.getCode() == KeyCode.B) viewModel.build();
        else if (!e.isShiftDown() && e.getCode() == KeyCode.BACK_QUOTE) { showPanel(); viewModel.openTerminal(); }
        else return;
        e.consume();
    }

    private void setupInteractions() {
        // Configure new and existing editors with language intelligence providers
        viewModel.getEditorArea().setEditorConfigurer(component -> {
            if (component instanceof io.github.dinamo541.idearm.app.editor.RichTextFxEditorComponent rtf) {
                rtf.setLocalization(localization);
            }
            component.setOnDefinitionRequested(viewModel::goToDefinition);
            component.setOnReferencesRequested(viewModel::findReferences);
            component.setHoverProvider(word -> {
                String lang = localization.localeProperty().get().getLanguage();
                return viewModel.getHover(word, lang)
                        .map(info -> io.github.dinamo541.idearm.app.editor.HoverText.localize(info, localization));
            });
            component.setCompletionProvider(viewModel::getCompletions);
        });

        // Explorer file double-click -> open in editor
        explorerView.setOnFileSelected(path -> {
            try {
                viewModel.getEditorArea().openFile(path);
            } catch (IOException unreadable) {
                viewModel.getStatusBar().setStatus(
                        io.github.dinamo541.idearm.app.i18n.Message.of("status.task.failed", io.github.dinamo541.idearm.app.i18n.Problem.of(unreadable)));
            }
        });

        // Outline item click -> jump to line in active editor
        outlineView.setOnItemSelected(item -> {
            EditorDocumentViewModel activeDoc = viewModel.getEditorArea().getActiveDocument();
            if (activeDoc != null && item.line() > 0) {
                activeDoc.getEditor().goToLine(item.line());
                activeDoc.getEditor().requestFocus();
            }
        });

        // References row double-click -> open file and jump to line
        bottomPanelView.setOnReferenceSelected(ref -> {
            Path targetPath = viewModel.resolvePath(ref.file());
            if (targetPath != null && Files.exists(targetPath)) {
                try {
                    EditorDocumentViewModel doc = viewModel.getEditorArea().openFile(targetPath);
                    if (ref.line() > 0) {
                        doc.getEditor().goToLine(ref.line());
                        doc.getEditor().requestFocus();
                    }
                } catch (IOException ignored) {
                }
            }
        });

        // Breakpoint double-click in debugger panel -> open file and jump to line
        bottomPanelView.getDebuggerView().setOnBreakpointSelected(bp -> {
            Path targetPath = viewModel.resolvePath(bp.getPath());
            if (targetPath != null && Files.exists(targetPath)) {
                try {
                    EditorDocumentViewModel doc = viewModel.getEditorArea().openFile(targetPath);
                    if (bp.getLine() > 0) {
                        doc.getEditor().goToLine(bp.getLine());
                        doc.getEditor().requestFocus();
                    }
                } catch (IOException ignored) {
                }
            }
        });

        // Sync outline when active document changes or is saved
        // One listener follows the active document only; it moves when the active tab changes.
        javafx.beans.value.ChangeListener<Boolean> savedListener = (o, wasModified, modified) -> {
            if (!modified) {
                updateOutline(viewModel.getEditorArea().getActiveDocument());
            }
        };
        viewModel.getEditorArea().activeDocumentProperty().addListener((obs, oldDoc, newDoc) -> {
            if (oldDoc != null) {
                oldDoc.getEditor().modifiedProperty().removeListener(savedListener);
            }
            if (newDoc != null) {
                updateOutline(newDoc);
                newDoc.getEditor().modifiedProperty().addListener(savedListener);
                // The explorer follows the active editor (VS Code's "auto reveal").
                explorerView.revealPath(newDoc.getFilePath());
            } else {
                outlineView.updateOutline(List.of());
            }
        });

        if (viewModel.getEditorArea().getActiveDocument() != null) {
            updateOutline(viewModel.getEditorArea().getActiveDocument());
        }

        // Problem double-click -> open file and jump to line
        bottomPanelView.setOnProblemSelected(diagnostic -> {
            String filePathStr = diagnostic.getFile();
            if (filePathStr == null || filePathStr.isBlank()) {
                return;
            }

            Path targetPath = Path.of(filePathStr);
            if (!Files.exists(targetPath) && viewModel.getExplorer().getProjectRoot() != null) {
                Path resolved = viewModel.getExplorer().getProjectRoot().resolve(filePathStr);
                if (Files.exists(resolved)) {
                    targetPath = resolved;
                }
            }

            if (Files.exists(targetPath)) {
                try {
                    EditorDocumentViewModel doc = viewModel.getEditorArea().openFile(targetPath);
                    if (diagnostic.getLine() > 0) {
                        doc.getEditor().goToLine(diagnostic.getLine());
                    }
                } catch (IOException ignored) {
                }
            }
        });
    }

    private void updateOutline(EditorDocumentViewModel doc) {
        if (doc == null) {
            outlineView.updateOutline(List.of());
            return;
        }
        var items = viewModel.getOutline(doc.getEditor().getText());
        outlineView.updateOutline(items);
    }

    public void openCommandPalette() {
        Window window = getScene() != null ? getScene().getWindow() : null;
        String catDebug = localization.get("menu.run");
        String catBuild = localization.get("menu.project");
        String catFile = localization.get("menu.file");
        String catView = localization.get("menu.view");
        String catHelp = localization.get("menu.help");

        List<CommandPaletteDialog.CommandEntry> commands = new java.util.ArrayList<>(List.of(
                new CommandPaletteDialog.CommandEntry(localization.get("recent.title"), "Ctrl+R", menuBar::openRecentDialog),
                new CommandPaletteDialog.CommandEntry(catDebug, localization.get("action.debug"), "F5", () -> viewModel.debug()),
                new CommandPaletteDialog.CommandEntry(catDebug, localization.get("action.run"), "Ctrl+F5", () -> viewModel.run(false)),
                new CommandPaletteDialog.CommandEntry(catDebug, localization.get("menu.run.runKeepOpen"), "", () -> viewModel.run(true)),
                new CommandPaletteDialog.CommandEntry(catDebug, localization.get("action.toggleBreakpoint"), "F9", () -> viewModel.toggleBreakpointAtCaret()),
                new CommandPaletteDialog.CommandEntry(catDebug, localization.get("action.clearBreakpoints"), "", () -> viewModel.clearAllBreakpoints()),
                new CommandPaletteDialog.CommandEntry(catBuild, localization.get("action.build"), "F7", () -> viewModel.build()),
                new CommandPaletteDialog.CommandEntry(catBuild, localization.get("action.cleanAndBuild"), "Shift+F7", () -> viewModel.cleanAndBuild()),
                new CommandPaletteDialog.CommandEntry(catBuild, localization.get("action.clean"), "", () -> viewModel.clean()),
                new CommandPaletteDialog.CommandEntry(catBuild, localization.get("action.package"), "", () -> viewModel.packageDist()),
                new CommandPaletteDialog.CommandEntry(catDebug, localization.get("action.stop"), "Shift+F5", () -> viewModel.stop()),
                new CommandPaletteDialog.CommandEntry(catFile, localization.get("explorer.newFile"), "Ctrl+N", explorerView::newFile),
                new CommandPaletteDialog.CommandEntry(catFile, localization.get("explorer.newFolder"), "", explorerView::newFolder),
                new CommandPaletteDialog.CommandEntry(catView, localization.get("explorer.refresh"), "", viewModel.getExplorer()::refresh),
                new CommandPaletteDialog.CommandEntry(catView, localization.get("explorer.collapseAll"), "", explorerView::collapseAll),
                new CommandPaletteDialog.CommandEntry(catFile, localization.get("action.newProject"), "Ctrl+Shift+N", () -> new NewProjectDialog(window, viewModel, localization).showAndWait()),
                new CommandPaletteDialog.CommandEntry(catFile, localization.get("menu.file.openFile"), "Ctrl+O", menuBar::openFileDialog),
                new CommandPaletteDialog.CommandEntry(catFile, localization.get("menu.file.saveAs"), "Ctrl+Shift+S", menuBar::saveAsDialog),
                new CommandPaletteDialog.CommandEntry(catFile, localization.get("menu.file.openProject"), "Ctrl+K Ctrl+O", menuBar::openProjectDialog),
                new CommandPaletteDialog.CommandEntry(catFile, localization.get("action.importProject"), "", menuBar::importProjectDialog),
                new CommandPaletteDialog.CommandEntry(catFile, localization.get("menu.file.save"), "Ctrl+S", () -> {
                    try {
                        viewModel.getEditorArea().saveActive();
                    } catch (IOException ignored) {
                    }
                }),
                new CommandPaletteDialog.CommandEntry(catFile, localization.get("menu.file.saveAll"), "Ctrl+K S", () -> {
                    try {
                        viewModel.getEditorArea().saveAll();
                    } catch (IOException ignored) {
                    }
                }),
                new CommandPaletteDialog.CommandEntry(catView, localization.get("menu.view.terminal"), "Ctrl+`", viewModel::openTerminal),
                new CommandPaletteDialog.CommandEntry(catHelp, localization.get("action.projectProperties"), "", () -> new ProjectPropertiesDialog(window, viewModel, localization).showAndWait()),
                new CommandPaletteDialog.CommandEntry(catHelp, localization.get("action.doctor"), "", () -> new DoctorDialog(window, localization, viewModel.getToolRegistry()).showAndWait()),
                new CommandPaletteDialog.CommandEntry(catView, localization.get("action.toggleTheme"), "", () -> {
                    toggleTheme();
                }),
                new CommandPaletteDialog.CommandEntry(catView, localization.get("action.switchLanguage"), "", () -> {
                    Locale current = localization.localeProperty().get();
                    localization.localeProperty().set(Localization.SPANISH.equals(current)
                            ? Localization.ENGLISH
                            : Localization.SPANISH);
                }),
                new CommandPaletteDialog.CommandEntry(catHelp, localization.get("menu.help.about"), "", () -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle(IdearmInfo.NAME);
                    alert.setHeaderText(IdearmInfo.NAME + " v" + IdearmInfo.version());
                    alert.setContentText(localization.get("dialog.about.content"));
                    alert.showAndWait();
                })
        ));
        for (var command : io.github.dinamo541.idearm.app.editor.EditorCommand.values()) {
            commands.add(new CommandPaletteDialog.CommandEntry(localization.get("menu.edit"), localization.get(command.key()),
                    command.shortcut().getDisplayText(), () -> edit(command)));
        }
        commands.add(new CommandPaletteDialog.CommandEntry(catView, localization.get("workbench.sidebar"), "Ctrl+B", this::toggleSidebar));
        commands.add(new CommandPaletteDialog.CommandEntry(catView, localization.get("workbench.panel"), "Ctrl+J", this::togglePanel));
        new CommandPaletteDialog(window, localization, commands).show();
    }

    public ProjectExplorerView getExplorerView() {
        return explorerView;
    }

    public WorkbenchViewModel getViewModel() {
        return viewModel;
    }

    public Localization getLocalization() {
        return localization;
    }
}
