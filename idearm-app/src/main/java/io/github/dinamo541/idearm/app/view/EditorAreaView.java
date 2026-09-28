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
    private final javafx.scene.control.ScrollPane emptyPlaceholder;
    private final javafx.beans.property.BooleanProperty minimapVisible = new javafx.beans.property.SimpleBooleanProperty(true);
    private final Map<EditorDocumentViewModel, Tab> tabMap = new HashMap<>();
    private boolean updatingSelection = false;
    private java.util.function.Consumer<WelcomeView.Action> onWelcomeAction = action -> {};

    public EditorAreaView(EditorAreaViewModel viewModel, Localization localization) {
        this.viewModel = viewModel;
        this.localization = localization;

        this.tabPane = new TabPane();
        this.tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        this.tabPane.getStyleClass().addAll(Styles.DENSE, "editor-tabs");
        getStyleClass().add("editor-area");

        var welcome = new WelcomeView(localization, action -> onWelcomeAction.accept(action));
        welcome.setMinWidth(0);
        var welcomeHost = new StackPane(welcome);
        welcomeHost.setMinWidth(0);
        this.emptyPlaceholder = new javafx.scene.control.ScrollPane(welcomeHost);
        this.emptyPlaceholder.setFitToWidth(true);
        this.emptyPlaceholder.setFitToHeight(true);
        this.emptyPlaceholder.setMinSize(0, 0);
        this.emptyPlaceholder.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        StackPane.setAlignment(emptyPlaceholder, Pos.CENTER);

        getChildren().addAll(emptyPlaceholder, tabPane);
        updatePlaceholderVisibility();
        minimapVisible.addListener((obs, before, visible) -> viewModel.getDocuments().forEach(doc -> {
            if (doc.getEditor() instanceof io.github.dinamo541.idearm.app.editor.RichTextFxEditorComponent editor)
                editor.setMinimapVisible(visible);
        }));

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
                        var spacer = new javafx.scene.layout.Region();
                        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                        var mapToggle = new javafx.scene.control.ToggleButton(null,
                                io.github.dinamo541.idearm.app.ui.WorkbenchIcons.MINIMAP.create());
                        mapToggle.getStyleClass().addAll("icon-button", "minimap-toggle");
                        mapToggle.selectedProperty().bindBidirectional(minimapVisible);
                        mapToggle.accessibleTextProperty().bind(localization.text("editor.minimap"));
                        io.github.dinamo541.idearm.app.ui.HoverHelp.install(mapToggle, localization, "editor.minimap.help", "");
                        var trail = new javafx.scene.layout.HBox(8, breadcrumb, spacer, mapToggle);
                        trail.setAlignment(Pos.CENTER_LEFT);
                        trail.getStyleClass().add("editor-breadcrumb-bar");
                        content.setTop(trail);
                        if (doc.getEditor() instanceof io.github.dinamo541.idearm.app.editor.RichTextFxEditorComponent editor)
                            editor.setMinimapVisible(minimapVisible.get());
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
                            var content = (javafx.scene.layout.BorderPane) tab.getContent();
                            var toggle = (javafx.scene.control.ToggleButton) content.lookup(".minimap-toggle");
                            if (toggle != null) toggle.selectedProperty().unbindBidirectional(minimapVisible);
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

    void setOnWelcomeAction(java.util.function.Consumer<WelcomeView.Action> action) { onWelcomeAction = action; }

    public void cycle(int direction) {
        int size = tabPane.getTabs().size();
        if (size == 0) return;
        tabPane.getSelectionModel().select(Math.floorMod(tabPane.getSelectionModel().getSelectedIndex() + direction, size));
        var doc = viewModel.getActiveDocument();
        if (doc != null) doc.getEditor().requestFocus();
    }

    public void closeActive() { close(viewModel.getActiveDocument()); }

    public javafx.beans.property.BooleanProperty minimapVisibleProperty() { return minimapVisible; }

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
        emptyPlaceholder.setManaged(!hasDocs);
        tabPane.setVisible(hasDocs);
        tabPane.setManaged(hasDocs);
    }
}
