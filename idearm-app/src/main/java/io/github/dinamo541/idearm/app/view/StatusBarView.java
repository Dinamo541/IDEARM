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
        setSpacing(10);
        setPadding(new Insets(4, 12, 4, 12));
        getStyleClass().addAll("status-bar", Styles.BG_SUBTLE);

        // Status text on the left
        var statusLabel = new Label();
        statusLabel.textProperty().bind(localization.text(viewModel.statusProperty()));
        statusLabel.getStyleClass().add(Styles.TEXT_SMALL);

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Badges on the right
        var profileBadge = createBadge();
        profileBadge.textProperty().bind(viewModel.targetProfileProperty());

        var toolchainBadge = createBadge();
        toolchainBadge.textProperty().bind(viewModel.toolchainProperty());

        var caretBadge = createBadge();
        caretBadge.textProperty().bind(localization.text(viewModel.caretPositionProperty()));

        var encodingBadge = createBadge();
        encodingBadge.textProperty().bind(viewModel.encodingProperty());

        getChildren().addAll(
                statusLabel,
                spacer,
                profileBadge,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                toolchainBadge,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                caretBadge,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                encodingBadge
        );
    }

    private static Label createBadge() {
        var label = new Label();
        label.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        return label;
    }
}
