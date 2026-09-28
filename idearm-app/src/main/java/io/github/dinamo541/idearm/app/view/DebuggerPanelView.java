package io.github.dinamo541.idearm.app.view;

import atlantafx.base.theme.Styles;
import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.ui.HoverHelp;
import io.github.dinamo541.idearm.app.ui.WorkbenchIcons;
import io.github.dinamo541.idearm.app.viewmodel.BreakpointItemViewModel;
import io.github.dinamo541.idearm.app.viewmodel.CallFrameItemViewModel;
import io.github.dinamo541.idearm.app.viewmodel.DebugViewModel;
import io.github.dinamo541.idearm.app.viewmodel.RegisterItemViewModel;
import io.github.dinamo541.idearm.app.viewmodel.WatchItemViewModel;
import io.github.dinamo541.idearm.domain.debug.DisasmLine;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.binding.BooleanExpression;
import javafx.scene.Node;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * Visual debugger panel hosting CPU registers, condition code flags, memory hex dump, stack inspection, and
 * project breakpoints.
 *
 * <p>Every control is bound to a capability the attached session declares, not merely to "the program is
 * paused": Turbo Debugger and CodeView drive the program in their own DOSBox window and report nothing back, so
 * with them the panel explains that instead of offering buttons that would do nothing.
 */
public final class DebuggerPanelView extends StackPane {

    private static final String MONOSPACED = "-fx-font-family: 'Consolas';";

    private final DebugViewModel viewModel;
    private final Localization localization;
    private Consumer<BreakpointItemViewModel> onBreakpointSelected;
    private Consumer<CallFrameItemViewModel> onCallFrameSelected;
    private Runnable onRestartRequested = () -> {};

    public DebuggerPanelView(DebugViewModel viewModel, Localization localization) {
        this.viewModel = viewModel;
        this.localization = localization;
        getStyleClass().add("debugger-panel");

        SplitPane panels = new SplitPane();
        panels.getStyleClass().add(Styles.DENSE);
        panels.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        panels.getItems().addAll(createRegistersBox(), createCenterTabPane(), createRightTabPane());
        panels.setDividerPositions(0.44, 0.74);
        panels.setMinWidth(880);
        panels.setMinHeight(210);
        // Keep register values and memory addresses readable on narrow windows; the whole tool can scroll.
        var viewport = new ScrollPane(panels);
        viewport.setFitToWidth(true);
        viewport.setFitToHeight(true);
        viewport.setMinSize(0, 0);

        // A session that declares no capability has nothing to show here. Before a session starts, the panels are
        // shown as before, so breakpoints and watches can be prepared.
        BooleanExpression launchOnly = viewModel.activeProperty().and(viewModel.integratedProperty().not());
        viewport.visibleProperty().bind(launchOnly.not());
        viewport.managedProperty().bind(viewport.visibleProperty());

        Node notice = createExternalDebuggerNotice();
        notice.visibleProperty().bind(launchOnly);
        notice.managedProperty().bind(notice.visibleProperty());

        getChildren().addAll(viewport, notice);
    }

