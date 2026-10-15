package io.github.dinamo541.idearm.app.ui;

import io.github.dinamo541.idearm.app.i18n.Localization;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

/** Delayed descriptions and keyboard hints also provide accessible button names. */
public final class HoverHelp {
    private HoverHelp() {}
    public static void install(ButtonBase button, Localization localization, String key, String shortcut) {
        var tip = new Tooltip();
        tip.textProperty().bind(localization.text(key).concat(shortcut.isBlank() ? "" : "\n" + shortcut));
        button.accessibleTextProperty().bind(localization.text(key));
        tip.setShowDelay(Duration.millis(450)); tip.setHideDelay(Duration.millis(100));
        tip.setShowDuration(Duration.seconds(20)); tip.setWrapText(true); tip.setMaxWidth(330);
        tip.setOnShowing(event -> {
            boolean light = button.getScene() != null && button.getScene().getRoot().getStyleClass().contains("light");
            // Inline, because a tooltip's popup does not always inherit the workbench sheet; same values as the tokens.
            tip.setStyle("-fx-font-family: 'Inter'; -fx-font-size: 12px; -fx-padding: 8 11; -fx-line-spacing: 2;"
                    + "-fx-background-radius: 7; -fx-border-radius: 7; -fx-border-width: 1;"
                    + (light ? "-fx-background-color: #ffffff; -fx-text-fill: #1e2633; -fx-border-color: #b9c3d3;"
                            + "-fx-effect: dropshadow(gaussian, rgba(23, 35, 60, 0.18), 14, 0.1, 0, 4);"
                    : "-fx-background-color: #1a2130; -fx-text-fill: #d5ddea; -fx-border-color: #364158;"
                            + "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.55), 14, 0.1, 0, 4);"));
        });
        button.setTooltip(tip);
    }
}
