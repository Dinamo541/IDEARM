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
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

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
    private double sidebarPosition = 0.20;
    private double panelPosition = 0.73;
    private MnemonicsDictionaryDialog activeDictionaryDialog;

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
        menuBar.setOnDictionaryRequested(this::openDictionary);
        menuBar.setOnNewFileRequested(explorerView::newFile);
        menuBar.setOnNewFolderRequested(explorerView::newFolder);
        explorerView.setOnOpenFolderRequested(menuBar::openProjectDialog);
        explorerView.setOnStatus(viewModel.getStatusBar()::setStatus);
        toolBar.setOnCommandPaletteRequested(this::openCommandPalette);
        toolBar.setOnRecentRequested(menuBar::openRecentDialog);

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                var shiftF1 = new KeyCodeCombination(KeyCode.F1, KeyCombination.SHIFT_DOWN);
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (shiftF1.match(event)) {
                        openDictionary();
                        event.consume();
                    }
                });
            }
        });

        getStyleClass().addAll("workbench", "workbench-shell");
        editorAreaView.setOnWelcomeAction(action -> {
            switch (action) {
                case NEW_PROJECT -> new NewProjectDialog(getScene().getWindow(), viewModel, localization).showAndWait();
                case OPEN_PROJECT -> menuBar.openProjectDialog();
                case RECENT -> menuBar.openRecentDialog();
                case COMMANDS -> openCommandPalette();
                case DICTIONARY -> openDictionary();
            }
        });
        var debugging = javafx.css.PseudoClass.getPseudoClass("debugging");
        viewModel.getBottomPanel().getDebugViewModel().activeProperty().addListener((obs, before, active) ->
                statusBarView.pseudoClassStateChanged(debugging, active));
        getStylesheets().add(java.util.Objects.requireNonNull(getClass().getResource("workbench.css")).toExternalForm());
        toolBar.setOnThemeRequested(this::toggleTheme);
        var logo = io.github.dinamo541.idearm.app.ui.BrandLogo.create(25);
        var logoBox = new javafx.scene.layout.StackPane(logo);
        logoBox.getStyleClass().add("title-logo");
        logoBox.setMinWidth(41);
        logoBox.setPrefWidth(41);
        var header = new javafx.scene.layout.HBox(logoBox, menuBar, toolBar);
        windowHeader = header;
        // Keep menu names readable at the minimum window size; ToolBar already has an overflow menu.
        menuBar.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        toolBar.setMinWidth(90);
        header.getStyleClass().add("workbench-header");
        javafx.scene.layout.HBox.setHgrow(toolBar, javafx.scene.layout.Priority.ALWAYS);
        setTop(header);

        // Center: Horizontal SplitPane ([Explorer / Outline] | [Editor / BottomPanel])
        centerVerticalSplit = new SplitPane(editorAreaView, bottomPanelView);
        centerVerticalSplit.setOrientation(Orientation.VERTICAL);
        centerVerticalSplit.setDividerPositions(panelPosition);
        editorAreaView.setMinHeight(140);
        bottomPanelView.setMinHeight(150);
        SplitPane.setResizableWithParent(bottomPanelView, false);

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
        setLeft(createActivityBar());
        menuBar.minimapVisibleProperty().bindBidirectional(editorAreaView.minimapVisibleProperty());

        // Bottom: Status Bar
        setBottom(statusBarView);

        // Wiring interactions
        setupInteractions();

        viewModel.getBottomPanel().activeTabProperty().addListener((o, before, after) -> {
            boolean debuggingPanel = after == io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel.BottomTab.DEBUG;
            bottomPanelView.setMinHeight(debuggingPanel ? 270 : 150);
            showPanel();
            if (debuggingPanel && centerVerticalSplit.getHeight() > 0) {
                double position = Math.min(centerVerticalSplit.getDividerPositions()[0],
                        1 - 310 / centerVerticalSplit.getHeight());
                centerVerticalSplit.setDividerPositions(Math.max(0.3, position));
            }
        });
        addEventFilter(KeyEvent.KEY_PRESSED, this::workbenchKeys);

        // Global hotkeys (Ctrl+Shift+P, F1 for Command Palette; Shift+F1 for Academic Dictionary)
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if ((event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.P)
                    || (!event.isShiftDown() && event.getCode() == KeyCode.F1)) {
                openCommandPalette();
                event.consume();
            } else if (event.isShiftDown() && event.getCode() == KeyCode.F1) {
                openDictionary();
                event.consume();
            }
        });
    }

    public void installWindowChrome(javafx.stage.Stage stage) {
        menuBar.bindWindow(stage);
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
        var explorer = activity(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.EXPLORER, "explorer.title", this::toggleSidebar);
        var debug = activity(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.DEBUG, "panel.debug.title", () -> selectPanel(io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel.BottomTab.DEBUG));
        var terminal = activity(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.TERMINAL, "panel.terminal.title", () -> { showPanel(); viewModel.openTerminal(); });
        var selected = javafx.css.PseudoClass.getPseudoClass("selected");
        Runnable updateSelection = () -> {
            explorer.pseudoClassStateChanged(selected, mainHorizontalSplit.getItems().contains(sidebarVerticalSplit));
            boolean panel = centerVerticalSplit.getItems().contains(bottomPanelView);
            var tab = viewModel.getBottomPanel().activeTabProperty().get();
            debug.pseudoClassStateChanged(selected, panel && tab == io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel.BottomTab.DEBUG);
            terminal.pseudoClassStateChanged(selected, panel && tab == io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel.BottomTab.TERMINAL);
        };
        mainHorizontalSplit.getItems().addListener((javafx.beans.InvalidationListener) obs -> updateSelection.run());
        centerVerticalSplit.getItems().addListener((javafx.beans.InvalidationListener) obs -> updateSelection.run());
        viewModel.getBottomPanel().activeTabProperty().addListener(obs -> updateSelection.run());
        updateSelection.run();
        bar.getChildren().addAll(explorer,
                activity(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.SEARCH, "editor.command.find", () -> edit(io.github.dinamo541.idearm.app.editor.EditorCommand.FIND)),
                debug, terminal,
                activity(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.BOOK, "action.dictionary", this::openDictionary));
        var space = new javafx.scene.layout.Region();
        VBox.setVgrow(space, javafx.scene.layout.Priority.ALWAYS);
        bar.getChildren().add(space);
        bar.getChildren().add(activity(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.SETTINGS, "action.commandPalette", this::openCommandPalette));
        return bar;
    }
    private javafx.scene.control.Button activity(io.github.dinamo541.idearm.app.ui.WorkbenchIcons icon, String key, Runnable action) {
        var button = new javafx.scene.control.Button(); button.setGraphic(icon.create(21));
        button.accessibleTextProperty().bind(localization.text(key));
        button.getStyleClass().add("activity-button");
        String help = switch (key) {
            case "explorer.title" -> "help.explorer";
            case "editor.command.find" -> "help.find";
            case "panel.debug.title" -> "help.debugPanel";
            case "panel.terminal.title" -> "help.terminal";
            case "action.dictionary" -> "help.dictionary";
            default -> "help.commands";
        };
        io.github.dinamo541.idearm.app.ui.HoverHelp.install(button, localization, help, "");
        button.setOnAction(e -> action.run()); return button;
    }
    public void openDictionary() {
        openDictionary(null);
    }

    public void openDictionary(String mnemonic) {
        if (activeDictionaryDialog != null && activeDictionaryDialog.isShowing()) {
            activeDictionaryDialog.setIconified(false);
            activeDictionaryDialog.toFront();
            activeDictionaryDialog.requestFocus();
            if (mnemonic != null && !mnemonic.isBlank()) {
                activeDictionaryDialog.selectMnemonic(mnemonic);
            }
            return;
        }
        Window window = getScene() != null ? getScene().getWindow() : null;
        activeDictionaryDialog = new MnemonicsDictionaryDialog(window, localization);
        // The view model reports a failure in the status bar and the Build Log.
        activeDictionaryDialog.setExampleProjectOpener(viewModel::instantiateExampleProject);
        if (mnemonic != null && !mnemonic.isBlank()) {
            activeDictionaryDialog.selectMnemonic(mnemonic);
        }
        activeDictionaryDialog.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> activeDictionaryDialog = null);
        activeDictionaryDialog.show();
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
        if (e.getCode() == KeyCode.F11 && !e.isControlDown() && !e.isShiftDown() && !e.isAltDown() && !e.isMetaDown()
                && !viewModel.getBottomPanel().getDebugViewModel().activeProperty().get()) {
            if (getScene().getWindow() instanceof javafx.stage.Stage stage) io.github.dinamo541.idearm.app.ui.WindowChrome.toggleFullScreen(stage);
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

        // Call-stack double-click -> open the source line that frame stands on
        bottomPanelView.getDebuggerView().setOnCallFrameSelected(frame -> openAt(frame.getFile(), frame.getLine()));
        bottomPanelView.getDebuggerView().setOnRestartRequested(viewModel::restartDebug);

        // Breakpoint double-click in debugger panel -> open file and jump to line
        bottomPanelView.getDebuggerView().setOnBreakpointSelected(bp -> openAt(bp.getPath(), bp.getLine()));

        // Sync outline when active document changes or is saved (call-stack and breakpoint navigation share openAt)
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

    /**
     * Opens a project-relative or absolute path and puts the caret on a line. Shared by the breakpoint list and the
     * call stack: both point at a place in the source without moving where the program is.
     */
    private void openAt(String path, int line) {
        Path targetPath = viewModel.resolvePath(path);
        if (targetPath == null || !Files.exists(targetPath)) {
            return;
        }
        try {
            EditorDocumentViewModel doc = viewModel.getEditorArea().openFile(targetPath);
            if (line > 0) {
                doc.getEditor().goToLine(line);
                doc.getEditor().requestFocus();
            }
        } catch (IOException unreadable) {
            // The file cannot be opened right now; the list keeps its selection.
        }
    }

    /** F5 and the Debug command: start a session, or continue the one that is paused. */
    private void debugOrResume() {
        if (viewModel.getBottomPanel().getDebugViewModel().isPaused()) {
            viewModel.resumeDebug();
        } else {
            viewModel.debug();
        }
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
                // F5 starts a session or continues a paused one, as the menu and the toolbar button do.
                new CommandPaletteDialog.CommandEntry(catDebug, localization.get("action.debug"), "F5", this::debugOrResume),
                new CommandPaletteDialog.CommandEntry(catDebug, localization.get("menu.run.restartDebug"), "Ctrl+Shift+F5", () -> viewModel.restartDebug()),
                new CommandPaletteDialog.CommandEntry(catDebug, localization.get("action.resume"), "F5", () -> viewModel.resumeDebug()),
                new CommandPaletteDialog.CommandEntry(catDebug, localization.get("action.pause"), "F6", () -> viewModel.pauseDebug()),
                new CommandPaletteDialog.CommandEntry(catDebug, localization.get("action.stepOver"), "F10", () -> viewModel.stepOver()),
                new CommandPaletteDialog.CommandEntry(catDebug, localization.get("action.stepInto"), "F11", () -> viewModel.stepInto()),
                new CommandPaletteDialog.CommandEntry(catDebug, localization.get("action.stepOut"), "Shift+F11", () -> viewModel.stepOut()),
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
                new CommandPaletteDialog.CommandEntry(catHelp, localization.get("action.dictionary"), "Shift+F1", this::openDictionary),
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
                    alert.initOwner(window);
                    alert.setGraphic(io.github.dinamo541.idearm.app.ui.BrandLogo.create(48));
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
        commands.add(new CommandPaletteDialog.CommandEntry(catView, localization.get("menu.view.fullScreen"), "Ctrl+Alt+F11",
                () -> io.github.dinamo541.idearm.app.ui.WindowChrome.toggleFullScreen((javafx.stage.Stage) window)));
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
