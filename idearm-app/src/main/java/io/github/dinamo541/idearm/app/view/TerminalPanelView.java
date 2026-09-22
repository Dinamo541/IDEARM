package io.github.dinamo541.idearm.app.view;

import atlantafx.base.theme.Styles;
import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.viewmodel.TerminalViewModel;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * View for the interactive host terminal tab.
 */
public final class TerminalPanelView extends BorderPane {

    private final TerminalViewModel viewModel;
    private final Localization localization;

    private final TextArea terminalArea = new TextArea();
    private final TextField inputField = new TextField();

    public TerminalPanelView(TerminalViewModel viewModel, Localization localization) {
        this.viewModel = viewModel;
        this.localization = localization;

        buildUi();
    }

    private void buildUi() {
        // Output Area
        terminalArea.setEditable(false);
        terminalArea.setWrapText(true);
        terminalArea.setStyle("-fx-font-family: 'JetBrains Mono', 'Consolas', monospace; -fx-font-size: 12px;");
        if (viewModel != null) {
            terminalArea.textProperty().bind(viewModel.terminalTextProperty());
            terminalArea.textProperty().addListener((obs, oldV, newV) -> terminalArea.setScrollTop(Double.MAX_VALUE));
        }
        setCenter(terminalArea);

        // Header toolbar with clear, restart, and status
        HBox headerBar = new HBox(8);
        headerBar.setAlignment(Pos.CENTER_LEFT);
        headerBar.setPadding(new Insets(4, 8, 4, 8));

        Label dirLabel = new Label();
        dirLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        if (viewModel != null) {
            dirLabel.textProperty().bind(viewModel.workingDirectoryProperty());
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button restartBtn = new Button();
        restartBtn.textProperty().bind(localization.text("terminal.restart"));
        io.github.dinamo541.idearm.app.ui.HoverHelp.install(restartBtn, localization, "terminal.restart.tooltip", "");
        restartBtn.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.RESTART.create());
        restartBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        if (viewModel != null) {
            restartBtn.setOnAction(e -> viewModel.restart());
        }

        Button clearBtn = new Button();
        clearBtn.textProperty().bind(localization.text("terminal.clear"));
        io.github.dinamo541.idearm.app.ui.HoverHelp.install(clearBtn, localization, "terminal.clear.tooltip", "");
        clearBtn.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.CLEAR.create());
        clearBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SMALL);
        if (viewModel != null) {
            clearBtn.setOnAction(e -> viewModel.clear());
        }

        Label statusBadge = new Label();
        statusBadge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.ROUNDED);
        statusBadge.setPadding(new Insets(2, 6, 2, 6));
        if (viewModel != null) {
            statusBadge.textProperty().bind(Bindings.createStringBinding(
                    () -> viewModel.isRunning()
                            ? localization.get("terminal.status.running")
                            : localization.get("terminal.status.stopped"),
                    viewModel.runningProperty(), localization.localeProperty()
            ));
            statusBadge.styleProperty().bind(Bindings.createStringBinding(
                    () -> viewModel.isRunning()
                            ? "-fx-background-color: -color-success-emphasis; -fx-text-fill: -color-fg-emphasis; -fx-font-weight: bold;"
                            : "-fx-background-color: -color-bg-subtle; -fx-text-fill: -color-fg-muted;",
                    viewModel.runningProperty()
            ));
        }

        headerBar.getChildren().addAll(dirLabel, spacer, statusBadge, restartBtn, clearBtn);
        setTop(headerBar);

        // Input row
        HBox inputRow = new HBox(6);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        inputRow.setPadding(new Insets(4, 6, 6, 6));

        Label promptSymbol = new Label("❯");
        promptSymbol.setStyle("-fx-font-weight: bold; -fx-text-fill: -color-accent-emphasis;");

        inputField.promptTextProperty().bind(localization.text("terminal.input.placeholder"));
        inputField.setStyle("-fx-font-family: 'JetBrains Mono', 'Consolas', monospace; -fx-font-size: 12px;");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        inputField.setOnKeyPressed(event -> {
            if (viewModel == null) return;
            if (event.getCode() == KeyCode.ENTER) {
                String cmd = inputField.getText();
                viewModel.sendCommand(cmd);
                inputField.clear();
                event.consume();
            } else if (event.getCode() == KeyCode.UP) {
                String prev = viewModel.getPreviousHistory();
                if (prev != null) {
                    inputField.setText(prev);
                    inputField.positionCaret(prev.length());
                }
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN) {
                String next = viewModel.getNextHistory();
                if (next != null) {
                    inputField.setText(next);
                    inputField.positionCaret(next.length());
                }
                event.consume();
            }
        });

        inputRow.getChildren().addAll(promptSymbol, inputField);
        setBottom(inputRow);
    }

    public void focusInput() {
        inputField.requestFocus();
    }
}
