package io.github.dinamo541.idearm.app.view;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.ui.BrandLogo;
import io.github.dinamo541.idearm.app.ui.WorkbenchIcons;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Actionable, keyboard-accessible landing screen shown when no files are open.
 *
 * <p>Every entry is a card: an icon tile, the action, one line on what it is for, and its shortcut. The two groups
 * sit side by side and stack when the editor area is too narrow for both.
 */
final class WelcomeView extends VBox {
    enum Action { NEW_PROJECT, OPEN_PROJECT, RECENT, COMMANDS, DICTIONARY }

    /** Below this width the Explore group moves under the Start group. */
    private static final double TWO_COLUMNS = 640;

    WelcomeView(Localization text, Consumer<Action> action) {
        getStyleClass().add("editor-welcome");
        setAlignment(Pos.CENTER_LEFT);
        setMaxHeight(USE_PREF_SIZE);

        var title = new Label("IDEARM");
        title.getStyleClass().add("welcome-title");
        var subtitle = label(text, "welcome.subtitle", "welcome-hint");
        subtitle.setWrapText(true);
        var logo = BrandLogo.create(64);
        logo.getStyleClass().add("welcome-logo");
        var titles = new VBox(2, title, subtitle);
        titles.setMinWidth(0);
        var brand = new HBox(logo, titles);
        brand.getStyleClass().add("welcome-brand");

        var start = group(text, "welcome.start",
                entry(text, "welcome.newProject", "welcome.newProject.description", "Ctrl+Shift+N", WorkbenchIcons.PLUS, "tile-blue",
                        () -> action.accept(Action.NEW_PROJECT)),
                entry(text, "welcome.openProject", "welcome.openProject.description", "Ctrl+K Ctrl+O", WorkbenchIcons.FOLDER_OPEN, "tile-amber",
                        () -> action.accept(Action.OPEN_PROJECT)),
                entry(text, "welcome.recent", "welcome.recent.description", "Ctrl+R", WorkbenchIcons.HISTORY, "tile-mint",
                        () -> action.accept(Action.RECENT)));
        var discover = group(text, "welcome.discover",
                entry(text, "welcome.commands", "welcome.commands.description", "Ctrl+Shift+P", WorkbenchIcons.SEARCH, "tile-blue",
                        () -> action.accept(Action.COMMANDS)),
                entry(text, "welcome.dictionary", "welcome.dictionary.description", "Shift+F1", WorkbenchIcons.BOOK, "tile-violet",
                        () -> action.accept(Action.DICTIONARY)));

        var columns = new GridPane();
        columns.setHgap(20);
        columns.setVgap(20);
        columns.setMinWidth(0);
        Runnable arrange = () -> {
            columns.getChildren().clear();
            columns.getColumnConstraints().clear();
            if (getWidth() == 0 || getWidth() >= TWO_COLUMNS) {
                var half = new javafx.scene.layout.ColumnConstraints();
                half.setPercentWidth(50);
                columns.getColumnConstraints().addAll(half, half);
                columns.add(start, 0, 0);
                columns.add(discover, 1, 0);
            } else {
                var full = new javafx.scene.layout.ColumnConstraints();
                full.setPercentWidth(100);
                columns.getColumnConstraints().add(full);
                columns.add(start, 0, 0);
                columns.add(discover, 0, 1);
            }
        };
        arrange.run();
        widthProperty().addListener((obs, before, after) -> {
            boolean wasWide = before.doubleValue() == 0 || before.doubleValue() >= TWO_COLUMNS;
            boolean isWide = after.doubleValue() >= TWO_COLUMNS;
            if (wasWide != isWide) arrange.run();
        });

        var footer = label(text, "welcome.footer", "welcome-footer");
        footer.setWrapText(true);
        footer.setMinWidth(0);
        HBox.setHgrow(footer, Priority.ALWAYS);
        var tips = new HBox(WorkbenchIcons.SYSTEM_INTERRUPTS.create(16), footer);
        tips.getStyleClass().add("welcome-tips");
        tips.setMinWidth(0);

        getChildren().addAll(brand, columns, tips);
    }

    private static VBox group(Localization text, String titleKey, Button... entries) {
        var heading = new Label();
        var source = text.text(titleKey);
        heading.textProperty().bind(Bindings.createStringBinding(
                () -> source.get() == null ? "" : source.get().toUpperCase(text.localeProperty().get()),
                source, text.localeProperty()));
        heading.getStyleClass().add("welcome-section-title");
        var group = new VBox(8, heading);
        group.getChildren().addAll(entries);
        group.setMinWidth(0);
        GridPane.setHgrow(group, Priority.ALWAYS);
        GridPane.setValignment(group, javafx.geometry.VPos.TOP);
        return group;
    }

    private static Label label(Localization text, String key, String style) {
        var label = new Label();
        label.textProperty().bind(text.text(key));
        label.getStyleClass().add(style);
        return label;
    }

    private static Button entry(Localization text, String key, String descriptionKey, String shortcut, WorkbenchIcons icon,
                                String tone, Runnable action) {
        var name = label(text, key, "welcome-action-label");
        name.setMinWidth(0);
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        var description = label(text, descriptionKey, "welcome-action-description");
        description.setMinWidth(0);
        description.setTextOverrun(OverrunStyle.ELLIPSIS);
        var words = new VBox(2, name, description);
        words.setMinWidth(0);
        HBox.setHgrow(words, Priority.ALWAYS);

        var tile = new StackPane(icon.create(18));
        tile.getStyleClass().addAll("welcome-icon-tile", tone);

        var keys = new Label(shortcut);
        keys.getStyleClass().add("keycap");
        keys.setMinWidth(Region.USE_PREF_SIZE);

        var row = new HBox(12, tile, words, keys);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        var button = new Button();
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinWidth(0);
        button.setGraphic(row);
        row.prefWidthProperty().bind(button.widthProperty().subtract(30));
        row.maxWidthProperty().bind(row.prefWidthProperty());
        button.getStyleClass().add("welcome-action");
        button.accessibleTextProperty().bind(text.text(key));
        button.setOnAction(event -> action.run());
        return button;
    }
}
