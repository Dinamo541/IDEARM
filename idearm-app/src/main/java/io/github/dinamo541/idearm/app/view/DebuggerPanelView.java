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
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
 *
 * <p>Structured as two resizable panels: CPU registers with controls and flags on the left sidebar, and a full
 * tabbed inspection area (Disassembly, Memory Dump, Call Stack, Breakpoints, Watches) on the right.
 */
public final class DebuggerPanelView extends StackPane {

    private static final String MONOSPACED = "-fx-font-family: 'JetBrains Mono';";

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
        panels.getStyleClass().addAll(Styles.DENSE, "debugger-split-pane");
        panels.setOrientation(javafx.geometry.Orientation.HORIZONTAL);

        var registersBox = createRegistersBox();
        var inspectionTabs = createInspectionTabPane();

        SplitPane.setResizableWithParent(registersBox, Boolean.TRUE);
        SplitPane.setResizableWithParent(inspectionTabs, Boolean.TRUE);

        registersBox.setMinWidth(300);
        inspectionTabs.setMinWidth(320);

        panels.getItems().addAll(registersBox, inspectionTabs);
        panels.setDividerPositions(0.40);
        panels.widthProperty().addListener((obs, oldW, newW) -> {
            if (oldW.doubleValue() <= 0 && newW.doubleValue() > 0) {
                javafx.application.Platform.runLater(() -> panels.setDividerPositions(0.40));
            }
        });

        // A session that declares no capability has nothing to show here. Before a session starts, the panels are
        // shown as before, so breakpoints and watches can be prepared.
        BooleanExpression launchOnly = viewModel.activeProperty().and(viewModel.integratedProperty().not());
        panels.visibleProperty().bind(launchOnly.not());
        panels.managedProperty().bind(panels.visibleProperty());

        Node notice = createExternalDebuggerNotice();
        notice.visibleProperty().bind(launchOnly);
        notice.managedProperty().bind(notice.visibleProperty());

        getChildren().addAll(panels, notice);
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

        resumeBtn.setId("debugger-resume-btn");
        pauseBtn.setId("debugger-pause-btn");
        stepOverBtn.setId("debugger-step-over-btn");
        stepIntoBtn.setId("debugger-step-into-btn");
        stepOutBtn.setId("debugger-step-out-btn");
        restartBtn.setId("debugger-restart-btn");
        stopBtn.setId("debugger-stop-btn");

        FlowPane controlsBar = new FlowPane(2, 2, resumeBtn, pauseBtn, stepOverBtn, stepIntoBtn, stepOutBtn, restartBtn,
                stopBtn);
        controlsBar.setAlignment(Pos.CENTER_LEFT);
        controlsBar.getStyleClass().add("debug-controls");

        TableView<RegisterItemViewModel> regTable = new TableView<>(viewModel.getRegisterList());
        regTable.setId("debugger-registers-table");
        regTable.getStyleClass().addAll("debug-register-table", Styles.DENSE);
        regTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        regTable.setFixedCellSize(25);
        regTable.setPlaceholder(new Label());

