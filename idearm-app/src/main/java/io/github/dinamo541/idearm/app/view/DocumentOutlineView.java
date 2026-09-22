package io.github.dinamo541.idearm.app.view;

import atlantafx.base.theme.Styles;
import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.application.editor.OutlineItem;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Visual document outline tree showing procedures, labels, and segments in the active file.
 */
public final class DocumentOutlineView extends BorderPane {

    private final Localization localization;
    private final TreeView<OutlineItem> treeView;
    private Consumer<OutlineItem> onItemSelected;

    public DocumentOutlineView(Localization localization) {
        this.localization = Objects.requireNonNull(localization, "localization cannot be null");

        // Header
        var header = new Label();
        header.textProperty().bind(localization.text("outline.title"));
        header.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_SMALL);
        header.setPadding(new Insets(6, 12, 6, 12));
        getStyleClass().add("document-outline");
        setTop(header);

        this.treeView = new TreeView<>();
        this.treeView.setShowRoot(false);
        this.treeView.setCellFactory(tv -> new OutlineTreeCell());

        var placeholder = new Label();
        placeholder.textProperty().bind(localization.text("outline.empty"));
        placeholder.getStyleClass().add(Styles.TEXT_MUTED);
        placeholder.visibleProperty().bind(treeView.rootProperty().isNull());

        // Double click or single click to jump to symbol
        this.treeView.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 1) {
                TreeItem<OutlineItem> item = treeView.getSelectionModel().getSelectedItem();
                if (item != null && item.getValue() != null && onItemSelected != null) {
                    onItemSelected.accept(item.getValue());
                }
            }
        });

        var centerStack = new javafx.scene.layout.StackPane(placeholder, treeView);
        setCenter(centerStack);
    }

    public void setOnItemSelected(Consumer<OutlineItem> listener) {
        this.onItemSelected = listener;
    }

    public void updateOutline(List<OutlineItem> items) {
        if (items == null || items.isEmpty()) {
            treeView.setRoot(null);
            return;
        }

        TreeItem<OutlineItem> root = new TreeItem<>(new OutlineItem("root", "root", 0, 0, List.of()));
        for (OutlineItem item : items) {
            root.getChildren().add(buildTreeItem(item));
        }

        root.setExpanded(true);
        treeView.setRoot(root);
    }

    private TreeItem<OutlineItem> buildTreeItem(OutlineItem item) {
        TreeItem<OutlineItem> ti = new TreeItem<>(item);
        if (item.children() != null && !item.children().isEmpty()) {
            for (OutlineItem child : item.children()) {
                ti.getChildren().add(buildTreeItem(child));
            }
            ti.setExpanded(true);
        }
        return ti;
    }

    private static final class OutlineTreeCell extends TreeCell<OutlineItem> {

        private final HBox root;
        private final Label badge;
        private final Label nameLabel;
        private final Label lineLabel;

        OutlineTreeCell() {
            this.badge = new Label();
            this.badge.setStyle(
                    "-fx-font-family: 'JetBrains Mono', monospace; " +
                    "-fx-font-size: 9px; " +
                    "-fx-font-weight: bold; " +
                    "-fx-padding: 1 4; " +
                    "-fx-background-radius: 3px;"
            );

            this.nameLabel = new Label();
            this.nameLabel.setStyle("-fx-font-family: 'JetBrains Mono', 'Consolas', monospace; -fx-font-size: 11px;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            this.lineLabel = new Label();
            this.lineLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;");

            this.root = new HBox(6, badge, nameLabel, spacer, lineLabel);
            this.root.setPadding(new Insets(2, 4, 2, 4));
        }

        @Override
        protected void updateItem(OutlineItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                nameLabel.setText(item.name());
                lineLabel.setText("Ln " + item.line());

                switch (item.kind()) {
                    case "procedure" -> {
                        badge.setText("PROC");
                        badge.setStyle(badge.getStyle() + "-fx-background-color: -color-warning-subtle; -fx-text-fill: -color-warning-fg;");
                    }
                    case "segment" -> {
                        badge.setText("SEG");
                        badge.setStyle(badge.getStyle() + "-fx-background-color: rgba(180, 100, 255, 0.2); -fx-text-fill: #a855f7;");
                    }
                    default -> {
                        badge.setText("LBL");
                        badge.setStyle(badge.getStyle() + "-fx-background-color: -color-accent-subtle; -fx-text-fill: -color-accent-fg;");
                    }
                }

                setText(null);
                setGraphic(root);
            }
        }
    }
}
