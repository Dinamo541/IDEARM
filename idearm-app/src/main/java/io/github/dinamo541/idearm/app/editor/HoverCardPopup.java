package io.github.dinamo541.idearm.app.editor;

import atlantafx.base.theme.Styles;
import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.application.editor.HoverInfo;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.Objects;

/**
 * Rich educational hover card popup displaying instruction documentation,
 * flags tables, numeric base conversions, or symbol declarations.
 */
public final class HoverCardPopup extends Popup {

    private final VBox rootBox;
    private final Label titleLabel;
    private final Label kindBadge;
    private final Label syntaxLabel;
    private final Label descLabel;
    private final VBox flagsBox;
    private final Label flagsTitle;
    private final Label flagsContent;
    private final VBox exampleBox;
    private final Label exampleTitle;
    private final Label exampleContent;

    public HoverCardPopup() {
        setAutoHide(true);
        setHideOnEscape(true);

        this.rootBox = new VBox(6);
        this.rootBox.setPadding(new Insets(10, 12, 10, 12));
        this.rootBox.setMaxWidth(440);
        this.rootBox.setStyle(
                "-fx-background-color: -color-bg-default; " +
                "-fx-border-color: -color-border-default; " +
                "-fx-border-width: 1px; " +
                "-fx-border-radius: 6px; " +
                "-fx-background-radius: 6px; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.35), 10, 0, 0, 4);"
        );

        // Header
        this.titleLabel = new Label();
        this.titleLabel.getStyleClass().addAll(Styles.TEXT_BOLD);
        this.titleLabel.setStyle("-fx-font-size: 13px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        this.kindBadge = new Label();
        this.kindBadge.setStyle(
                "-fx-background-color: -color-accent-subtle; " +
                "-fx-text-fill: -color-accent-fg; " +
                "-fx-padding: 2 6; " +
                "-fx-background-radius: 4px; " +
                "-fx-font-size: 10px; " +
                "-fx-font-weight: bold;"
        );

        HBox header = new HBox(8, titleLabel, spacer, kindBadge);

        // Syntax block
        this.syntaxLabel = new Label();
        this.syntaxLabel.setStyle(
                "-fx-font-family: 'JetBrains Mono', 'Consolas', monospace; " +
                "-fx-background-color: -color-bg-subtle; " +
                "-fx-padding: 4 8; " +
                "-fx-background-radius: 4px; " +
                "-fx-font-size: 11px;"
        );
        this.syntaxLabel.setWrapText(true);

        // Description
        this.descLabel = new Label();
        this.descLabel.setWrapText(true);
        this.descLabel.setMaxWidth(420);
        this.descLabel.setStyle("-fx-font-size: 12px; -fx-line-spacing: 2px;");

        // Flags Box
        this.flagsTitle = new Label("Flags:");
        this.flagsTitle.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_SMALL);
        this.flagsContent = new Label();
        this.flagsContent.setStyle(
                "-fx-font-family: 'JetBrains Mono', 'Consolas', monospace; " +
                "-fx-background-color: -color-bg-subtle; " +
                "-fx-padding: 4 8; " +
                "-fx-background-radius: 4px; " +
                "-fx-font-size: 11px;"
        );
        this.flagsBox = new VBox(3, flagsTitle, flagsContent);

        // Example Box
        this.exampleTitle = new Label("Example:");
        this.exampleTitle.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_SMALL);
        this.exampleContent = new Label();
        this.exampleContent.setStyle(
                "-fx-font-family: 'JetBrains Mono', 'Consolas', monospace; " +
                "-fx-background-color: -color-bg-subtle; " +
                "-fx-padding: 4 8; " +
                "-fx-background-radius: 4px; " +
                "-fx-font-size: 11px;"
        );
        this.exampleBox = new VBox(3, exampleTitle, exampleContent);

        getContent().add(rootBox);
    }

    public void showHover(Node owner, double screenX, double screenY, HoverInfo info, Localization localization) {
        Objects.requireNonNull(owner, "owner cannot be null");
        Objects.requireNonNull(info, "info cannot be null");

        titleLabel.setText(info.title());
        kindBadge.setText(info.kind().name());

        if (localization != null) {
            flagsTitle.setText(localization.get("hover.flags"));
            exampleTitle.setText(localization.get("hover.example"));
        }

        rootBox.getChildren().clear();
        rootBox.getChildren().add(titleLabel.getParent()); // header HBox

        if (info.syntax() != null && !info.syntax().isBlank()) {
            syntaxLabel.setText(info.syntax());
            rootBox.getChildren().add(syntaxLabel);
        }

        if (info.description() != null && !info.description().isBlank()) {
            descLabel.setText(info.description());
            rootBox.getChildren().add(descLabel);
        }

        if (info.flagsTable() != null && !info.flagsTable().isBlank()) {
            flagsContent.setText(info.flagsTable());
            rootBox.getChildren().addAll(new Separator(), flagsBox);
        }

        if (info.example() != null && !info.example().isBlank()) {
            exampleContent.setText(info.example());
            rootBox.getChildren().addAll(new Separator(), exampleBox);
        }

        if (isShowing()) {
            hide();
        }
        show(owner, screenX, screenY);
    }
}
