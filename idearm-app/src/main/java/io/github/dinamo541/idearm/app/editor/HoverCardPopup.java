package io.github.dinamo541.idearm.app.editor;

import atlantafx.base.theme.Styles;
import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.application.editor.HoverInfo;
import io.github.dinamo541.idearm.application.editor.HoverKind;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;

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

    private Scene installedScene;
    private Window installedWindow;
    private EventHandler<MouseEvent> outsideClickFilter;
    private ChangeListener<Boolean> windowFocusListener;

    public HoverCardPopup() {
        setAutoHide(true);
        setHideOnEscape(true);
        setOnHiding(event -> cleanUpListeners());

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
                "-fx-font-family: 'Consolas'; " +
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
                "-fx-font-family: 'Consolas'; " +
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
                "-fx-font-family: 'Consolas'; " +
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
        io.github.dinamo541.idearm.app.ui.WorkbenchTheme.apply(rootBox,
                owner.getScene() == null ? null : owner.getScene().getWindow());

        titleLabel.setText(info.title());
        updateBadge(info.kind(), localization);

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

        if (info.secondary() != null) {
            HoverInfo sec = info.secondary();
            String secPrefix;
            if (sec.kind() == HoverKind.INTERRUPT_SERVICE || sec.kind() == HoverKind.DIRECTIVE) {
                secPrefix = sec.title();
            } else {
                secPrefix = (localization != null)
                        ? localization.get("hover.alsoInstruction", sec.title())
                        : ("Also x86 instruction: " + sec.title());
            }
            Label secTitle = new Label(secPrefix);
            secTitle.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_SMALL);
            secTitle.setStyle("-fx-font-size: 11px;");

            VBox secBox = new VBox(4, secTitle);
            if (sec.syntax() != null && !sec.syntax().isBlank()) {
                Label secSyntax = new Label(sec.syntax());
                secSyntax.setStyle("-fx-font-family: 'Consolas'; -fx-background-color: -color-bg-subtle; -fx-padding: 3 6; -fx-background-radius: 4px; -fx-font-size: 10px;");
                secBox.getChildren().add(secSyntax);
            }
            if (sec.description() != null && !sec.description().isBlank()) {
                Label secDesc = new Label(sec.description());
                secDesc.setWrapText(true);
                secDesc.setStyle("-fx-font-size: 11px; -fx-line-spacing: 1px;");
                secBox.getChildren().add(secDesc);
            }
            if (sec.example() != null && !sec.example().isBlank()) {
                Label secExTitle = new Label(localization != null ? localization.get("hover.example") : "Example:");
                secExTitle.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_SMALL);
                secExTitle.setStyle("-fx-font-size: 10px;");
                Label secExample = new Label(sec.example());
                secExample.setStyle("-fx-font-family: 'Consolas'; -fx-background-color: -color-bg-subtle; -fx-padding: 3 6; -fx-background-radius: 4px; -fx-font-size: 10px;");
                secBox.getChildren().addAll(secExTitle, secExample);
            }
            rootBox.getChildren().addAll(new Separator(), secBox);
        }

        if (isShowing()) {
            hide();
        }
        show(owner, screenX, screenY);
        installDismissListeners(owner);
    }

    private void installDismissListeners(Node owner) {
        cleanUpListeners();

        this.installedScene = owner.getScene();
        this.installedWindow = installedScene != null ? installedScene.getWindow() : null;

        if (installedScene != null) {
            outsideClickFilter = event -> {
                if (!isShowing()) {
                    return;
                }
                double x = event.getScreenX();
                double y = event.getScreenY();
                double px = getX();
                double py = getY();
                double pw = getWidth();
                double ph = getHeight();
                if (x < px || x > px + pw || y < py || y > py + ph) {
                    hide();
                }
            };
            installedScene.addEventFilter(MouseEvent.MOUSE_PRESSED, outsideClickFilter);
        }

        if (installedWindow != null) {
            windowFocusListener = (obs, oldVal, newVal) -> {
                if (!newVal) {
                    hide();
                }
            };
            installedWindow.focusedProperty().addListener(windowFocusListener);
        }
    }

    @Override
    public void hide() {
        cleanUpListeners();
        super.hide();
    }

    private void cleanUpListeners() {
        if (installedScene != null && outsideClickFilter != null) {
            installedScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClickFilter);
            outsideClickFilter = null;
        }
        if (installedWindow != null && windowFocusListener != null) {
            installedWindow.focusedProperty().removeListener(windowFocusListener);
            windowFocusListener = null;
        }
        installedScene = null;
        installedWindow = null;
    }

    private void updateBadge(HoverKind kind, Localization localization) {
        String badgeKey = "hover.kind." + kind.name();
        String text;
        try {
            text = (localization != null) ? localization.get(badgeKey) : kind.name();
        } catch (Exception e) {
            text = kind.name();
        }
        kindBadge.setText(text);

        String bg;
        String fg;
        switch (kind) {
            case INSTRUCTION -> {
                bg = "-color-accent-subtle";
                fg = "-color-accent-fg";
            }
            case DIRECTIVE -> {
                bg = "-color-warning-subtle";
                fg = "-color-warning-fg";
            }
            case REGISTER -> {
                bg = "-color-success-subtle";
                fg = "-color-success-fg";
            }
            case NUMBER_CONVERSION -> {
                bg = "-color-neutral-subtle";
                fg = "-color-neutral-fg";
            }
            case INTERRUPT_SERVICE -> {
                bg = "-color-danger-subtle";
                fg = "-color-danger-fg";
            }
            case SYMBOL_INFO -> {
                bg = "-color-accent-subtle";
                fg = "-color-accent-fg";
            }
            case OPERATOR -> {
                bg = "-color-neutral-subtle";
                fg = "-color-neutral-fg";
            }
            case PREDEFINED_SYMBOL, KEYWORD, CONCEPT -> {
                bg = "-color-accent-subtle";
                fg = "-color-accent-fg";
            }
            case DIAGNOSTIC -> {
                bg = "-color-danger-subtle";
                fg = "-color-danger-fg";
            }
            default -> {
                bg = "-color-accent-subtle";
                fg = "-color-accent-fg";
            }
        }
        kindBadge.setStyle(
                "-fx-background-color: " + bg + "; " +
                "-fx-text-fill: " + fg + "; " +
                "-fx-padding: 2 6; " +
                "-fx-background-radius: 4px; " +
                "-fx-font-size: 10px; " +
                "-fx-font-weight: bold;"
        );
    }
}
