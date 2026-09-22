package io.github.dinamo541.idearm.app.view;

import atlantafx.base.theme.Styles;
import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.viewmodel.BreakpointItemViewModel;
import io.github.dinamo541.idearm.app.viewmodel.DebugViewModel;
import io.github.dinamo541.idearm.app.viewmodel.RegisterItemViewModel;
import io.github.dinamo541.idearm.app.viewmodel.WatchItemViewModel;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;

import java.util.function.Consumer;

/**
 * Visual debugger panel hosting CPU registers, condition code flags,
 * memory hex dump, stack inspection, and project breakpoints.
 */
public final class DebuggerPanelView extends SplitPane {

    private final DebugViewModel viewModel;
    private final Localization localization;
    private Consumer<BreakpointItemViewModel> onBreakpointSelected;

    public DebuggerPanelView(DebugViewModel viewModel, Localization localization) {
        this.viewModel = viewModel;
        this.localization = localization;

        getStyleClass().add(Styles.DENSE);
        setOrientation(javafx.geometry.Orientation.HORIZONTAL);

        // Panel 1: Registers & Flags (Left)
        VBox registersBox = createRegistersBox();

        // Panel 2: Memory & Stack (Center)
        TabPane centerTabs = createCenterTabPane();

        // Panel 3: Breakpoints & Watches (Right)
        TabPane rightTabs = createRightTabPane();

        getItems().addAll(registersBox, centerTabs, rightTabs);
        setDividerPositions(0.36, 0.68);
    }

    private VBox createRegistersBox() {
        Label title = new Label();
        title.textProperty().bind(localization.text("debugger.registers.title"));
        title.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_MUTED);