    /** Shown while a launch-only debugger owns the session, in place of panels that would stay empty. */
    private Node createExternalDebuggerNotice() {
        Label text = new Label();
        text.textProperty().bind(localization.text("debugger.external.notice"));
        text.setWrapText(true);
        text.setMaxWidth(560);
        text.getStyleClass().add(Styles.TEXT_MUTED);
        text.setAlignment(Pos.CENTER);
        text.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        VBox box = new VBox(8, text);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24));
        return box;
    }

    private VBox createRegistersBox() {
        Label title = new Label();
        title.textProperty().bind(localization.text("debugger.registers.title"));
        title.getStyleClass().add("debug-heading");

        // A step is offered only when the session can step, and only while the program is stopped.
        BooleanExpression steppable = viewModel.pausedProperty().and(viewModel.canStepProperty());

        var resumeBtn = controlButton("action.resume", "tooltip.resume", WorkbenchIcons.RUN, Styles.SUCCESS,
                viewModel::resume, steppable);
        var pauseBtn = controlButton("action.pause", "tooltip.pause", WorkbenchIcons.PAUSE, Styles.WARNING,
                viewModel::pause,
                viewModel.activeProperty().and(viewModel.pausedProperty().not()).and(viewModel.canPauseProperty()));
        var stepOverBtn = controlButton("action.stepOver", "tooltip.stepOver", WorkbenchIcons.STEP_OVER, null,
                viewModel::stepOver, steppable);
        var stepIntoBtn = controlButton("action.stepInto", "tooltip.stepInto", WorkbenchIcons.STEP_INTO, null,
                viewModel::stepInto, steppable);
        var stepOutBtn = controlButton("action.stepOut", "tooltip.stepOut", WorkbenchIcons.STEP_OUT, null,
                viewModel::stepOut, steppable);
        var restartBtn = controlButton("menu.run.restartDebug", "tooltip.restartDebug", WorkbenchIcons.RESTART, null,
                () -> onRestartRequested.run(), viewModel.activeProperty());
        var stopBtn = controlButton("action.stop", "tooltip.stopDebug", WorkbenchIcons.STOP, Styles.DANGER,
                viewModel::stop, viewModel.activeProperty());

        FlowPane controlsBar = new FlowPane(3, 3, resumeBtn, pauseBtn, stepOverBtn, stepIntoBtn, stepOutBtn, restartBtn,
                stopBtn);
        controlsBar.setAlignment(Pos.CENTER_LEFT);
        controlsBar.getStyleClass().add("debug-controls");

        TableView<RegisterItemViewModel> regTable = new TableView<>(viewModel.getRegisterList());
        regTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        regTable.setFixedCellSize(26);
        regTable.setPlaceholder(new Label());
        // Digits line up in a monospaced font; a 64-bit value needs its 16 hex digits.
        regTable.setStyle(MONOSPACED);

        TableColumn<RegisterItemViewModel, String> nameCol = new TableColumn<>();
        nameCol.textProperty().bind(localization.text("debugger.registers.name"));
        nameCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::getName));
        nameCol.setPrefWidth(75);
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    setStyle("");
                } else {
                    setText(item);
                    var reg = getTableRow() != null ? getTableRow().getItem() : null;
                    if (reg != null && reg.isChanged()) {
                        setStyle("-fx-text-fill: -color-warning-fg; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                    var help = viewModel.registerHelp(item, localization.localeProperty().get().getLanguage());
                    if (help.isPresent()) {
                        var tip = new Tooltip(help.get());
                        tip.setShowDelay(Duration.millis(450));
                        tip.setMaxWidth(360);
                        tip.setWrapText(true);
                        setTooltip(tip);
                    } else {
                        setTooltip(null);
                    }
                }
            }
        });

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
                                setStyle("-fx-text-fill: -color-warning-fg; -fx-font-weight: bold;");
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
        hexCol.prefWidthProperty().bind(Bindings.when(viewModel.nativeSessionProperty()).then(170).otherwise(70));

        TableColumn<RegisterItemViewModel, String> decCol = new TableColumn<>();
        decCol.textProperty().bind(localization.text("debugger.registers.dec"));
        decCol.setCellValueFactory(data -> data.getValue().decValueProperty());
        decCol.setCellFactory(regCellFactory);
        decCol.prefWidthProperty().bind(Bindings.when(viewModel.nativeSessionProperty()).then(175).otherwise(75));

        // Binary is what a computer architecture course reads flags and masks in. A 64-bit register would need 64
        // digits, which do not fit and say little, so the column is dropped for native programs.
        TableColumn<RegisterItemViewModel, String> binCol = new TableColumn<>();
        binCol.textProperty().bind(localization.text("debugger.registers.bin"));
        binCol.setCellValueFactory(data -> data.getValue().binValueProperty());
        binCol.setCellFactory(regCellFactory);
        binCol.setPrefWidth(170);
        binCol.visibleProperty().bind(viewModel.nativeSessionProperty().not());

        regTable.getColumns().addAll(nameCol, hexCol, decCol, binCol);
        regTable.setContextMenu(createRegisterContextMenu(regTable));
        VBox.setVgrow(regTable, Priority.ALWAYS);

        FlowPane flagsBox = new FlowPane(3, 3);
        flagsBox.getStyleClass().add("debug-flags");
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
        box.getStyleClass().add("debug-section");
        box.setMinWidth(0);
        return box;
    }

    /** A debugger control whose enabled state follows what the session can actually do. */
    private Button controlButton(String textKey, String tooltipKey, WorkbenchIcons icon, String accent,
                                 Runnable action, BooleanExpression enabled) {
        var button = new Button();
        button.accessibleTextProperty().bind(localization.text(textKey));
        HoverHelp.install(button, localization, tooltipKey, "");
        button.setGraphic(icon.create());
        button.getStyleClass().add("icon-button");
        if (accent != null) {
            button.getStyleClass().add(accent);
        }
        button.setOnAction(e -> action.run());
        button.disableProperty().bind(enabled.not());
        return button;
    }

    /** Copies a register in the base the reader needs, without retyping digits by hand. */
    private ContextMenu createRegisterContextMenu(TableView<RegisterItemViewModel> table) {
        var menu = new ContextMenu();
        menu.getItems().addAll(
                copyItem("debugger.registers.hex", RegisterItemViewModel::getHexValue, table),
                copyItem("debugger.registers.dec", RegisterItemViewModel::getDecValue, table),
                copyItem("debugger.registers.bin", RegisterItemViewModel::getBinValue, table));
        return menu;
    }

    private MenuItem copyItem(String labelKey, java.util.function.Function<RegisterItemViewModel, String> value,
                              TableView<RegisterItemViewModel> table) {
        var item = new MenuItem();
        item.textProperty().bind(localization.text(labelKey));
        item.setOnAction(e -> {
            RegisterItemViewModel selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                var content = new javafx.scene.input.ClipboardContent();
                content.putString(value.apply(selected));
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            }
        });
        item.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        return item;
    }

    private Label createFlagBadge(String name, BooleanProperty property, BooleanProperty changedProperty) {
        Label badge = new Label(name);
        badge.getStyleClass().add("debug-flag");
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
        // What each flag means, in the reader's language: a badge is not self-explanatory to a beginner.
        var tip = new Tooltip();
        tip.textProperty().bind(localization.text("debugger.flags." + name.toLowerCase(java.util.Locale.ROOT)));
        Tooltip.install(badge, tip);
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

        Tab disasmTab = new Tab();
        disasmTab.textProperty().bind(localization.text("debugger.disasm.title"));
        disasmTab.setContent(createDisassemblyBox());

        tabPane.getTabs().addAll(memoryTab, stackTab, disasmTab);
        return tabPane;
    }

    /**
     * What the current source line actually became. One line of assembly is often several instructions — on an
     * 8086, {@code shl ax, 3} is three shifts — and this is where that becomes visible.
     */
    private VBox createDisassemblyBox() {
        Label title = new Label();
        title.textProperty().bind(localization.text("debugger.disasm.title"));
        title.getStyleClass().add("debug-heading");

        ListView<DisasmLine> list = new ListView<>(viewModel.getDisassembly());
        list.setPlaceholder(new Label());
        ((Label) list.getPlaceholder()).textProperty().bind(localization.text("debugger.disasm.empty"));
        list.setStyle(MONOSPACED + " -fx-font-size: 12px;");
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(DisasmLine item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.toDisplayString());
                // The instruction about to run, marked as the editor marks its line.
                setStyle(item != null && item.current()
                        ? "-fx-background-color: -color-warning-subtle; -fx-font-weight: bold;" : "");
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        VBox box = new VBox(4, title, list);
        box.getStyleClass().add("debug-section");
        box.setMinWidth(0);
        return box;
    }

    private VBox createMemoryBox() {
        Label title = new Label();
        title.textProperty().bind(localization.text("debugger.memory.title"));
        title.getStyleClass().add("debug-heading");

        HBox addressRow = new HBox(6);
        addressRow.setAlignment(Pos.CENTER_LEFT);
        Label addrLabel = new Label();
        // A DOS program is addressed as segment:offset; a 32/64-bit program by one flat address or a register.
        addrLabel.textProperty().bind(javafx.beans.binding.Bindings.when(viewModel.nativeSessionProperty())
                .then(localization.text("debugger.memory.flatAddress"))
                .otherwise(localization.text("debugger.memory.address")));

        TextField segField = new TextField();
        segField.setPrefWidth(60);
        segField.setMinWidth(45);
        segField.textProperty().bindBidirectional(viewModel.memorySegmentProperty());
        segField.setOnAction(e -> viewModel.requestMemoryRefresh());
        segField.visibleProperty().bind(viewModel.nativeSessionProperty().not());
        segField.managedProperty().bind(segField.visibleProperty());

        Label colon = new Label(":");
        colon.visibleProperty().bind(segField.visibleProperty());
        colon.managedProperty().bind(segField.visibleProperty());

        TextField offField = new TextField();
        offField.setPrefWidth(100);
        offField.setMinWidth(55);
        HBox.setHgrow(offField, Priority.ALWAYS);
        offField.textProperty().bindBidirectional(viewModel.memoryOffsetProperty());
        offField.setOnAction(e -> viewModel.requestMemoryRefresh());

        Button readBtn = new Button(null, WorkbenchIcons.RESTART.create());
        HoverHelp.install(readBtn, localization, "help.memory", "");
        readBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        readBtn.setOnAction(e -> viewModel.requestMemoryRefresh());
        readBtn.disableProperty().bind(viewModel.canReadMemoryProperty().not());

        addressRow.getChildren().addAll(segField, colon, offField, readBtn);
        addrLabel.setWrapText(true);
        addrLabel.getStyleClass().add(Styles.TEXT_MUTED);

        TextArea dumpArea = new TextArea();
        dumpArea.setEditable(false);
        dumpArea.setStyle(MONOSPACED + " -fx-font-size: 12px;");
        dumpArea.textProperty().bind(viewModel.memoryDumpTextProperty());
        VBox.setVgrow(dumpArea, Priority.ALWAYS);

        VBox box = new VBox(6, addrLabel, addressRow, dumpArea);
        box.getStyleClass().add("debug-section");
        box.setMinWidth(0);
        return box;
    }

    private VBox createCallStackBox() {
        Label title = new Label();
        title.textProperty().bind(localization.text("debugger.stack.title"));
        title.getStyleClass().add("debug-heading");

        ListView<CallFrameItemViewModel> stackList = new ListView<>(viewModel.getStackLines());
        stackList.setPlaceholder(new Label());
        ((Label) stackList.getPlaceholder()).textProperty().bind(localization.text("debugger.stack.empty"));
        stackList.setStyle(MONOSPACED + " -fx-font-size: 12px;");
        // A frame that knows its source line opens it on a double click; a stack word has nowhere to go.
        stackList.setCellFactory(list -> {
            var cell = new ListCell<CallFrameItemViewModel>() {
                @Override
                protected void updateItem(CallFrameItemViewModel item, boolean empty) {
                    super.updateItem(item, empty);
                    setText((empty || item == null) ? null : item.getText());
                }
            };
            cell.setOnMouseClicked(event -> {
                CallFrameItemViewModel item = cell.getItem();
                if (event.getClickCount() == 2 && item != null && item.isNavigable() && onCallFrameSelected != null) {
                    onCallFrameSelected.accept(item);
                }
            });
            return cell;
        });
        VBox.setVgrow(stackList, Priority.ALWAYS);

        VBox box = new VBox(4, title, stackList);
        box.getStyleClass().add("debug-section");
        box.setMinWidth(0);
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
        title.getStyleClass().add("debug-heading");

        TextField exprField = new TextField();
        exprField.promptTextProperty().bind(localization.text("debugger.watches.prompt"));
        HBox.setHgrow(exprField, Priority.ALWAYS);
        exprField.setMinWidth(50);

        Button addBtn = new Button();
        addBtn.setGraphic(WorkbenchIcons.PLUS.create());
        HoverHelp.install(addBtn, localization, "debugger.watches.add", "");
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
        removeBtn.setGraphic(WorkbenchIcons.CLEAR.create());
        HoverHelp.install(removeBtn, localization, "action.remove", "");
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
        // An expression the debugger could not evaluate reads as an explanation in the user's language, not as a
        // marker the view model made up.
        valCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> data.getValue().isError()
                        ? localization.text("debugger.watch.error").get()
                        : data.getValue().getValue(),
                data.getValue().valueProperty(), data.getValue().errorProperty(), localization.localeProperty()));
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
        box.getStyleClass().add("debug-section");
        box.setMinWidth(0);
        return box;
    }

    private VBox createBreakpointsBox() {
        Label title = new Label();
        title.textProperty().bind(localization.text("debugger.breakpoints.title"));
        title.getStyleClass().add("debug-heading");

        TableView<BreakpointItemViewModel> table = new TableView<>(viewModel.getBreakpoints());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label());
        ((Label) table.getPlaceholder()).textProperty().bind(localization.text("debugger.breakpoints.empty"));

        TableColumn<BreakpointItemViewModel, Boolean> enabledCol = new TableColumn<>();
        enabledCol.textProperty().bind(localization.text("debugger.breakpoints.enabled"));
        // Ticking the box writes through to the stored breakpoint and to a running session; the view model carries
        // that intent on, so the listener lives with the row rather than with each rendering of the cell.
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
        box.getStyleClass().add("debug-section");
        box.setMinWidth(0);
        return box;
    }

    public void setOnBreakpointSelected(Consumer<BreakpointItemViewModel> handler) {
        this.onBreakpointSelected = handler;
    }

    /** Called when a call-stack frame that knows its source line is double-clicked. */
    public void setOnCallFrameSelected(Consumer<CallFrameItemViewModel> handler) {
        this.onCallFrameSelected = handler;
    }

    /** Called by the Restart button; restarting a session is a workbench task, not a session operation. */
    public void setOnRestartRequested(Runnable handler) {
        this.onRestartRequested = (handler != null) ? handler : () -> {};
    }
}
