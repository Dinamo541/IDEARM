package io.github.dinamo541.idearm.app.view;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.ui.BrandLogo;
import io.github.dinamo541.idearm.app.ui.WorkbenchIcons;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Actionable, keyboard-accessible landing screen shown when no files are open. */
final class WelcomeView extends VBox {
    enum Action { NEW_PROJECT, OPEN_PROJECT, RECENT, COMMANDS, DICTIONARY }

    WelcomeView(Localization text, Consumer<Action> action) {
        getStyleClass().add("editor-welcome");
        setAlignment(Pos.CENTER_LEFT);
        setMaxHeight(USE_PREF_SIZE);
        var title = new Label("IDEARM");
        title.getStyleClass().add("welcome-title");
        var subtitle = label(text, "welcome.subtitle", "welcome-hint");
        subtitle.setWrapText(true);
        var brand = new HBox(BrandLogo.create(56), new VBox(3, title, subtitle));
        brand.getStyleClass().add("welcome-brand");
        var start = new VBox(2, label(text, "welcome.start", "welcome-section-title"),
                entry(text, "welcome.newProject", "Ctrl+Shift+N", WorkbenchIcons.PLUS, () -> action.accept(Action.NEW_PROJECT)),
                entry(text, "welcome.openProject", "Ctrl+K Ctrl+O", WorkbenchIcons.FOLDER, () -> action.accept(Action.OPEN_PROJECT)),
                entry(text, "welcome.recent", "Ctrl+R", WorkbenchIcons.HISTORY, () -> action.accept(Action.RECENT)));
        var discover = new VBox(2, label(text, "welcome.discover", "welcome-section-title"),
                entry(text, "welcome.commands", "Ctrl+Shift+P", WorkbenchIcons.SEARCH, () -> action.accept(Action.COMMANDS)),
                entry(text, "welcome.dictionary", "Shift+F1", WorkbenchIcons.BOOK, () -> action.accept(Action.DICTIONARY)));
        var footer = label(text, "welcome.footer", "welcome-footer");
        footer.setWrapText(true);
        getChildren().addAll(brand, start, discover, footer);
    }

    private static Label label(Localization text, String key, String style) {
        var label = new Label();
        label.textProperty().bind(text.text(key));
        label.getStyleClass().add(style);
        return label;
    }

    private static Button entry(Localization text, String key, String shortcut, WorkbenchIcons icon, Runnable action) {
        var name = label(text, key, "welcome-action-label");
        var space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        var keys = new Label(shortcut);
        keys.getStyleClass().add("keycap");
        var row = new HBox(12, icon.create(18), name, space, keys);
        row.setAlignment(Pos.CENTER_LEFT);
        var button = new Button();
        button.setMaxWidth(Double.MAX_VALUE);
        button.setGraphic(row);
        row.prefWidthProperty().bind(button.widthProperty().subtract(26));
        button.getStyleClass().add("welcome-action");
        button.accessibleTextProperty().bind(text.text(key));
        button.setOnAction(event -> action.run());
        return button;
    }
}
