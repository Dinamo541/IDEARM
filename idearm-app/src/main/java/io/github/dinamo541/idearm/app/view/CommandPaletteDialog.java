package io.github.dinamo541.idearm.app.view;

import atlantafx.base.theme.Styles;
import io.github.dinamo541.idearm.app.i18n.Localization;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * VS Code-style Command Palette modal dialog triggered by Ctrl+Shift+P or F1.
 * Supports quick search, arrow navigation, Enter to run, and Esc to dismiss.
 */
public final class CommandPaletteDialog extends Stage {

    public record CommandEntry(String category, String title, String shortcut, Runnable action) {
        public CommandEntry(String title, String shortcut, Runnable action) {
            this(null, title, shortcut, action);
        }

        @Override
        public String toString() {
            String prefix = category != null && !category.isBlank() ? "[" + category + "] " : "";
            return prefix + title + (shortcut != null && !shortcut.isBlank() ? " (" + shortcut + ")" : "");
        }
    }

    private final TextField searchField = new TextField();
    private final ListView<CommandEntry> commandList = new ListView<>();
    private final ObservableList<CommandEntry> masterCommands = FXCollections.observableArrayList();
    private final FilteredList<CommandEntry> filteredCommands = new FilteredList<>(masterCommands, p -> true);

    public CommandPaletteDialog(Window owner, Localization localization, List<CommandEntry> commands) {
        initOwner(owner);
        io.github.dinamo541.idearm.app.ui.BrandLogo.apply(this);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UNDECORATED);
        titleProperty().bind(localization.text("dialog.palette.title"));

        masterCommands.addAll(commands);

        buildUi(localization);
    }

    private void buildUi(Localization localization) {
        searchField.promptTextProperty().bind(localization.text("dialog.palette.placeholder"));
        searchField.setId("command-search");
        searchField.accessibleTextProperty().bind(localization.text("dialog.palette.placeholder"));

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredCommands.setPredicate(entry -> {
                if (newVal == null || newVal.isBlank()) {
                    return true;
                }
                String[] terms = newVal.trim().toLowerCase().split("\\s+");
                String title = entry.title() != null ? entry.title().toLowerCase() : "";
                String category = entry.category() != null ? entry.category().toLowerCase() : "";
                String shortcut = entry.shortcut() != null ? entry.shortcut().toLowerCase() : "";
                String combined = category + " " + title + " " + shortcut;

                for (String term : terms) {
                    if (!combined.contains(term)) {
                        return false;
                    }
                }
                return true;
            });
            if (!filteredCommands.isEmpty()) {
                commandList.getSelectionModel().select(0);
            }
        });

        commandList.setId("command-list");
        commandList.setFixedCellSize(32);
        var empty = new Label();
        empty.textProperty().bind(localization.text("dialog.palette.empty"));
        empty.getStyleClass().add("empty-description");
        commandList.setPlaceholder(empty);
        commandList.setItems(filteredCommands);
        commandList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CommandEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    var box = new HBox(8);
                    box.setAlignment(Pos.CENTER_LEFT);

                    if (item.category() != null && !item.category().isBlank()) {
                        var catBadge = new Label(item.category());
                        catBadge.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.ROUNDED);
                        catBadge.getStyleClass().add("command-category");
                        box.getChildren().add(catBadge);
                    }

                    var titleLbl = new Label(item.title());
                    titleLbl.setMinWidth(0);
                    titleLbl.setMaxWidth(Double.MAX_VALUE);
                    HBox.setHgrow(titleLbl, Priority.ALWAYS);
                    box.getChildren().add(titleLbl);

                    if (item.shortcut() != null && !item.shortcut().isBlank()) {
                        var shortcutLbl = new Label(item.shortcut());
                        shortcutLbl.getStyleClass().add("keycap");
                        shortcutLbl.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                        box.getChildren().add(shortcutLbl);
                    }

                    setGraphic(box);
                }
            }
        });

        if (!filteredCommands.isEmpty()) {
            commandList.getSelectionModel().select(0);
        }

        // Keyboard handling
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                int next = commandList.getSelectionModel().getSelectedIndex() + 1;
                if (next < filteredCommands.size()) {
                    commandList.getSelectionModel().select(next);
                    commandList.scrollTo(next);
                }
                event.consume();
            } else if (event.getCode() == KeyCode.UP) {
                int prev = commandList.getSelectionModel().getSelectedIndex() - 1;
                if (prev >= 0) {
                    commandList.getSelectionModel().select(prev);
                    commandList.scrollTo(prev);
                }
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                executeSelected();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                close();
                event.consume();
            }
        });

        commandList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                executeSelected();
            }
        });

        var hint = new Label();
        hint.textProperty().bind(localization.text("dialog.palette.hint"));
        hint.getStyleClass().add("picker-hint");
        var root = new VBox(8, searchField, commandList, hint);
        VBox.setVgrow(commandList, Priority.ALWAYS);
        root.setPadding(new Insets(12));

        root.getStyleClass().add("command-palette");
        io.github.dinamo541.idearm.app.ui.WorkbenchTheme.apply(root, getOwner());
        setScene(new Scene(root, 640, 400));
        getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) { close(); event.consume(); }
            else if (event.getCode() == KeyCode.ENTER && commandList.isFocused()) {
                executeSelected(); event.consume();
            }
        });
        setOnShowing(e -> { if (getOwner() != null) {
            setX(getOwner().getX() + (getOwner().getWidth() - 640) / 2);
            setY(getOwner().getY() + 64);
        }});
        setOnShown(e -> searchField.requestFocus());
    }

    private void executeSelected() {
        CommandEntry selected = commandList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            close();
            selected.action().run();
        }
    }
}
