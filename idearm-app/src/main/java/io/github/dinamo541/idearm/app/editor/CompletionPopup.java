package io.github.dinamo541.idearm.app.editor;

import atlantafx.base.theme.Styles;
import io.github.dinamo541.idearm.application.editor.CompletionItem;
import io.github.dinamo541.idearm.application.editor.CompletionKind;
import io.github.dinamo541.idearm.language.catalog.CpuLevel;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Popup;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Autocompletion dropdown popup offering instructions, registers, directives, and project symbols.
 */
public final class CompletionPopup extends Popup {

    private final ListView<CompletionItem> listView;
    private Consumer<CompletionItem> onSelected;

    public CompletionPopup() {
        setAutoHide(true);
        setHideOnEscape(true);

        this.listView = new ListView<>();
        this.listView.setPrefWidth(350);
        this.listView.setPrefHeight(220);
        this.listView.setMaxHeight(260);
        this.listView.getStyleClass().add(Styles.DENSE);
        this.listView.setStyle(
                "-fx-background-color: -color-bg-default; " +
                "-fx-border-color: -color-border-default; " +
                "-fx-border-width: 1px; " +
                "-fx-border-radius: 6px; " +
                "-fx-background-radius: 6px; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.35), 10, 0, 0, 4);"
        );

        this.listView.setCellFactory(lv -> new CompletionListCell());

        // Keyboard handling inside popup
        this.listView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
                commitSelection();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                hide();
                event.consume();
            }
        });

        // Mouse double/single click selection
        this.listView.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 1) {
                commitSelection();
            }
        });

        getContent().add(listView);
    }

    public void showCompletions(Node owner, double screenX, double screenY,
                                List<CompletionItem> items, Consumer<CompletionItem> onSelected) {
        Objects.requireNonNull(owner, "owner cannot be null");
        Objects.requireNonNull(items, "items cannot be null");

        if (items.isEmpty()) {
            hide();
            return;
        }

        this.onSelected = onSelected;
        this.listView.setItems(FXCollections.observableArrayList(items));
        this.listView.getSelectionModel().select(0);

        if (isShowing()) {
            hide();
        }
        show(owner, screenX, screenY);
        listView.requestFocus();
    }

    /**
     * Handles a key event dispatched from the editor when the popup is visible.
     *
     * @return true if the event was consumed by the completion popup.
     */
    public boolean handleEditorKeyEvent(KeyEvent event) {
        if (!isShowing()) {
            return false;
        }

        KeyCode code = event.getCode();
        if (code == KeyCode.UP) {
            int prev = Math.max(0, listView.getSelectionModel().getSelectedIndex() - 1);
            listView.getSelectionModel().select(prev);
            listView.scrollTo(prev);
            event.consume();
            return true;
        } else if (code == KeyCode.DOWN) {
            int next = Math.min(listView.getItems().size() - 1, listView.getSelectionModel().getSelectedIndex() + 1);
            listView.getSelectionModel().select(next);
            listView.scrollTo(next);
            event.consume();
            return true;
        } else if (code == KeyCode.ENTER || code == KeyCode.TAB) {
            commitSelection();
            event.consume();
            return true;
        } else if (code == KeyCode.ESCAPE) {
            hide();
            event.consume();
            return true;
        }
        return false;
    }

    private void commitSelection() {
        CompletionItem item = listView.getSelectionModel().getSelectedItem();
        if (item != null && onSelected != null) {
            hide();
            onSelected.accept(item);
        } else {
            hide();
        }
    }

    private static final class CompletionListCell extends ListCell<CompletionItem> {

        private final HBox root;
        private final Label badge;
        private final Label label;
        private final Label detail;
        private final Label cpuBadge;

        CompletionListCell() {
            this.badge = new Label();
            this.badge.setStyle(
                    "-fx-font-family: 'JetBrains Mono', monospace; " +
                    "-fx-font-size: 9px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-padding: 1 4; " +
                    "-fx-background-radius: 3px;"
            );

            this.label = new Label();
            this.label.setStyle("-fx-font-family: 'JetBrains Mono', 'Consolas', monospace; -fx-font-weight: bold; -fx-font-size: 11px;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            this.detail = new Label();
            this.detail.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;");

            this.cpuBadge = new Label();
            this.cpuBadge.setStyle(
                    "-fx-font-size: 9px; " +
                    "-fx-padding: 1 3; " +
                    "-fx-background-color: -color-warning-subtle; " +
                    "-fx-text-fill: -color-warning-fg; " +
                    "-fx-background-radius: 3px;"
            );

            this.root = new HBox(6, badge, label, spacer, detail, cpuBadge);
            this.root.setPadding(new Insets(2, 4, 2, 4));
        }

        @Override
        protected void updateItem(CompletionItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                label.setText(item.label());
                detail.setText(item.detail() != null ? item.detail() : "");

                configureBadge(item.kind());

                if (item.minCpu() != null && item.minCpu() != CpuLevel.CPU_8086) {
                    cpuBadge.setText(item.minCpu().name().replace("CPU_", ""));
                    cpuBadge.setVisible(true);
                    cpuBadge.setManaged(true);
                } else {
                    cpuBadge.setVisible(false);
                    cpuBadge.setManaged(false);
                }

                setText(null);
                setGraphic(root);
            }
        }

        private void configureBadge(CompletionKind kind) {
            switch (kind) {
                case INSTRUCTION -> {
                    badge.setText("INS");
                    badge.setStyle(badge.getStyle() + "-fx-background-color: -color-accent-subtle; -fx-text-fill: -color-accent-fg;");
                }
                case REGISTER -> {
                    badge.setText("REG");
                    badge.setStyle(badge.getStyle() + "-fx-background-color: -color-success-subtle; -fx-text-fill: -color-success-fg;");
                }
                case DIRECTIVE -> {
                    badge.setText("DIR");
                    badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(180, 100, 255, 0.2); -fx-text-fill: #a855f7;");
                }
                case PROCEDURE -> {
                    badge.setText("PROC");
                    badge.setStyle(badge.getStyle() + "-fx-background-color: -color-warning-subtle; -fx-text-fill: -color-warning-fg;");
                }
                case VARIABLE -> {
                    badge.setText("VAR");
                    badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(6, 182, 212, 0.2); -fx-text-fill: #06b6d4;");
                }
                case CONSTANT -> {
                    badge.setText("CONST");
                    badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(236, 72, 153, 0.2); -fx-text-fill: #ec4899;");
                }
                case LABEL -> {
                    badge.setText("LBL");
                    badge.setStyle(badge.getStyle() + "-fx-background-color: -color-bg-subtle; -fx-text-fill: -color-fg-default;");
                }
            }
        }
    }
}