        // Digits line up in a monospaced font; a 64-bit value needs its 16 hex digits and 20 decimal digits.
        TableColumn<RegisterItemViewModel, String> nameCol = new TableColumn<>();
        nameCol.textProperty().bind(localization.text("debugger.registers.name"));
        nameCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::getName));
        nameCol.setMinWidth(72);
        nameCol.setPrefWidth(82);
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("reg-changed");
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    var reg = getTableRow() != null ? getTableRow().getItem() : null;
                    if (reg != null && reg.isChanged()) {
                        getStyleClass().add("reg-changed");
                    }
                    var help = viewModel.registerHelp(item, localization.localeProperty().get().getLanguage());
                    if (help.isPresent()) {
                        var tip = new Tooltip(help.get());
                        tip.setShowDelay(Duration.millis(450));
                        tip.setMaxWidth(360);
                        tip.setWrapText(true);
                        setTooltip(tip);
                    } else {
                        var tip = new Tooltip(item);
                        tip.setShowDelay(Duration.millis(450));
                        setTooltip(tip);
                    }
                }
            }
        });

        javafx.util.Callback<TableColumn<RegisterItemViewModel, String>, TableCell<RegisterItemViewModel, String>> regCellFactory =
                col -> new TableCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        getStyleClass().remove("reg-changed");
                        if (empty || item == null) {
                            setText(null);
                            setTooltip(null);
                        } else {
                            setText(item);
                            var reg = getTableRow() != null ? getTableRow().getItem() : null;
                            if (reg != null && reg.isChanged()) {
                                getStyleClass().add("reg-changed");
                            }
                            var tip = new Tooltip(item);
                            tip.setShowDelay(Duration.millis(350));
                            setTooltip(tip);
                        }
                    }
                };

        TableColumn<RegisterItemViewModel, String> hexCol = new TableColumn<>();
        hexCol.textProperty().bind(localization.text("debugger.registers.hex"));
        hexCol.setCellValueFactory(data -> data.getValue().hexValueProperty());
        hexCol.setCellFactory(regCellFactory);
        hexCol.minWidthProperty().bind(Bindings.when(viewModel.nativeSessionProperty()).then(130.0).otherwise(54.0));
        hexCol.prefWidthProperty().bind(Bindings.when(viewModel.nativeSessionProperty()).then(150.0).otherwise(64.0));

        TableColumn<RegisterItemViewModel, String> decCol = new TableColumn<>();
        decCol.textProperty().bind(localization.text("debugger.registers.dec"));
        decCol.setCellValueFactory(data -> data.getValue().decValueProperty());
        decCol.setCellFactory(regCellFactory);
        decCol.minWidthProperty().bind(Bindings.when(viewModel.nativeSessionProperty()).then(150.0).otherwise(56.0));
        decCol.prefWidthProperty().bind(Bindings.when(viewModel.nativeSessionProperty()).then(170.0).otherwise(68.0));

        // Binary is what a computer architecture course reads flags and masks in. A 64-bit register would need 64
        // digits, which do not fit and say little, so the column is dropped for native programs.
        TableColumn<RegisterItemViewModel, String> binCol = new TableColumn<>();
        binCol.textProperty().bind(localization.text("debugger.registers.bin"));
        binCol.setCellValueFactory(data -> data.getValue().binValueProperty());
        binCol.setCellFactory(regCellFactory);
        binCol.setMinWidth(135);
        binCol.setPrefWidth(150);
        binCol.visibleProperty().bind(viewModel.nativeSessionProperty().not());

        regTable.getColumns().addAll(nameCol, hexCol, decCol, binCol);
        regTable.setContextMenu(createRegisterContextMenu(regTable));
        VBox.setVgrow(regTable, Priority.ALWAYS);

        FlowPane flagsBox = new FlowPane(3, 3);
        flagsBox.setId("debugger-flags-box");
        flagsBox.getStyleClass().add("debug-flags");
        flagsBox.setPadding(new Insets(3));
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

        VBox box = new VBox(5, title, controlsBar, regTable, flagsBox, instructionsLabel);
        box.getStyleClass().add("debug-section");
        box.setMinWidth(300);
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

    // What each flag means, in the reader's language: a badge is not self-explanatory to a beginner.
    private Label createFlagBadge(String name, BooleanProperty property, BooleanProperty changedProperty) {
        Label badge = new Label(name);
        badge.getStyleClass().add("debug-flag");

        Runnable updateState = () -> {
            boolean active = Boolean.TRUE.equals(property.get());
            boolean changed = Boolean.TRUE.equals(changedProperty.get());
            if (active) {
                if (!badge.getStyleClass().contains("active")) {
                    badge.getStyleClass().add("active");
                }
            } else {
                badge.getStyleClass().remove("active");
            }
            if (changed) {
                if (!badge.getStyleClass().contains("changed")) {
                    badge.getStyleClass().add("changed");
                }
            } else {
                badge.getStyleClass().remove("changed");
            }
        };

        property.addListener((obs, oldV, newV) -> updateState.run());
        changedProperty.addListener((obs, oldV, newV) -> updateState.run());
        updateState.run();

        var tip = new Tooltip();
        tip.textProperty().bind(localization.text("debugger.flags." + name.toLowerCase(java.util.Locale.ROOT)));
        Tooltip.install(badge, tip);
        return badge;
    }

    /**
     * Unified inspection tab pane housing Disassembly, Memory Dump, Call Stack, Breakpoints, and Watches.
     */
    private TabPane createInspectionTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setId("debugger-inspection-tabs");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().addAll(Styles.DENSE, "debugger-tab-pane");

        Tab disasmTab = new Tab();
        disasmTab.textProperty().bind(localization.text("debugger.disasm.title"));
        disasmTab.setGraphic(WorkbenchIcons.ASSEMBLY.create(14));
        var disasmTip = new Tooltip();
        disasmTip.textProperty().bind(localization.text("debugger.disasm.title"));
        disasmTab.setTooltip(disasmTip);
        disasmTab.setContent(createDisassemblyBox());

        Tab memoryTab = new Tab();
        memoryTab.textProperty().bind(localization.text("debugger.memory.title"));
        memoryTab.setGraphic(WorkbenchIcons.CHIP.create(14));
        var memoryTip = new Tooltip();
        memoryTip.textProperty().bind(localization.text("debugger.memory.title"));
        memoryTab.setTooltip(memoryTip);
        memoryTab.setContent(createMemoryBox());

        Tab stackTab = new Tab();
        stackTab.textProperty().bind(localization.text("debugger.stack.title"));
        stackTab.setGraphic(WorkbenchIcons.STACK_PROCEDURES.create(14));
        var stackTip = new Tooltip();
        stackTip.textProperty().bind(localization.text("debugger.stack.title"));
        stackTab.setTooltip(stackTip);
        stackTab.setContent(createCallStackBox());

        Tab breakpointsTab = new Tab();
        breakpointsTab.textProperty().bind(localization.text("debugger.breakpoints.title"));
        breakpointsTab.setGraphic(WorkbenchIcons.BREAKPOINT.create(14));
        var bpTip = new Tooltip();
        bpTip.textProperty().bind(localization.text("debugger.breakpoints.title"));
        breakpointsTab.setTooltip(bpTip);
        breakpointsTab.setContent(createBreakpointsBox());

        Tab watchesTab = new Tab();
        watchesTab.textProperty().bind(localization.text("debugger.watches.title"));
        watchesTab.setGraphic(WorkbenchIcons.SEARCH.create(14));
        var watchTip = new Tooltip();
        watchTip.textProperty().bind(localization.text("debugger.watches.title"));
        watchesTab.setTooltip(watchTip);
        watchesTab.setContent(createWatchesBox());

        tabPane.getTabs().addAll(disasmTab, memoryTab, stackTab, breakpointsTab, watchesTab);
        return tabPane;
    }

    /**
     * What the current source line actually became. One line of assembly is often several instructions — on an
     * 8086, {@code shl ax, 3} is three shifts — and this is where that becomes visible.
     */
    private VBox createDisassemblyBox() {
        ListView<DisasmLine> list = new ListView<>(viewModel.getDisassembly());
        list.setId("debugger-disasm-list");
        list.setPlaceholder(new Label());
        ((Label) list.getPlaceholder()).textProperty().bind(localization.text("debugger.disasm.empty"));
        list.setStyle(MONOSPACED + " -fx-font-size: 12px;");
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(DisasmLine item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("disasm-current");
                setText((empty || item == null) ? null : item.toDisplayString());
                // The instruction about to run, marked as the editor marks its line.
                if (item != null && item.current()) {
                    getStyleClass().add("disasm-current");
                }
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        VBox box = new VBox(list);
        box.getStyleClass().add("debug-section");
        box.setMinWidth(0);
        return box;
    }

    private VBox createMemoryBox() {
        HBox addressRow = new HBox(6);
        addressRow.setAlignment(Pos.CENTER_LEFT);
        Label addrLabel = new Label();
        // A DOS program is addressed as segment:offset; a 32/64-bit program by one flat address or a register.
        addrLabel.textProperty().bind(Bindings.when(viewModel.nativeSessionProperty())
                .then(localization.text("debugger.memory.flatAddress"))
                .otherwise(localization.text("debugger.memory.address")));

        TextField segField = new TextField();
        segField.setId("debugger-memory-seg-field");
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
        offField.setId("debugger-memory-off-field");
        offField.setPrefWidth(120);
        offField.setMinWidth(65);
        HBox.setHgrow(offField, Priority.ALWAYS);
        offField.textProperty().bindBidirectional(viewModel.memoryOffsetProperty());
        offField.setOnAction(e -> viewModel.requestMemoryRefresh());

        Button readBtn = new Button(null, WorkbenchIcons.RESTART.create());
        readBtn.setId("debugger-memory-read-btn");
        HoverHelp.install(readBtn, localization, "help.memory", "");
        readBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        readBtn.setOnAction(e -> viewModel.requestMemoryRefresh());
        readBtn.disableProperty().bind(viewModel.canReadMemoryProperty().not());

        addressRow.getChildren().addAll(addrLabel, segField, colon, offField, readBtn);
        addrLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);

        TextArea dumpArea = new TextArea();
        dumpArea.setId("debugger-memory-dump-area");
        dumpArea.setEditable(false);
        dumpArea.setStyle(MONOSPACED + " -fx-font-size: 12px;");
        dumpArea.textProperty().bind(viewModel.memoryDumpTextProperty());
        VBox.setVgrow(dumpArea, Priority.ALWAYS);

        VBox box = new VBox(6, addressRow, dumpArea);
        box.getStyleClass().add("debug-section");
        box.setMinWidth(0);
        return box;
    }

    private VBox createCallStackBox() {
        ListView<CallFrameItemViewModel> stackList = new ListView<>(viewModel.getStackLines());
        stackList.setId("debugger-stack-list");
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

        VBox box = new VBox(stackList);
        box.getStyleClass().add("debug-section");
        box.setMinWidth(0);
        return box;
    }

    private VBox createWatchesBox() {
        TextField exprField = new TextField();
        exprField.setId("debugger-watch-expr-field");
        exprField.promptTextProperty().bind(localization.text("debugger.watches.prompt"));
        HBox.setHgrow(exprField, Priority.ALWAYS);
        exprField.setMinWidth(80);

        Button addBtn = new Button();
        addBtn.setId("debugger-watch-add-btn");
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
        removeBtn.setId("debugger-watch-remove-btn");
        removeBtn.setGraphic(WorkbenchIcons.CLEAR.create());
        HoverHelp.install(removeBtn, localization, "action.remove", "");
        removeBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER, Styles.SMALL);

        HBox inputBar = new HBox(6, exprField, addBtn, removeBtn);
        inputBar.setAlignment(Pos.CENTER_LEFT);

        TableView<WatchItemViewModel> table = new TableView<>(viewModel.getWatches());
        table.setId("debugger-watches-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label());
        ((Label) table.getPlaceholder()).textProperty().bind(localization.text("debugger.watches.empty"));

        TableColumn<WatchItemViewModel, String> exprCol = new TableColumn<>();
        exprCol.textProperty().bind(localization.text("debugger.watches.expression"));
        exprCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::getExpression));
        exprCol.setPrefWidth(160);

        TableColumn<WatchItemViewModel, String> valCol = new TableColumn<>();
        valCol.textProperty().bind(localization.text("debugger.watches.value"));
        // An expression the debugger could not evaluate reads as an explanation in the user's language, not as a
        // marker the view model made up.
        valCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> data.getValue().isError()
                        ? localization.text("debugger.watch.error").get()
                        : data.getValue().getValue(),
                data.getValue().valueProperty(), data.getValue().errorProperty(), localization.localeProperty()));
        valCol.setPrefWidth(160);

        table.getColumns().addAll(exprCol, valCol);
        VBox.setVgrow(table, Priority.ALWAYS);

        removeBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        removeBtn.setOnAction(e -> {
            WatchItemViewModel selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                viewModel.removeWatch(selected);
            }
        });

        VBox box = new VBox(5, inputBar, table);
        box.getStyleClass().add("debug-section");
        box.setMinWidth(0);
        return box;
    }

    private VBox createBreakpointsBox() {
        TableView<BreakpointItemViewModel> table = new TableView<>(viewModel.getBreakpoints());
        table.setId("debugger-breakpoints-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label());
        ((Label) table.getPlaceholder()).textProperty().bind(localization.text("debugger.breakpoints.empty"));

        TableColumn<BreakpointItemViewModel, Boolean> enabledCol = new TableColumn<>();
        enabledCol.textProperty().bind(localization.text("debugger.breakpoints.enabled"));
        // Ticking the box writes through to the stored breakpoint and to a running session; the view model carries
        // that intent on, so the listener lives with the row rather than with each rendering of the cell.
        enabledCol.setCellValueFactory(data -> data.getValue().enabledProperty());
        enabledCol.setCellFactory(tc -> new CheckBoxTableCell<>());
        enabledCol.setMinWidth(60);
        enabledCol.setPrefWidth(70);

        TableColumn<BreakpointItemViewModel, String> fileCol = new TableColumn<>();
        fileCol.textProperty().bind(localization.text("debugger.breakpoints.file"));
        fileCol.setCellValueFactory(data -> Bindings.createStringBinding(data.getValue()::getPath));
        fileCol.setMinWidth(120);
        fileCol.setPrefWidth(220);

        TableColumn<BreakpointItemViewModel, String> lineCol = new TableColumn<>();
        lineCol.textProperty().bind(localization.text("debugger.breakpoints.line"));
        lineCol.setCellValueFactory(data -> Bindings.createStringBinding(() -> String.valueOf(data.getValue().getLine())));
        lineCol.setMinWidth(55);
        lineCol.setPrefWidth(70);

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

        VBox box = new VBox(table);
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
