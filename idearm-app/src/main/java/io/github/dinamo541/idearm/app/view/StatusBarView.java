package io.github.dinamo541.idearm.app.view;

import atlantafx.base.theme.Styles;
import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.viewmodel.StatusBarViewModel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Visual status bar rendered at the bottom of the workbench window.
 */
public final class StatusBarView extends HBox {

    private final StatusBarViewModel viewModel;
    private final Localization localization;

    public StatusBarView(StatusBarViewModel viewModel, Localization localization) {
        this.viewModel = viewModel;
        this.localization = localization;

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(6);
        setPadding(new Insets(2, 8, 2, 8));
        getStyleClass().add("status-bar");

        // Status text on the left
        var statusLabel = new Label();
        statusLabel.textProperty().bind(localization.text(viewModel.statusProperty()));
        statusLabel.getStyleClass().addAll(Styles.TEXT_SMALL, "status-message", "status-item-pill");
        statusLabel.setMinWidth(0);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        statusLabel.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.ASSEMBLY.create());

        var spacer = new Region();
        spacer.setMinWidth(12);

        // Badges on the right
        var profileBadge = createBadge();
        profileBadge.textProperty().bind(viewModel.targetProfileProperty());
        profileBadge.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.CHIP.create());

        var toolchainBadge = createBadge();
        toolchainBadge.textProperty().bind(viewModel.toolchainProperty());

        var caretBadge = createBadge();
        caretBadge.textProperty().bind(localization.text(viewModel.caretPositionProperty()));

        var encodingBadge = createBadge();
        encodingBadge.textProperty().bind(viewModel.encodingProperty());
        encodingBadge.setCursor(javafx.scene.Cursor.HAND);
        encodingBadge.disableProperty().bind(viewModel.encodingProperty().isEmpty());
        encodingBadge.setOnMouseClicked(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY && !viewModel.encodingProperty().get().isEmpty()) {
                showEncodingContextMenu(encodingBadge, event.getScreenX(), event.getScreenY());
            }
        });
        encodingBadge.setOnContextMenuRequested(event -> {
            if (!viewModel.encodingProperty().get().isEmpty()) {
                showEncodingContextMenu(encodingBadge, event.getScreenX(), event.getScreenY());
            }
        });

        var profileSeparator = new Separator(javafx.geometry.Orientation.VERTICAL);
        var toolchainSeparator = new Separator(javafx.geometry.Orientation.VERTICAL);
        for (var node : new javafx.scene.Node[]{profileBadge, profileSeparator, toolchainBadge, toolchainSeparator, spacer}) {
            node.visibleProperty().bind(widthProperty().greaterThanOrEqualTo(700));
            node.managedProperty().bind(node.visibleProperty());
        }
        getChildren().addAll(
                statusLabel,
                spacer,
                profileBadge,
                profileSeparator,
                toolchainBadge,
                toolchainSeparator,
                caretBadge,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                encodingBadge
        );
    }

    private static Label createBadge() {
        var label = new Label();
        label.getStyleClass().addAll(Styles.TEXT_SMALL, "status-item-pill");
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private java.util.function.Consumer<java.nio.charset.Charset> onReopenWithEncoding;
    private java.util.function.Consumer<java.nio.charset.Charset> onSaveWithEncoding;

    public void setOnReopenWithEncoding(java.util.function.Consumer<java.nio.charset.Charset> onReopenWithEncoding) {
        this.onReopenWithEncoding = onReopenWithEncoding;
    }

    public void setOnSaveWithEncoding(java.util.function.Consumer<java.nio.charset.Charset> onSaveWithEncoding) {
        this.onSaveWithEncoding = onSaveWithEncoding;
    }

    private void showEncodingContextMenu(javafx.scene.Node anchor, double screenX, double screenY) {
        var menu = new javafx.scene.control.ContextMenu();

        var reopenMenu = new javafx.scene.control.Menu(localization.get("status.encoding.reopen"));
        for (var cs : io.github.dinamo541.idearm.domain.files.TextDecoding.COMMON_ENCODINGS) {
            var item = new javafx.scene.control.MenuItem(io.github.dinamo541.idearm.domain.files.TextDecoding.displayName(cs));
            item.setOnAction(e -> {
                if (onReopenWithEncoding != null) {
                    onReopenWithEncoding.accept(cs);
                }
            });
            reopenMenu.getItems().add(item);
        }

        var saveMenu = new javafx.scene.control.Menu(localization.get("status.encoding.saveWith"));
        for (var cs : io.github.dinamo541.idearm.domain.files.TextDecoding.COMMON_ENCODINGS) {
            var item = new javafx.scene.control.MenuItem(io.github.dinamo541.idearm.domain.files.TextDecoding.displayName(cs));
            item.setOnAction(e -> {
                if (onSaveWithEncoding != null) {
                    onSaveWithEncoding.accept(cs);
                }
            });
            saveMenu.getItems().add(item);
        }

        menu.getItems().addAll(reopenMenu, saveMenu);
        menu.show(anchor, screenX, screenY);
    }
}
