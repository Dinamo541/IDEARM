package io.github.dinamo541.idearm.app.editor;

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

        // The look lives in workbench.css (.hover-card and friends), which WorkbenchTheme applies on showing.
        this.rootBox = new VBox(8);
        this.rootBox.setPadding(new Insets(12, 14, 12, 14));
        this.rootBox.setMaxWidth(460);
        this.rootBox.getStyleClass().add("hover-card");

        // Header
        this.titleLabel = new Label();
        this.titleLabel.getStyleClass().add("hover-card-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        this.kindBadge = new Label();
        this.kindBadge.getStyleClass().add("kind-badge");

        HBox header = new HBox(8, titleLabel, spacer, kindBadge);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Syntax block
        this.syntaxLabel = new Label();
        this.syntaxLabel.getStyleClass().add("hover-card-code");
        this.syntaxLabel.setMaxWidth(Double.MAX_VALUE);
        this.syntaxLabel.setWrapText(true);

        // Description
        this.descLabel = new Label();
        this.descLabel.setWrapText(true);
        this.descLabel.setMaxWidth(420);
        this.descLabel.getStyleClass().add("hover-card-text");

        // Flags Box
        this.flagsTitle = new Label("Flags:");
        this.flagsTitle.getStyleClass().add("hover-card-section");
        this.flagsContent = new Label();
        this.flagsContent.getStyleClass().add("hover-card-code");
        this.flagsBox = new VBox(4, flagsTitle, flagsContent);

        // Example Box
        this.exampleTitle = new Label("Example:");
        this.exampleTitle.getStyleClass().add("hover-card-section");
        this.exampleContent = new Label();
        this.exampleContent.getStyleClass().add("hover-card-code");
        this.exampleBox = new VBox(4, exampleTitle, exampleContent);

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
            secTitle.getStyleClass().add("hover-card-section");

            VBox secBox = new VBox(4, secTitle);
            if (sec.syntax() != null && !sec.syntax().isBlank()) {
                Label secSyntax = new Label(sec.syntax());
                secSyntax.getStyleClass().addAll("hover-card-code", "small");
                secBox.getChildren().add(secSyntax);
            }
            if (sec.description() != null && !sec.description().isBlank()) {
                Label secDesc = new Label(sec.description());
                secDesc.setWrapText(true);
                secDesc.getStyleClass().add("hover-card-text");
                secBox.getChildren().add(secDesc);
            }
            if (sec.example() != null && !sec.example().isBlank()) {
                Label secExTitle = new Label(localization != null ? localization.get("hover.example") : "Example:");
                secExTitle.getStyleClass().add("hover-card-section");
                Label secExample = new Label(sec.example());
                secExample.getStyleClass().addAll("hover-card-code", "small");
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

        kindBadge.getStyleClass().removeAll("kind-warning", "kind-success", "kind-neutral", "kind-danger");
        String tone = switch (kind) {
            case INSTRUCTION -> null;
            case DIRECTIVE -> "kind-warning";
            case REGISTER -> "kind-success";
            case NUMBER_CONVERSION -> "kind-neutral";
            case INTERRUPT_SERVICE -> "kind-danger";
            case SYMBOL_INFO -> null;
            case OPERATOR -> "kind-neutral";
            case PREDEFINED_SYMBOL, KEYWORD, CONCEPT -> null;
            case DIAGNOSTIC -> "kind-danger";
            default -> null;
        };
        if (tone != null) kindBadge.getStyleClass().add(tone);
    }
}
