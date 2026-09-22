package io.github.dinamo541.idearm.app.view;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.ui.HoverHelp;
import io.github.dinamo541.idearm.app.ui.WorkbenchIcons;
import io.github.dinamo541.idearm.app.viewmodel.WorkbenchViewModel;
import io.github.dinamo541.idearm.domain.model.RecentItem;
import java.io.IOException;
import java.util.Locale;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.*;

/** Searchable project/file MRU picker, also usable entirely with the keyboard. */
public final class OpenRecentDialog extends Stage {
    private final WorkbenchViewModel model;
    private final Localization localization;
    private final ListView<RecentItem> list = new ListView<>();
    private final TextField search = new TextField();
    private final Label message = new Label();
    private final FilteredList<RecentItem> filtered;
    private RecentItem.Kind kind;

    public OpenRecentDialog(Window owner, WorkbenchViewModel model, Localization localization) {
        this.model = model; this.localization = localization;
        initOwner(owner); initModality(Modality.WINDOW_MODAL); initStyle(StageStyle.UNDECORATED);
        titleProperty().bind(localization.text("recent.title"));
        filtered = new FilteredList<>(model.getRecentItems().getItems());
        var title = new Label(); title.textProperty().bind(localization.text("recent.title"));
        title.getStyleClass().add("recent-heading");
        var close = new Button(null, WorkbenchIcons.CLOSE.create()); close.getStyleClass().add("icon-button");
        HoverHelp.install(close, localization, "recent.close", "Esc"); close.setOnAction(e -> close());
        var space = new Region(); HBox.setHgrow(space, Priority.ALWAYS);
        var heading = new HBox(10, WorkbenchIcons.HISTORY.create(), title, space, close); heading.setAlignment(Pos.CENTER_LEFT);
        search.setId("recent-search"); search.promptTextProperty().bind(localization.text("recent.search"));
        search.textProperty().addListener((o, old, value) -> filter());
        var group = new ToggleGroup(); var filters = new HBox(5);
        String[] keys = {"recent.all", "recent.projects", "recent.files"};
        for (int i = 0; i < keys.length; i++) {
            var button = new ToggleButton(); button.textProperty().bind(localization.text(keys[i]));
            button.getStyleClass().add("recent-filter"); button.setToggleGroup(group);
            button.setUserData(i == 0 ? null : RecentItem.Kind.values()[i - 1]);
            if (i == 0) button.setSelected(true);
            button.setOnAction(e -> { button.setSelected(true); kind = (RecentItem.Kind) button.getUserData(); filter(); });
            filters.getChildren().add(button);
        }
        list.setId("recent-list"); list.setItems(filtered); list.setFixedCellSize(62);
        var empty = new Label(); empty.textProperty().bind(localization.text("recent.empty")); list.setPlaceholder(empty);
        list.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(RecentItem item, boolean emptyCell) {
                super.updateItem(item, emptyCell); setText(null); setGraphic(null); setOnMouseClicked(null);
                if (emptyCell || item == null) return;
                var name = new Label(item.name()); name.getStyleClass().add("recent-name");
                var type = new Label(localization.get(item.kind() == RecentItem.Kind.PROJECT ? "recent.project" : "recent.file"));
                type.getStyleClass().add("recent-kind");
                var first = new HBox(10, name, type); first.setAlignment(Pos.CENTER_LEFT);
                var path = new Label(item.path().toString()); path.getStyleClass().add("recent-path"); path.setMaxWidth(Double.MAX_VALUE);
                var labels = new VBox(3, first, path); labels.setMinWidth(0); HBox.setHgrow(labels, Priority.ALWAYS);
                var remove = new Button(null, WorkbenchIcons.CLOSE.create()); remove.getStyleClass().add("icon-button");
                HoverHelp.install(remove, localization, "recent.remove", "");
                remove.setOnAction(e -> {
                    int index = getIndex(); model.getRecentItems().remove(item);
                    list.getSelectionModel().select(Math.min(index, filtered.size() - 1));
                });
                remove.setOnMouseClicked(e -> e.consume());
                var row = new HBox(12, (item.kind() == RecentItem.Kind.PROJECT ? WorkbenchIcons.FOLDER : WorkbenchIcons.FILE).create(), labels, remove);
                row.setAlignment(Pos.CENTER_LEFT); setGraphic(row);
                setOnMouseClicked(e -> { if (e.getClickCount() == 2) open(item); });
            }
        });
        list.getSelectionModel().selectFirst();
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) { open(list.getSelectionModel().getSelectedItem()); e.consume(); }
            if (e.getCode() == KeyCode.DELETE) {
                var selected = list.getSelectionModel().getSelectedItem();
                if (selected != null) model.getRecentItems().remove(selected);
                e.consume();
            }
        });
        search.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.UP) {
                if (e.getCode() == KeyCode.DOWN) list.getSelectionModel().selectNext(); else list.getSelectionModel().selectPrevious();
                list.scrollTo(list.getSelectionModel().getSelectedIndex()); e.consume();
            } else if (e.getCode() == KeyCode.ENTER) { open(list.getSelectionModel().getSelectedItem()); e.consume(); }
        });
        message.textProperty().bind(localization.text(model.getRecentItems().errorProperty()));
        message.setWrapText(true); message.getStyleClass().add("recent-message");
        message.visibleProperty().bind(message.textProperty().isNotEmpty()); message.managedProperty().bind(message.visibleProperty());
        var hint = new Label(); hint.textProperty().bind(localization.text("recent.hint")); hint.getStyleClass().add("recent-path");
        var clear = new Button(); clear.textProperty().bind(localization.text("recent.clear")); clear.getStyleClass().add("subtle-button");
        HoverHelp.install(clear, localization, "recent.clearHelp", "");
        clear.setOnAction(e -> {
            model.getRecentItems().clear();
            message.textProperty().bind(localization.text(model.getRecentItems().errorProperty()));
        });
        var footerSpace = new Region(); HBox.setHgrow(footerSpace, Priority.ALWAYS);
        var footer = new HBox(hint, footerSpace, clear); footer.setAlignment(Pos.CENTER_LEFT);
        var root = new VBox(10, heading, search, filters, list, message, footer); VBox.setVgrow(list, Priority.ALWAYS);
        root.getStyleClass().addAll("workbench", "recent-picker");
        if (owner.getScene().getRoot().getStyleClass().contains("light")) root.getStyleClass().add("light");
        root.getStylesheets().addAll(owner.getScene().getRoot().getStylesheets());
        setScene(new Scene(root, Math.min(700, owner.getWidth() - 40), 470));
        getScene().setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) { close(); e.consume(); } });
        setOnShown(e -> { setX(owner.getX() + (owner.getWidth() - getWidth()) / 2); setY(owner.getY() + 55); search.requestFocus(); });
    }
    private void filter() {
        message.textProperty().bind(localization.text(model.getRecentItems().errorProperty()));
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        filtered.setPredicate(item -> (kind == null || item.kind() == kind) && item.path().toString().toLowerCase(Locale.ROOT).contains(query));
        list.getSelectionModel().selectFirst();
    }
    private void open(RecentItem item) {
        if (item == null) return;
        try {
            if (model.openRecent(item)) { close(); return; }
            message.textProperty().unbind(); message.setText(localization.get("recent.missing", item.path()));
        } catch (IOException failure) {
            message.textProperty().unbind(); message.setText(localization.get("recent.openError", failure.getMessage()));
        }
    }
}