        var resumeBtn = new Button();
        resumeBtn.textProperty().bind(localization.text("action.resume"));
        io.github.dinamo541.idearm.app.ui.HoverHelp.install(resumeBtn, localization, "tooltip.resume", "");
        resumeBtn.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.RUN.create());
        resumeBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS, Styles.SMALL);
        resumeBtn.setOnAction(e -> viewModel.resume());
        resumeBtn.disableProperty().bind(viewModel.pausedProperty().not());

        var stepOverBtn = new Button();
        stepOverBtn.textProperty().bind(localization.text("action.stepOver"));
        io.github.dinamo541.idearm.app.ui.HoverHelp.install(stepOverBtn, localization, "tooltip.stepOver", "");
        stepOverBtn.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.STEP_OVER.create());
        stepOverBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        stepOverBtn.setOnAction(e -> viewModel.stepOver());
        stepOverBtn.disableProperty().bind(viewModel.pausedProperty().not());

        var stepIntoBtn = new Button();
        stepIntoBtn.textProperty().bind(localization.text("action.stepInto"));
        io.github.dinamo541.idearm.app.ui.HoverHelp.install(stepIntoBtn, localization, "tooltip.stepInto", "");
        stepIntoBtn.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.STEP_INTO.create());
        stepIntoBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        stepIntoBtn.setOnAction(e -> viewModel.stepInto());
        stepIntoBtn.disableProperty().bind(viewModel.pausedProperty().not());

        var stepOutBtn = new Button();
        stepOutBtn.textProperty().bind(localization.text("action.stepOut"));
        io.github.dinamo541.idearm.app.ui.HoverHelp.install(stepOutBtn, localization, "tooltip.stepOut", "");
        stepOutBtn.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.STEP_OUT.create());
        stepOutBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        stepOutBtn.setOnAction(e -> viewModel.stepOut());
        stepOutBtn.disableProperty().bind(viewModel.pausedProperty().not());

        var stopBtn = new Button();
        stopBtn.textProperty().bind(localization.text("action.stop"));
        io.github.dinamo541.idearm.app.ui.HoverHelp.install(stopBtn, localization, "tooltip.stopDebug", "");
        stopBtn.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.STOP.create());
        stopBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER, Styles.SMALL);
        stopBtn.setOnAction(e -> viewModel.stop());
        stopBtn.disableProperty().bind(viewModel.activeProperty().not());

        HBox controlsBar = new HBox(4, resumeBtn, stepOverBtn, stepIntoBtn, stepOutBtn, stopBtn);
        controlsBar.setAlignment(Pos.CENTER_LEFT);

        TableView<RegisterItemViewModel> regTable = new TableView<>(viewModel.getRegisterList());
        regTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        regTable.getStyleClass().add(Styles.STRIPED);
        // Digits line up in a monospaced font; a 64-bit value needs its 16 hex digits.
        regTable.setStyle("-fx-font-family: 'JetBrains Mono', 'Consolas', monospace;");

        TableColumn<RegisterItemViewModel, String> nameCol = new TableColumn<>();
        nameCol.textProperty().bind(localization.text("debugger.registers.name"));
        nameCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::getName));
        nameCol.setPrefWidth(60);

        javafx.util.Callback<TableColumn<RegisterItemViewModel, String>, TableCell<RegisterItemViewModel, String>> regCellFactory =
                col -> new TableCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setStyle("");
                        } else {
                            setText(item);
                            var reg = getTableRow() != null ? getTableRow().getItem() : null;
                            if (reg != null && reg.isChanged()) {
                                setStyle("-fx-text-fill: -color-danger-emphasis; -fx-font-weight: bold;");
                            } else {
                                setStyle("");
                            }
                        }
                    }
                };

        TableColumn<RegisterItemViewModel, String> hexCol = new TableColumn<>();
        hexCol.textProperty().bind(localization.text("debugger.registers.hex"));
        hexCol.setCellValueFactory(data -> data.getValue().hexValueProperty());
        hexCol.setCellFactory(regCellFactory);
        hexCol.setPrefWidth(170);

        TableColumn<RegisterItemViewModel, String> decCol = new TableColumn<>();
        decCol.textProperty().bind(localization.text("debugger.registers.dec"));
        decCol.setCellValueFactory(data -> data.getValue().decValueProperty());
        decCol.setCellFactory(regCellFactory);
        decCol.setPrefWidth(110);

        regTable.getColumns().addAll(nameCol, hexCol, decCol);
        VBox.setVgrow(regTable, Priority.ALWAYS);

        // Flags Box
        HBox flagsBox = new HBox(6);
        flagsBox.setPadding(new Insets(4));
        flagsBox.setAlignment(Pos.CENTER_LEFT);
        flagsBox.getChildren().addAll(
                createFlagBadge("CF", viewModel.cfProperty(), viewModel.cfChangedProperty()),
                createFlagBadge("ZF", viewModel.zfProperty(), viewModel.zfChangedProperty()),
                createFlagBadge("SF", viewModel.sfProperty(), viewModel.sfChangedProperty()),
                createFlagBadge("OF", viewModel.ofProperty(), viewModel.ofChangedProperty()),
                createFlagBadge("PF", viewModel.pfProperty(), viewModel.pfChangedProperty()),
                createFlagBadge("AF", viewModel.afProperty(), viewModel.afChangedProperty()),
                createFlagBadge("IF", viewModel.ifFlagProperty(), viewModel.ifFlagChangedProperty()),
                createFlagBadge("DF", viewModel.dfProperty(), viewModel.dfChangedProperty())
        );

        Label instructionsLabel = new Label();
        instructionsLabel.textProperty().bind(Bindings.createStringBinding(
                () -> localization.text("label.instructionsExecuted").get().replace("{0}", String.valueOf(viewModel.instructionsExecutedProperty().get())),
                localization.localeProperty(), viewModel.instructionsExecutedProperty()
        ));
        instructionsLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        instructionsLabel.setPadding(new Insets(0, 4, 0, 4));

        VBox box = new VBox(6, title, controlsBar, regTable, flagsBox, instructionsLabel);
        box.setPadding(new Insets(6));
        return box;
    }

    private Label createFlagBadge(String name, BooleanProperty property, BooleanProperty changedProperty) {
        Label badge = new Label(name);
        badge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.ROUNDED);
        badge.setPadding(new Insets(2, 6, 2, 6));
        badge.styleProperty().bind(Bindings.createStringBinding(() -> {
            String border = Boolean.TRUE.equals(changedProperty.get())
                    ? "-fx-border-color: -color-danger-emphasis; -fx-border-width: 1.5px; -fx-border-radius: 4px;"
                    : "-fx-border-color: transparent; -fx-border-width: 1.5px; -fx-border-radius: 4px;";
            String bg = property.get()
                    ? "-fx-background-color: -color-accent-emphasis; -fx-text-fill: -color-fg-emphasis; -fx-font-weight: bold;"
                    : "-fx-background-color: -color-bg-subtle; -fx-text-fill: -color-fg-muted;";
            return bg + " " + border;
        }, property, changedProperty));
        return badge;
    }

    private TabPane createCenterTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add(Styles.DENSE);

        Tab memoryTab = new Tab();
        memoryTab.textProperty().bind(localization.text("debugger.memory.title"));
        memoryTab.setContent(createMemoryBox());

        Tab stackTab = new Tab();
        stackTab.textProperty().bind(localization.text("debugger.stack.title"));
        stackTab.setContent(createCallStackBox());

        tabPane.getTabs().addAll(memoryTab, stackTab);
        return tabPane;
    }

    private VBox createMemoryBox() {
        Label title = new Label();
        title.textProperty().bind(localization.text("debugger.memory.title"));
        title.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_MUTED);

        HBox addressRow = new HBox(6);
        addressRow.setAlignment(Pos.CENTER_LEFT);
        Label addrLabel = new Label();
        // A DOS program is addressed as segment:offset; a 32/64-bit program by one flat address or a register.
        addrLabel.textProperty().bind(javafx.beans.binding.Bindings.when(viewModel.nativeSessionProperty())
                .then(localization.text("debugger.memory.flatAddress"))
                .otherwise(localization.text("debugger.memory.address")));

        TextField segField = new TextField();
        segField.setPrefWidth(60);
        segField.textProperty().bindBidirectional(viewModel.memorySegmentProperty());
        segField.setOnAction(e -> viewModel.requestMemoryRefresh());
        segField.visibleProperty().bind(viewModel.nativeSessionProperty().not());
        segField.managedProperty().bind(segField.visibleProperty());

        Label colon = new Label(":");
        colon.visibleProperty().bind(segField.visibleProperty());
        colon.managedProperty().bind(segField.visibleProperty());

        TextField offField = new TextField();
        offField.setPrefWidth(120);
        offField.textProperty().bindBidirectional(viewModel.memoryOffsetProperty());
        offField.setOnAction(e -> viewModel.requestMemoryRefresh());

        Button readBtn = new Button(null, io.github.dinamo541.idearm.app.ui.WorkbenchIcons.RESTART.create());
        io.github.dinamo541.idearm.app.ui.HoverHelp.install(readBtn, localization, "help.memory", "");
        readBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        readBtn.setOnAction(e -> viewModel.requestMemoryRefresh());

        addressRow.getChildren().addAll(addrLabel, segField, colon, offField, readBtn);

        TextArea dumpArea = new TextArea();
        dumpArea.setEditable(false);
        dumpArea.setStyle("-fx-font-family: 'JetBrains Mono', 'Consolas', monospace; -fx-font-size: 12px;");
        dumpArea.textProperty().bind(viewModel.memoryDumpTextProperty());
        VBox.setVgrow(dumpArea, Priority.ALWAYS);

        VBox box = new VBox(4, title, addressRow, dumpArea);
        box.setPadding(new Insets(6));
        return box;
    }

    private VBox createCallStackBox() {
        Label title = new Label();
        title.textProperty().bind(localization.text("debugger.stack.title"));
        title.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_MUTED);

        ListView<String> stackList = new ListView<>(viewModel.getStackLines());
        stackList.setPlaceholder(new Label());
        ((Label) stackList.getPlaceholder()).textProperty().bind(localization.text("debugger.stack.empty"));
        stackList.setStyle("-fx-font-family: 'JetBrains Mono', 'Consolas', monospace; -fx-font-size: 12px;");
        VBox.setVgrow(stackList, Priority.ALWAYS);

        VBox box = new VBox(4, title, stackList);
        box.setPadding(new Insets(6));
        return box;
    }

    private TabPane createRightTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add(Styles.DENSE);

        Tab breakpointsTab = new Tab();
        breakpointsTab.textProperty().bind(localization.text("debugger.breakpoints.title"));
        breakpointsTab.setContent(createBreakpointsBox());

        Tab watchesTab = new Tab();
        watchesTab.textProperty().bind(localization.text("debugger.watches.title"));
        watchesTab.setContent(createWatchesBox());

        tabPane.getTabs().addAll(breakpointsTab, watchesTab);
        return tabPane;
    }

    private VBox createWatchesBox() {
        Label title = new Label();
        title.textProperty().bind(localization.text("debugger.watches.title"));
        title.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_MUTED);

        TextField exprField = new TextField();
        exprField.promptTextProperty().bind(localization.text("debugger.watches.prompt"));
        HBox.setHgrow(exprField, Priority.ALWAYS);

        Button addBtn = new Button();
        addBtn.textProperty().bind(localization.text("debugger.watches.add"));
        addBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        addBtn.setOnAction(e -> {
            String expr = exprField.getText();
            if (expr != null && !expr.isBlank()) {
                viewModel.addWatch(expr);
                exprField.clear();
            }
        });
        exprField.setOnAction(e -> addBtn.fire());

        Button removeBtn = new Button();
        removeBtn.textProperty().bind(localization.text("action.remove"));
        removeBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER, Styles.SMALL);

        HBox inputBar = new HBox(4, exprField, addBtn, removeBtn);
        inputBar.setAlignment(Pos.CENTER_LEFT);

        TableView<WatchItemViewModel> table = new TableView<>(viewModel.getWatches());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label());
        ((Label) table.getPlaceholder()).textProperty().bind(localization.text("debugger.watches.empty"));

        TableColumn<WatchItemViewModel, String> exprCol = new TableColumn<>();
        exprCol.textProperty().bind(localization.text("debugger.watches.expression"));
        exprCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::getExpression));
        exprCol.setPrefWidth(120);

        TableColumn<WatchItemViewModel, String> valCol = new TableColumn<>();
        valCol.textProperty().bind(localization.text("debugger.watches.value"));
        valCol.setCellValueFactory(data -> data.getValue().valueProperty());
        valCol.setPrefWidth(120);

        table.getColumns().addAll(exprCol, valCol);
        VBox.setVgrow(table, Priority.ALWAYS);

        removeBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        removeBtn.setOnAction(e -> {
            WatchItemViewModel selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                viewModel.removeWatch(selected);
            }
        });

        VBox box = new VBox(4, title, inputBar, table);
        box.setPadding(new Insets(6));
        return box;
    }

    private VBox createBreakpointsBox() {
        Label title = new Label();
        title.textProperty().bind(localization.text("debugger.breakpoints.title"));
        title.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_MUTED);

        TableView<BreakpointItemViewModel> table = new TableView<>(viewModel.getBreakpoints());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label());
        ((Label) table.getPlaceholder()).textProperty().bind(localization.text("debugger.breakpoints.empty"));

        TableColumn<BreakpointItemViewModel, Boolean> enabledCol = new TableColumn<>();
        enabledCol.textProperty().bind(localization.text("debugger.breakpoints.enabled"));
        enabledCol.setCellValueFactory(data -> data.getValue().enabledProperty());
        enabledCol.setCellFactory(tc -> new CheckBoxTableCell<>());
        enabledCol.setPrefWidth(60);

        TableColumn<BreakpointItemViewModel, String> fileCol = new TableColumn<>();
        fileCol.textProperty().bind(localization.text("debugger.breakpoints.file"));
        fileCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::getPath));
        fileCol.setPrefWidth(120);

        TableColumn<BreakpointItemViewModel, String> lineCol = new TableColumn<>();
        lineCol.textProperty().bind(localization.text("debugger.breakpoints.line"));
        lineCol.setCellValueFactory(data -> Bindings.createStringBinding(() -> String.valueOf(data.getValue().getLine())));
        lineCol.setPrefWidth(60);

        table.getColumns().addAll(enabledCol, fileCol, lineCol);
        VBox.setVgrow(table, Priority.ALWAYS);

        table.setRowFactory(tv -> {
            TableRow<BreakpointItemViewModel> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    if (onBreakpointSelected != null) {
                        onBreakpointSelected.accept(row.getItem());
                    }
                }
            });
            return row;
        });

        VBox box = new VBox(4, title, table);
        box.setPadding(new Insets(6));
        return box;
    }

    public void setOnBreakpointSelected(Consumer<BreakpointItemViewModel> handler) {
        this.onBreakpointSelected = handler;
    }
}
