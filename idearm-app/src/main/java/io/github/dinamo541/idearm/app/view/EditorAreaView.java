package io.github.dinamo541.idearm.app.view;

import atlantafx.base.theme.Styles;
import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.viewmodel.EditorAreaViewModel;
import io.github.dinamo541.idearm.app.viewmodel.EditorDocumentViewModel;
import java.util.HashMap;
import java.util.Map;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;

/**
 * Tabbed editor area displaying open document tabs with RichTextFX editors.
 */
public final class EditorAreaView extends StackPane {

    private final EditorAreaViewModel viewModel;
    private final Localization localization;
    private final TabPane tabPane;
    private final Label emptyPlaceholder;
    private final Map<EditorDocumentViewModel, Tab> tabMap = new HashMap<>();
    private boolean updatingSelection = false;

    public EditorAreaView(EditorAreaViewModel viewModel, Localization localization) {
        this.viewModel = viewModel;
        this.localization = localization;

        this.tabPane = new TabPane();
        this.tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        this.tabPane.getStyleClass().addAll(Styles.DENSE, "editor-tabs");
        getStyleClass().add("editor-area");

        this.emptyPlaceholder = new Label();
        this.emptyPlaceholder.textProperty().bind(localization.text("editor.emptyPlaceholder"));
        this.emptyPlaceholder.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TITLE_4);
        StackPane.setAlignment(emptyPlaceholder, Pos.CENTER);

        getChildren().addAll(emptyPlaceholder, tabPane);
        updatePlaceholderVisibility();

        // Listen for open/closed document list changes
        viewModel.getDocuments().addListener((ListChangeListener<EditorDocumentViewModel>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (EditorDocumentViewModel doc : change.getAddedSubList()) {
                        Tab tab = new Tab();
                        tab.textProperty().bind(doc.titleProperty());
                        var breadcrumb = new Label();
                        breadcrumb.getStyleClass().add("breadcrumbs");
                        breadcrumb.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() -> {
                            var file = doc.getFilePath();
                            String folder = file.getParent() != null && file.getParent().getFileName() != null
                                    ? file.getParent().getFileName().toString() + "  ›  " : "";
                            return folder + file.getFileName();
                        }, doc.filePathProperty()));
                        var content = new javafx.scene.layout.BorderPane(doc.getEditor().getNode());
                        content.setTop(breadcrumb);
                        tab.setGraphic(ExplorerIcons.file(doc.getFilePath().getFileName().toString()));
                        tab.setContent(content);
                        tab.setUserData(doc);
                        tab.setOnCloseRequest(e -> { e.consume(); close(doc); });

                        tabMap.put(doc, tab);
                        tabPane.getTabs().add(tab);
                    }
                }
                if (change.wasRemoved()) {
                    for (EditorDocumentViewModel doc : change.getRemoved()) {
                        Tab tab = tabMap.remove(doc);
                        if (tab != null) {
                            tabPane.getTabs().remove(tab);
                        }
                    }
                }
            }
            updatePlaceholderVisibility();
        });

        // Tab selection -> ViewModel active document
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (!updatingSelection && newTab != null && newTab.getUserData() instanceof EditorDocumentViewModel doc) {
                viewModel.activeDocumentProperty().set(doc);
            }
        });

        // ViewModel active document -> Tab selection
        viewModel.activeDocumentProperty().addListener((obs, oldDoc, newDoc) -> {
            if (newDoc != null) {
                Tab target = tabMap.get(newDoc);
                if (target != null && tabPane.getSelectionModel().getSelectedItem() != target) {
                    updatingSelection = true;
                    try {
                        tabPane.getSelectionModel().select(target);
                    } finally {
                        updatingSelection = false;
                    }
                }
            }
        });
    }

    public void cycle(int direction) {
        int size = tabPane.getTabs().size();
        if (size == 0) return;
        tabPane.getSelectionModel().select(Math.floorMod(tabPane.getSelectionModel().getSelectedIndex() + direction, size));
        var doc = viewModel.getActiveDocument();
        if (doc != null) doc.getEditor().requestFocus();
    }

    public void closeActive() { close(viewModel.getActiveDocument()); }

    private void close(EditorDocumentViewModel doc) {
        if (doc == null) return;
        if (doc.isModified()) {
            var save = new javafx.scene.control.ButtonType(localization.get("menu.file.save"));
            var discard = new javafx.scene.control.ButtonType(localization.get("editor.discard"));
            var cancel = new javafx.scene.control.ButtonType(localization.get("explorer.cancel"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
            var dialog = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
                    localization.get("editor.saveChanges", doc.getFilePath().getFileName()), save, discard, cancel);
            dialog.initOwner(getScene().getWindow());
            dialog.setHeaderText(null);
            var choice = dialog.showAndWait().orElse(cancel);
            if (choice == cancel) return;
            if (choice == save) {
                try { doc.save(); }
                catch (java.io.IOException failure) {
                    var error = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR, failure.getMessage());
                    error.initOwner(getScene().getWindow()); error.showAndWait(); return;
                }
            }
        }
        viewModel.closeDocument(doc);
    }

    private void updatePlaceholderVisibility() {
        boolean hasDocs = !viewModel.getDocuments().isEmpty();
        emptyPlaceholder.setVisible(!hasDocs);
        tabPane.setVisible(hasDocs);
    }
}
