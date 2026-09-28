package io.github.dinamo541.idearm.app.view;

import atlantafx.base.theme.Styles;
import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.i18n.Problem;
import io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel;
import io.github.dinamo541.idearm.app.viewmodel.DiagnosticItemViewModel;
import io.github.dinamo541.idearm.app.viewmodel.ReferenceItemViewModel;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Side;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;

/**
 * Bottom tabbed panel hosting Problems table, Build console, Output console, and References table.
 */
public final class BottomPanelView extends TabPane {

    private final BottomPanelViewModel viewModel;
    private final Localization localization;
    private final TableView<DiagnosticItemViewModel> problemsTable;
    private final TableView<ReferenceItemViewModel> referencesTable;
    private final DebuggerPanelView debuggerView;
    private final TerminalPanelView terminalView;
    private final TextArea buildArea;
    private final TextArea outputArea;
    private Consumer<DiagnosticItemViewModel> onProblemSelected;
    private Consumer<ReferenceItemViewModel> onReferenceSelected;

    public BottomPanelView(BottomPanelViewModel viewModel, Localization localization) {
        this.viewModel = viewModel;
        this.localization = localization;

        setSide(Side.TOP);
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        getStyleClass().addAll(Styles.DENSE, "bottom-panel");

        // 1. Problems Tab
        this.problemsTable = new TableView<>();
        this.problemsTable.setItems(viewModel.getProblems());
        this.problemsTable.setPlaceholder(emptyState("panel.problems.empty", "panel.problems.emptyHint",
                io.github.dinamo541.idearm.app.ui.WorkbenchIcons.CHECK));
        setupProblemsTable();

        var problemsTab = new Tab();
        problemsTab.textProperty().bind(Bindings.createStringBinding(() -> {
            int count = viewModel.getProblems().size();
            return localization.get("panel.problems.title") + (count > 0 ? " (" + count + ")" : "");
        }, viewModel.getProblems(), localization.localeProperty()));
        problemsTab.setContent(problemsTable);

        // 2. Build Tab
        this.buildArea = new TextArea();
        this.buildArea.setEditable(false);
        this.buildArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");
        this.buildArea.textProperty().bind(viewModel.buildTextProperty());
        this.buildArea.textProperty().addListener((obs, oldV, newV) -> buildArea.setScrollTop(Double.MAX_VALUE));

        var buildTab = new Tab();
        buildTab.textProperty().bind(localization.text("panel.build.title"));
        buildTab.setContent(console(buildArea, "panel.build.empty", "panel.build.emptyHint",
                io.github.dinamo541.idearm.app.ui.WorkbenchIcons.BUILD));

        // 3. Output Tab
        this.outputArea = new TextArea();
        this.outputArea.setEditable(false);
        this.outputArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");
        this.outputArea.textProperty().bind(viewModel.outputTextProperty());
        this.outputArea.textProperty().addListener((obs, oldV, newV) -> outputArea.setScrollTop(Double.MAX_VALUE));

        var outputTab = new Tab();
        outputTab.textProperty().bind(localization.text("panel.output.title"));
        var outputPane = new javafx.scene.layout.BorderPane(console(outputArea, "panel.output.empty", "panel.output.emptyHint",
                io.github.dinamo541.idearm.app.ui.WorkbenchIcons.RUN));
        outputPane.setBottom(createProgramInput());
        outputTab.setContent(outputPane);

        // 4. References Tab
        this.referencesTable = new TableView<>();
        this.referencesTable.setItems(viewModel.getReferences());
        this.referencesTable.setPlaceholder(emptyState("panel.references.empty", "panel.references.emptyHint",
                io.github.dinamo541.idearm.app.ui.WorkbenchIcons.REFERENCES));
        setupReferencesTable();

        var referencesTab = new Tab();
        referencesTab.textProperty().bind(Bindings.createStringBinding(() -> {
            int count = viewModel.getReferences().size();
            return localization.get("panel.references.title") + (count > 0 ? " (" + count + ")" : "");
        }, viewModel.getReferences(), localization.localeProperty()));
        referencesTab.setContent(referencesTable);

        // 5. Debug Tab
        this.debuggerView = new DebuggerPanelView(viewModel.getDebugViewModel(), localization);
        var debugTab = new Tab();
        debugTab.textProperty().bind(localization.text("panel.debug.title"));
        debugTab.setContent(debuggerView);

        // 6. Terminal Tab
        this.terminalView = new TerminalPanelView(viewModel.getTerminalViewModel(), localization);
        var terminalTab = new Tab();
        terminalTab.textProperty().bind(localization.text("panel.terminal.title"));
        terminalTab.setContent(terminalView);

        getTabs().addAll(problemsTab, buildTab, outputTab, referencesTab, debugTab, terminalTab);
        problemsTab.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.PROBLEMS.create());
        buildTab.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.BUILD.create());
        outputTab.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.RUN.create());
        referencesTab.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.REFERENCES.create());
        debugTab.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.DEBUG.create());
        terminalTab.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.TERMINAL.create());

        // Sync active tab selection
        getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == problemsTab) {
                viewModel.setActiveTab(BottomPanelViewModel.BottomTab.PROBLEMS);
            } else if (newTab == buildTab) {
                viewModel.setActiveTab(BottomPanelViewModel.BottomTab.BUILD);
            } else if (newTab == outputTab) {
                viewModel.setActiveTab(BottomPanelViewModel.BottomTab.OUTPUT);
            } else if (newTab == referencesTab) {
                viewModel.setActiveTab(BottomPanelViewModel.BottomTab.REFERENCES);
            } else if (newTab == debugTab) {
                viewModel.setActiveTab(BottomPanelViewModel.BottomTab.DEBUG);
            } else if (newTab == terminalTab) {
                viewModel.setActiveTab(BottomPanelViewModel.BottomTab.TERMINAL);
            }
        });

        viewModel.activeTabProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                switch (newTab) {
                    case PROBLEMS -> getSelectionModel().select(problemsTab);
                    case BUILD -> getSelectionModel().select(buildTab);
                    case OUTPUT -> getSelectionModel().select(outputTab);
                    case REFERENCES -> getSelectionModel().select(referencesTab);
                    case DEBUG -> getSelectionModel().select(debugTab);
                    case TERMINAL -> getSelectionModel().select(terminalTab);
                }
            }
        });
    }

    /**
     * The keyboard of the running program. In the built-in emulator every key goes to the program at once, as on a
     * real PC, and the program decides what to echo; for a program reading a pipe the line is sent on Enter and
     * shown here. The row only appears while a program can read it.
     */
    private javafx.scene.Node createProgramInput() {
        var field = new javafx.scene.control.TextField();
        field.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");
        field.promptTextProperty().bind(Bindings.createStringBinding(() ->
                        viewModel.programInputProperty().get() == BottomPanelViewModel.ProgramInput.LINE
                                ? localization.get("output.programInput.line")
                                : localization.get("output.programInput.keys"),
                viewModel.programInputProperty(), localization.localeProperty()));
        javafx.scene.layout.HBox.setHgrow(field, javafx.scene.layout.Priority.ALWAYS);

        field.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            var mode = viewModel.programInputProperty().get();
            if (mode == BottomPanelViewModel.ProgramInput.KEYS) {
                switch (event.getCode()) {
                    case ENTER -> { viewModel.sendProgramInput("\r"); event.consume(); }
                    case BACK_SPACE -> { viewModel.sendProgramInput("\b"); event.consume(); }
                    case ESCAPE -> { viewModel.sendProgramInput(""); event.consume(); }
                    default -> { }
                }
            } else if (mode == BottomPanelViewModel.ProgramInput.LINE
                    && event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                String line = field.getText();
                viewModel.appendOutputText(line + "\n");
                viewModel.sendProgramInput(line + "\n");
                field.clear();
                event.consume();
            }
        });
        field.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, event -> {
            if (viewModel.programInputProperty().get() != BottomPanelViewModel.ProgramInput.KEYS) {
                return;
            }
            String typed = event.getCharacter();
            // Enter, Backspace and Escape were sent on key press; only printable keys remain.
            if (typed != null && !typed.isEmpty() && typed.charAt(0) >= ' ' && typed.charAt(0) != 127) {
                viewModel.sendProgramInput(typed);
            }
            event.consume();
        });

        var prompt = new javafx.scene.control.Label(null,
                io.github.dinamo541.idearm.app.ui.WorkbenchIcons.RIGHT.create(14));
        prompt.getStyleClass().add("terminal-prompt");
        var row = new javafx.scene.layout.HBox(6, prompt, field);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.getStyleClass().add("console-input");
        var visible = viewModel.programInputProperty().isNotEqualTo(BottomPanelViewModel.ProgramInput.NONE);
        row.visibleProperty().bind(visible);
        row.managedProperty().bind(visible);
        // Typing goes straight to the program the moment it starts reading the keyboard.
        viewModel.programInputProperty().addListener((obs, before, after) -> {
            if (after != BottomPanelViewModel.ProgramInput.NONE) {
                javafx.application.Platform.runLater(field::requestFocus);
            }
        });
        return row;
    }

    private javafx.scene.Node emptyState(String titleKey, String hintKey,
                                          io.github.dinamo541.idearm.app.ui.WorkbenchIcons icon) {
        var title = new javafx.scene.control.Label();
        title.textProperty().bind(localization.text(titleKey));
        var hint = new javafx.scene.control.Label();
        hint.textProperty().bind(localization.text(hintKey));
        hint.getStyleClass().add("empty-description");
        hint.setWrapText(true);
        hint.setMaxWidth(420);
        var box = new javafx.scene.layout.VBox(icon.create(24), title, hint);
        box.getStyleClass().add("panel-empty");
        box.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return box;
    }

    private javafx.scene.Node console(TextArea area, String titleKey, String hintKey,
                                       io.github.dinamo541.idearm.app.ui.WorkbenchIcons icon) {
        var empty = emptyState(titleKey, hintKey, icon);
        empty.visibleProperty().bind(area.textProperty().isEmpty());
        empty.managedProperty().bind(empty.visibleProperty());
        empty.setMouseTransparent(true);
        return new javafx.scene.layout.StackPane(area, empty);
    }

    public TerminalPanelView getTerminalView() {
        return terminalView;
    }

    public DebuggerPanelView getDebuggerView() {
        return debuggerView;
    }

    public void setOnProblemSelected(Consumer<DiagnosticItemViewModel> listener) {
        this.onProblemSelected = listener;
    }

    public void setOnReferenceSelected(Consumer<ReferenceItemViewModel> listener) {
        this.onReferenceSelected = listener;
    }

    private void setupProblemsTable() {
        problemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        var severityCol = new TableColumn<DiagnosticItemViewModel, Severity>();
        severityCol.textProperty().bind(localization.text("panel.problems.severity"));
        severityCol.setCellValueFactory(cell -> cell.getValue().severityProperty());
        severityCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Severity item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(localization.get("severity." + item.name().toLowerCase(java.util.Locale.ROOT)));
                    if (item == Severity.ERROR) {
                        setStyle("-fx-text-fill: -color-danger-fg; -fx-font-weight: bold;");
                    } else if (item == Severity.WARNING) {
                        setStyle("-fx-text-fill: -color-warning-fg;");
                    } else {
                        setStyle("-fx-text-fill: -color-accent-fg;");
                    }
                }
            }
        });
        severityCol.setMaxWidth(100);

        var fileCol = new TableColumn<DiagnosticItemViewModel, String>();
        fileCol.textProperty().bind(localization.text("panel.problems.file"));
        fileCol.setCellValueFactory(cell -> cell.getValue().fileProperty());
        fileCol.setMaxWidth(200);

        var lineCol = new TableColumn<DiagnosticItemViewModel, Number>();
        lineCol.textProperty().bind(localization.text("panel.problems.line"));
        lineCol.setCellValueFactory(cell -> cell.getValue().lineProperty());
        lineCol.setMaxWidth(70);

        var codeCol = new TableColumn<DiagnosticItemViewModel, String>();
        codeCol.textProperty().bind(localization.text("panel.problems.code"));
        codeCol.setCellValueFactory(cell -> cell.getValue().codeProperty());
        codeCol.setMaxWidth(120);

        var msgCol = new TableColumn<DiagnosticItemViewModel, String>();
        msgCol.textProperty().bind(localization.text("panel.problems.message"));
        // Problems the IDE reports follow the UI language; tool output keeps the tool's own words.
        msgCol.setCellValueFactory(cell -> localization.text(Problem.of(cell.getValue().getDiagnostic())));
        msgCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                setText(empty ? null : text);
                DiagnosticItemViewModel item = empty ? null : getTableRow().getItem();
                // The original English text stays reachable, for searching it online.
                setTooltip(item == null || item.getMessage().equals(text) ? null : new Tooltip(item.getMessage()));
            }
        });

        problemsTable.getColumns().add(severityCol);
        problemsTable.getColumns().add(fileCol);
        problemsTable.getColumns().add(lineCol);
        problemsTable.getColumns().add(codeCol);
        problemsTable.getColumns().add(msgCol);

        // Row double click
        problemsTable.setRowFactory(tv -> {
            TableRow<DiagnosticItemViewModel> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    DiagnosticItemViewModel item = row.getItem();
                    if (onProblemSelected != null && item != null) {
                        onProblemSelected.accept(item);
                    }
                }
            });
            return row;
        });
    }

    private void setupReferencesTable() {
        referencesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        var fileCol = new TableColumn<ReferenceItemViewModel, String>();
        fileCol.textProperty().bind(localization.text("panel.references.file"));
        fileCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().file()));
        fileCol.setMaxWidth(220);

        var lineCol = new TableColumn<ReferenceItemViewModel, Number>();
        lineCol.textProperty().bind(localization.text("panel.references.line"));
        lineCol.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().line()));
        lineCol.setMaxWidth(80);

        var previewCol = new TableColumn<ReferenceItemViewModel, String>();
        previewCol.textProperty().bind(localization.text("panel.references.preview"));
        previewCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().preview()));
        previewCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px;");
                }
            }
        });

        referencesTable.getColumns().add(fileCol);
        referencesTable.getColumns().add(lineCol);
        referencesTable.getColumns().add(previewCol);

        // Row double click
        referencesTable.setRowFactory(tv -> {
            TableRow<ReferenceItemViewModel> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    ReferenceItemViewModel item = row.getItem();
                    if (onReferenceSelected != null && item != null) {
                        onReferenceSelected.accept(item);
                    }
                }
            });
            return row;
        });
    }
}
