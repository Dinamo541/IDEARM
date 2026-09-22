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
            tip.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 12px; -fx-padding: 9 12;"
                    + "-fx-background-radius: 4; -fx-border-radius: 4; -fx-border-width: 1;"
                    + (light ? "-fx-background-color: #ffffff; -fx-text-fill: #333333; -fx-border-color: #c8c8c8;"
                    : "-fx-background-color: #252526; -fx-text-fill: #eeeeee; -fx-border-color: #454545;"));
        });
        button.setTooltip(tip);
    }
}
