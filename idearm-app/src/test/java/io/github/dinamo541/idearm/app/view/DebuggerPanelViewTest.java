package io.github.dinamo541.idearm.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.ui.WorkbenchTheme;
import io.github.dinamo541.idearm.app.viewmodel.BreakpointItemViewModel;
import io.github.dinamo541.idearm.app.viewmodel.CallFrameItemViewModel;
import io.github.dinamo541.idearm.app.viewmodel.DebugViewModel;
import io.github.dinamo541.idearm.app.viewmodel.RegisterItemViewModel;
import io.github.dinamo541.idearm.app.viewmodel.WatchItemViewModel;
import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.CallFrame;
import io.github.dinamo541.idearm.domain.debug.DisasmLine;
import io.github.dinamo541.idearm.domain.debug.RegisterState;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Validates the visual debugger layout, responsiveness, column sizing, 64-bit value accessibility,
 * capability gating, and user interactions across English and Spanish locales.
 */
class DebuggerPanelViewTest {

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        FxDialogs.startJavaFx();
    }

    @Test
    void panelHostsTwoResizableSectionsWithoutOuterHorizontalScrollPane() throws Exception {
        FxDialogs.onFxThread(() -> {
            var viewModel = new DebugViewModel();
            var localization = new Localization();
            var view = new DebuggerPanelView(viewModel, localization);

            // There must not be an outer ScrollPane forcing horizontal scrolling on narrow windows
            assertTrue(view.getChildren().stream().noneMatch(node -> node instanceof ScrollPane),
                    "DebuggerPanelView should directly host SplitPane without an outer ScrollPane");

            var splitPaneOpt = view.getChildren().stream()
                    .filter(node -> node instanceof SplitPane)
                    .map(node -> (SplitPane) node)
                    .findFirst();
            assertTrue(splitPaneOpt.isPresent(), "Expected a SplitPane in DebuggerPanelView");

            var splitPane = splitPaneOpt.get();
            assertEquals(2, splitPane.getItems().size(), "Expected exactly 2 resizable panels (Registers and Inspector)");

            var leftItem = splitPane.getItems().get(0);
            var rightItem = splitPane.getItems().get(1);

            assertTrue(leftItem instanceof VBox, "Left panel should be the registers VBox container");
            assertTrue(rightItem instanceof TabPane, "Right panel should be the unified inspection TabPane");

            var inspectionTabs = (TabPane) rightItem;
            assertEquals(5, inspectionTabs.getTabs().size(),
                    "Inspection TabPane should contain all 5 views: Disassembly, Memory, Stack, Breakpoints, Watches");

            // Verify order and accessibility of all five views
            Tab disasmTab = inspectionTabs.getTabs().get(0);
            Tab memoryTab = inspectionTabs.getTabs().get(1);
            Tab stackTab = inspectionTabs.getTabs().get(2);
            Tab breakpointsTab = inspectionTabs.getTabs().get(3);
            Tab watchesTab = inspectionTabs.getTabs().get(4);

            assertNotNull(disasmTab.getGraphic(), "Disassembly tab should have an icon");
            assertNotNull(memoryTab.getGraphic(), "Memory tab should have an icon");
            assertNotNull(stackTab.getGraphic(), "Stack tab should have an icon");
            assertNotNull(breakpointsTab.getGraphic(), "Breakpoints tab should have an icon");
            assertNotNull(watchesTab.getGraphic(), "Watches tab should have an icon");

            assertNotNull(disasmTab.getTooltip(), "Disassembly tab should have a tooltip");
            assertNotNull(memoryTab.getTooltip(), "Memory tab should have a tooltip");
            assertNotNull(stackTab.getTooltip(), "Stack tab should have a tooltip");
            assertNotNull(breakpointsTab.getTooltip(), "Breakpoints tab should have a tooltip");
            assertNotNull(watchesTab.getTooltip(), "Watches tab should have a tooltip");

            return null;
        });
    }

    @Test
    void renderedHeadersAndValuesAt960pxInEnglishAndSpanish() throws Exception {
        FxDialogs.onFxThread(() -> {
            var viewModel = new DebugViewModel();
            var localization = new Localization();
            localization.localeProperty().set(Locale.ENGLISH);

            var view = new DebuggerPanelView(viewModel, localization);
            var stage = new Stage();
            var scene = new Scene(view, 960, 450);
            WorkbenchTheme.apply(view, stage);
            stage.setScene(scene);
            stage.show();

            try {
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                @SuppressWarnings("unchecked")
                var regTable = (TableView<RegisterItemViewModel>) scene.lookup("#debugger-registers-table");
                assertNotNull(regTable, "Registers TableView should exist");

                assertEquals(4, regTable.getColumns().size());
                TableColumn<RegisterItemViewModel, ?> nameCol = regTable.getColumns().get(0);
                TableColumn<RegisterItemViewModel, ?> hexCol = regTable.getColumns().get(1);
                TableColumn<RegisterItemViewModel, ?> decCol = regTable.getColumns().get(2);
                TableColumn<RegisterItemViewModel, ?> binCol = regTable.getColumns().get(3);

                assertEquals("Register", nameCol.getText());
                assertEquals("Hex", hexCol.getText());
                assertEquals("Dec", decCol.getText());
                assertEquals("Bin", binCol.getText());

                assertTrue(nameCol.getMinWidth() >= 70, "Name column minWidth should comfortably fit 'Register'");
                assertTrue(binCol.isVisible(), "Binary column must be visible in DOS mode");

                // Switch to Spanish at runtime
                localization.localeProperty().set(Locale.of("es"));
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                assertEquals("Registro", nameCol.getText(), "Header should reactively translate to Spanish");
                assertEquals("Hex", hexCol.getText());
                assertEquals("Dec", decCol.getText());
                assertEquals("Bin", binCol.getText());
                assertTrue(nameCol.getMinWidth() >= 70, "Name column minWidth should comfortably fit 'Registro'");

                // Verify registers heading and executed counter label
                var titleLabel = (Label) ((VBox) regTable.getParent()).getChildren().get(0);
                assertEquals("REGISTROS", titleLabel.getText());

                viewModel.instructionsExecutedProperty().set(142);
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                var executedLabel = (Label) ((VBox) regTable.getParent()).getChildren().get(4);
                assertTrue(executedLabel.getText().contains("142"), "Executed counter must reflect count");
            } finally {
                stage.close();
            }
            return null;
        });
    }

    @Test
    void sixteenHexDigitsAndTwentyDecimalDigitsIn64BitNativeMode() throws Exception {
        FxDialogs.onFxThread(() -> {
            var viewModel = new DebugViewModel();
            var localization = new Localization();
            var view = new DebuggerPanelView(viewModel, localization);
            var stage = new Stage();
            var scene = new Scene(view, 960, 450);
            WorkbenchTheme.apply(view, stage);
            stage.setScene(scene);
            stage.show();

            try {
                // Supply maximum 64-bit unsigned value (0xFFFFFFFFFFFFFFFFL = 18446744073709551615, 20 decimal digits)
                Map<String, Long> regs = Map.of(
                        "RAX", 0xFFFFFFFFFFFFFFFFL,
                        "RBX", 0x123456789ABCDEF0L,
                        "RCX", 100L,
                        "RDX", 0L
                );
                viewModel.updateRegisters(RegisterState.fromExtended(regs));

                scene.getRoot().applyCss();
                scene.getRoot().layout();

                assertTrue(viewModel.nativeSessionProperty().get(), "Extended registers must trigger native session");

                @SuppressWarnings("unchecked")
                var regTable = (TableView<RegisterItemViewModel>) scene.lookup("#debugger-registers-table");
                assertNotNull(regTable);

                TableColumn<RegisterItemViewModel, ?> binCol = regTable.getColumns().get(3);
                TableColumn<RegisterItemViewModel, ?> hexCol = regTable.getColumns().get(1);
                TableColumn<RegisterItemViewModel, ?> decCol = regTable.getColumns().get(2);

                // Binary column must be hidden in 64-bit mode to preserve horizontal room
                assertFalse(binCol.isVisible(), "Binary column must be hidden in 64-bit native mode");

                // Columns for hex and dec must allocate enough space for 16 hex and 20 dec digits
                assertTrue(hexCol.getMinWidth() >= 130.0, "Hex column minWidth must fit 16 hex digits (>= 130px)");
                assertTrue(hexCol.getPrefWidth() >= 145.0, "Hex column prefWidth must accommodate 16 hex digits");
                assertTrue(decCol.getMinWidth() >= 150.0, "Dec column minWidth must fit 20 unsigned decimal digits (>= 150px)");
                assertTrue(decCol.getPrefWidth() >= 165.0, "Dec column prefWidth must accommodate 20 decimal digits");

                RegisterItemViewModel rax = viewModel.getRegisterList().stream()
                        .filter(r -> "RAX".equals(r.getName()))
                        .findFirst()
                        .orElseThrow();
                assertEquals("FFFFFFFFFFFFFFFF", rax.getHexValue(), "Full 16 hex digits must be preserved");
                assertEquals("18446744073709551615", rax.getDecValue(), "Full 20 unsigned decimal digits must be preserved");

                // Check rendered cells in table scene graph
                TableCell<?, ?> renderedHexCell = (TableCell<?, ?>) regTable.lookupAll(".table-cell").stream()
                        .filter(c -> ((TableCell<?, ?>) c).getText() != null && ((TableCell<?, ?>) c).getText().contains("FFFFFFFFFFFFFFFF"))
                        .findFirst()
                        .orElseThrow();
                assertNotNull(renderedHexCell.getTooltip(), "Hex cell must have tooltip installed for full value accessibility");
                assertEquals("FFFFFFFFFFFFFFFF", renderedHexCell.getTooltip().getText());

                TableCell<?, ?> renderedDecCell = (TableCell<?, ?>) regTable.lookupAll(".table-cell").stream()
                        .filter(c -> ((TableCell<?, ?>) c).getText() != null && ((TableCell<?, ?>) c).getText().contains("18446744073709551615"))
                        .findFirst()
                        .orElseThrow();
                assertNotNull(renderedDecCell.getTooltip(), "Dec cell must have tooltip installed for full value accessibility");
                assertEquals("18446744073709551615", renderedDecCell.getTooltip().getText());

                // Test register changed highlighting (bold changed values)
                Map<String, Long> changedRegs = Map.of(
                        "RAX", 0x5555555555555555L,
                        "RBX", 0x123456789ABCDEF0L,
                        "RCX", 100L,
                        "RDX", 0L
                );
                viewModel.updateRegisters(RegisterState.fromExtended(changedRegs));
                assertTrue(rax.isChanged(), "RAX should be marked changed after value mutation");

                scene.getRoot().applyCss();
                scene.getRoot().layout();

                TableCell<?, ?> changedCell = (TableCell<?, ?>) regTable.lookupAll(".table-cell").stream()
                        .filter(c -> ((TableCell<?, ?>) c).getText() != null && ((TableCell<?, ?>) c).getText().contains("5555555555555555"))
                        .findFirst()
                        .orElseThrow();
                assertTrue(changedCell.getStyleClass().contains("reg-changed"),
                        "TableCell must acquire 'reg-changed' class when register is changed");
            } finally {
                stage.close();
            }
            return null;
        });
    }

    @Test
    void narrowLayoutAndInspectionTabsAccess() throws Exception {
        FxDialogs.onFxThread(() -> {
            var viewModel = new DebugViewModel();
            var localization = new Localization();
            var view = new DebuggerPanelView(viewModel, localization);
            var stage = new Stage();
            var scene = new Scene(view, 680, 380);
            WorkbenchTheme.apply(view, stage);
            stage.setScene(scene);
            stage.show();

            try {
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                var tabs = (TabPane) scene.lookup("#debugger-inspection-tabs");
                assertNotNull(tabs);
                assertEquals(5, tabs.getTabs().size());

                // Ensure all five views can be selected and lay out without throwing errors
                for (int i = 0; i < 5; i++) {
                    tabs.getSelectionModel().select(i);
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                    var content = tabs.getTabs().get(i).getContent();
                    assertNotNull(content, "Tab " + i + " content must be present");
                    assertTrue(content.isVisible(), "Active tab content must be visible");
                }

                // Disassembly line styling
                tabs.getSelectionModel().select(0);
                @SuppressWarnings("unchecked")
                var disasmList = (ListView<DisasmLine>) scene.lookup("#debugger-disasm-list");
                assertNotNull(disasmList);
                viewModel.getDisassembly().setAll(List.of(
                        new DisasmLine("0710:0100", "B8 01 00", "MOV AX, 0001", true),
                        new DisasmLine("0710:0103", "BB 02 00", "MOV BX, 0002", false)
                ));
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                ListCell<?> currentCell = (ListCell<?>) disasmList.lookupAll(".list-cell").stream()
                        .filter(c -> ((ListCell<?>) c).getText() != null && ((ListCell<?>) c).getText().contains("MOV AX, 0001"))
                        .findFirst()
                        .orElseThrow();
                assertTrue(currentCell.getStyleClass().contains("disasm-current"),
                        "Current executing disassembly line must have disasm-current style");

                // Memory hex dump area
                tabs.getSelectionModel().select(1); // Memory tab
                var dumpArea = (TextArea) scene.lookup("#debugger-memory-dump-area");
                assertNotNull(dumpArea);
                viewModel.memoryDumpTextProperty().set("0710:0100  48 65 6C 6C 6F 20 57 6F  72 6C 64 21 0D 0A 24 00  Hello World!..$.");
                assertEquals("0710:0100  48 65 6C 6C 6F 20 57 6F  72 6C 64 21 0D 0A 24 00  Hello World!..$.",
                        dumpArea.getText());
            } finally {
                stage.close();
            }
            return null;
        });
    }

    @Test
    void capabilityGatingAndExternalNotice() throws Exception {
        FxDialogs.onFxThread(() -> {
            var viewModel = new DebugViewModel();
            var localization = new Localization();
            var view = new DebuggerPanelView(viewModel, localization);
            var stage = new Stage();
            var scene = new Scene(view, 960, 450);
            WorkbenchTheme.apply(view, stage);
            stage.setScene(scene);
            stage.show();

            try {
                var splitPane = (SplitPane) view.getChildren().stream()
                        .filter(n -> n instanceof SplitPane).findFirst().orElseThrow();
                var notice = (VBox) view.getChildren().stream()
                        .filter(n -> n instanceof VBox).findFirst().orElseThrow();

                // Initially idle: panels visible, notice hidden
                assertTrue(splitPane.isVisible());
                assertFalse(notice.isVisible());

                // External debugger launch-only session (e.g. Turbo Debugger in DOSBox)
                viewModel.activeProperty().set(true);
                viewModel.integratedProperty().set(false);
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                assertFalse(splitPane.isVisible(), "Panels must hide during external launch-only debug");
                assertTrue(notice.isVisible(), "Notice must display during external launch-only debug");

                Label noticeLabel = (Label) notice.getChildren().get(0);
                assertTrue(noticeLabel.getText().contains("Turbo Debugger"),
                        "Notice must explain external session limitations in English");

                // Language switch to Spanish updates notice
                localization.localeProperty().set(Locale.of("es"));
                assertTrue(noticeLabel.getText().contains("Turbo Debugger"),
                        "Spanish notice must be active");

                // Integrated session (built-in emulator or integrated GDB)
                viewModel.integratedProperty().set(true);
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                assertTrue(splitPane.isVisible(), "Panels must display for integrated session");
                assertFalse(notice.isVisible(), "Notice must hide for integrated session");

                // Control buttons gating:
                var resumeBtn = (Button) scene.lookup("#debugger-resume-btn");
                var pauseBtn = (Button) scene.lookup("#debugger-pause-btn");
                var stepOverBtn = (Button) scene.lookup("#debugger-step-over-btn");
                var stopBtn = (Button) scene.lookup("#debugger-stop-btn");
                var readBtn = (Button) scene.lookup("#debugger-memory-read-btn");

                // While running (paused = false): step and resume disabled, pause enabled if canPause
                viewModel.pausedProperty().set(false);
                viewModel.canPauseProperty().set(true);
                viewModel.canStepProperty().set(true);
                assertTrue(resumeBtn.isDisabled(), "Resume must be disabled while running");
                assertTrue(stepOverBtn.isDisabled(), "Step must be disabled while running");
                assertFalse(pauseBtn.isDisabled(), "Pause must be enabled when running and canPause is true");
                assertFalse(stopBtn.isDisabled(), "Stop must be enabled when session is active");

                // When paused: step and resume enabled, pause disabled
                viewModel.pausedProperty().set(true);
                assertFalse(resumeBtn.isDisabled(), "Resume must be enabled when paused and canStep is true");
                assertFalse(stepOverBtn.isDisabled(), "Step must be enabled when paused and canStep is true");
                assertTrue(pauseBtn.isDisabled(), "Pause must be disabled when already paused");

                // Memory read button capability gating
                viewModel.canReadMemoryProperty().set(false);
                assertTrue(readBtn.isDisabled(), "Read memory button must be disabled when capability is false");
                viewModel.canReadMemoryProperty().set(true);
                assertFalse(readBtn.isDisabled(), "Read memory button must be enabled when capability is true");
            } finally {
                stage.close();
            }
            return null;
        });
    }

    @Test
    void callbackWiringAndInteractions() throws Exception {
        FxDialogs.onFxThread(() -> {
            var viewModel = new DebugViewModel();
            var localization = new Localization();
            var view = new DebuggerPanelView(viewModel, localization);
            var stage = new Stage();
            var scene = new Scene(view, 960, 450);
            WorkbenchTheme.apply(view, stage);
            stage.setScene(scene);
            stage.show();

            try {
                // 1. Test Restart callback wiring
                var restartFired = new AtomicBoolean(false);
                view.setOnRestartRequested(() -> restartFired.set(true));
                viewModel.activeProperty().set(true);
                var restartBtn = (Button) scene.lookup("#debugger-restart-btn");
                assertNotNull(restartBtn);
                restartBtn.fire();
                assertTrue(restartFired.get(), "Restart button click must invoke onRestartRequested callback");

                // 2. Test Breakpoint selected callback
                var selectedBp = new AtomicReference<BreakpointItemViewModel>();
                view.setOnBreakpointSelected(selectedBp::set);

                var tabs = (TabPane) scene.lookup("#debugger-inspection-tabs");
                tabs.getSelectionModel().select(3); // Breakpoints tab
                var bp = new Breakpoint("main.asm", 12, true);
                viewModel.setBreakpoints(List.of(bp));

                scene.getRoot().applyCss();
                scene.getRoot().layout();

                @SuppressWarnings("unchecked")
                var bpTable = (TableView<BreakpointItemViewModel>) scene.lookup("#debugger-breakpoints-table");
                assertNotNull(bpTable);

                @SuppressWarnings("unchecked")
                TableRow<BreakpointItemViewModel> bpRow = (TableRow<BreakpointItemViewModel>) bpTable.lookupAll(".table-row-cell").stream()
                        .filter(r -> !((TableRow<?>) r).isEmpty())
                        .findFirst()
                        .orElseThrow();
                var clickEvent = new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                        MouseButton.PRIMARY, 2, false, false, false, false,
                        true, false, false, false, false, false, null);
                bpRow.getOnMouseClicked().handle(clickEvent);
                assertNotNull(selectedBp.get(), "Double click on breakpoint row must invoke onBreakpointSelected");
                assertEquals("main.asm", selectedBp.get().getPath());
                assertEquals(12, selectedBp.get().getLine());

                // 3. Test Call Frame selected callback
                var selectedFrame = new AtomicReference<CallFrameItemViewModel>();
                view.setOnCallFrameSelected(selectedFrame::set);
                tabs.getSelectionModel().select(2); // Call Stack tab

                var frameVm = CallFrameItemViewModel.of(new CallFrame(0, "0x1000", "my_func", "main.asm", 25));
                viewModel.getStackLines().setAll(List.of(frameVm));

                scene.getRoot().applyCss();
                scene.getRoot().layout();

                @SuppressWarnings("unchecked")
                var stackList = (ListView<CallFrameItemViewModel>) scene.lookup("#debugger-stack-list");
                assertNotNull(stackList);
                @SuppressWarnings("unchecked")
                ListCell<CallFrameItemViewModel> frameCell = (ListCell<CallFrameItemViewModel>) stackList.lookupAll(".list-cell").stream()
                        .filter(c -> {
                            Object item = ((ListCell<?>) c).getItem();
                            return item instanceof CallFrameItemViewModel cf && cf.isNavigable();
                        })
                        .findFirst()
                        .orElseThrow();
                frameCell.getOnMouseClicked().handle(clickEvent);
                assertNotNull(selectedFrame.get(), "Double click on call frame must invoke onCallFrameSelected");
                assertEquals("main.asm", selectedFrame.get().getFile());
                assertEquals(25, selectedFrame.get().getLine());

                // 4. Test Watch add and remove interactions
                tabs.getSelectionModel().select(4); // Watches tab
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                var exprField = (TextField) scene.lookup("#debugger-watch-expr-field");
                var addBtn = (Button) scene.lookup("#debugger-watch-add-btn");
                var removeBtn = (Button) scene.lookup("#debugger-watch-remove-btn");
                @SuppressWarnings("unchecked")
                var watchTable = (TableView<WatchItemViewModel>) scene.lookup("#debugger-watches-table");

                exprField.setText("DS:0100");
                addBtn.fire();
                assertEquals(1, viewModel.getWatches().size(), "Watch expression should be added");
                assertEquals("DS:0100", viewModel.getWatches().get(0).getExpression());

                watchTable.getSelectionModel().select(0);
                assertFalse(removeBtn.isDisabled(), "Remove button should be enabled when a watch is selected");
                removeBtn.fire();
                assertEquals(0, viewModel.getWatches().size(), "Watch expression should be removed");

                // 5. Test Register context menu copy actions
                @SuppressWarnings("unchecked")
                var regTable = (TableView<RegisterItemViewModel>) scene.lookup("#debugger-registers-table");
                assertNotNull(regTable.getContextMenu(), "Register table must have context menu");
                assertEquals(3, regTable.getContextMenu().getItems().size(), "Context menu must have 3 copy items");
            } finally {
                stage.close();
            }
            return null;
        });
    }
}
