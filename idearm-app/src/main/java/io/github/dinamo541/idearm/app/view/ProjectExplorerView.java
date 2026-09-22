package io.github.dinamo541.idearm.app.view;

import atlantafx.base.theme.Styles;
import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.i18n.Message;
import io.github.dinamo541.idearm.app.viewmodel.ProjectExplorerViewModel;
import io.github.dinamo541.idearm.app.viewmodel.ProjectExplorerViewModel.NameFeedback;
import io.github.dinamo541.idearm.domain.files.EntryNames;
import io.github.dinamo541.idearm.domain.files.EntryNames.Kind;
import io.github.dinamo541.idearm.domain.port.ProjectFiles.Deletion;
import io.github.dinamo541.idearm.domain.port.ProjectFiles.Entry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The project explorer, modelled on VS Code's.
 *
 * <p>The header names the open folder and offers New File, New Folder, Refresh and Collapse Folders. New entries
 * and renames are typed inline, in the tree, with the problem (or the name that will be stored) shown under the
 * field while typing: Enter confirms, Escape cancels, and leaving the field keeps a valid name. The context menu
 * adds Open, Copy Path, Rename (F2) and Delete (Delete key, to the Recycle Bin when there is one). Double-click or
 * Enter opens a file.
 */
public final class ProjectExplorerView extends BorderPane {

    /** A tree row: an existing entry, or the field of an entry being created. */
    record ExplorerNode(Path path, boolean directory, Kind pending) {
        static ExplorerNode of(Entry entry) {
            return new ExplorerNode(entry.path(), entry.directory(), null);
        }

        boolean isPending() {
            return pending != null;
        }

        String name() {
            Path name = path.getFileName();
            return name == null ? path.toString() : name.toString();
        }
    }

    /** A folder loads its children the first time it is expanded, and again after it was invalidated. */
    private final class ExplorerItem extends TreeItem<ExplorerNode> {
        private boolean loaded;

        ExplorerItem(ExplorerNode node) {
            super(node);
        }

        @Override
        public boolean isLeaf() {
            return !getValue().directory() || getValue().isPending();
        }

        @Override
        public ObservableList<TreeItem<ExplorerNode>> getChildren() {
            if (!loaded && !isLeaf()) {
                loaded = true;
                super.getChildren().setAll(load(getValue().path()));
            }
            return super.getChildren();
        }

        boolean isLoaded() {
            return loaded;
        }

        /** Forgets the children of a collapsed folder; they are read again when it opens. */
        void invalidate() {
            loaded = false;
            super.getChildren().clear();
        }
    }

    private final ProjectExplorerViewModel viewModel;
    private final Localization localization;
    private final TreeView<ExplorerNode> tree = new TreeView<>();
    private Consumer<Path> onFileSelected;
    private Runnable onOpenFolderRequested;
    private Consumer<Message> onStatus;

    /** The only row allowed to enter edit mode: a click on a selected row must not start a rename. */
    private TreeItem<ExplorerNode> editRequest;
    private boolean refreshPending;

    public ProjectExplorerView(ProjectExplorerViewModel viewModel, Localization localization) {
        this.viewModel = viewModel;
        this.localization = localization;
        getStyleClass().add("project-explorer");

        setTop(createHeader());
        setCenter(new StackPane(tree, createEmptyState()));

        tree.setShowRoot(false);
        tree.setEditable(true);
        tree.setCellFactory(view -> new ExplorerCell());
        tree.setContextMenu(contextMenu());
        tree.getSelectionModel().selectedItemProperty().addListener((obs, old, item) ->
                viewModel.setSelectedFile(item == null || item.getValue().isPending() ? null : item.getValue().path()));
        tree.setOnKeyPressed(event -> {
            if (tree.getEditingItem() != null) {
                return;
            }
            if (event.getCode() == KeyCode.F2) {
                renameSelected();
                event.consume();
            } else if (event.getCode() == KeyCode.DELETE) {
                deleteSelected();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                openSelected();
                event.consume();
            }
        });

        viewModel.refreshCountProperty().addListener((obs, old, count) -> refresh());
        refresh();
    }

    public void setOnFileSelected(Consumer<Path> listener) {
        this.onFileSelected = listener;
    }

    /** What the "Open Folder" button of an empty explorer does. */
    public void setOnOpenFolderRequested(Runnable listener) {
        this.onOpenFolderRequested = listener;
    }

    /** Where the explorer reports what an operation did. */
    public void setOnStatus(Consumer<Message> listener) {
        this.onStatus = listener;
    }

    /** Starts typing a new file name in the selected folder, or next to the selected file. */
    public void newFile() {
        startCreate(Kind.FILE);
    }

    /** Starts typing a new folder name in the selected folder, or next to the selected file. */
    public void newFolder() {
        startCreate(Kind.FOLDER);
    }

    /** Selects an entry and opens the folders above it, as VS Code does for the active editor. */
    public void revealPath(Path entry) {
        if (entry != null && tree.getEditingItem() == null) {
            reveal(entry.toAbsolutePath().normalize());
        }
    }

    public void collapseAll() {
        TreeItem<ExplorerNode> root = tree.getRoot();
        if (root != null) {
            for (TreeItem<ExplorerNode> child : root.getChildren()) {
                collapse(child);
            }
        }
    }

    // ------------------------------------------------------------------ layout

    private Node createHeader() {
        var title = new Label();
        title.textProperty().bind(localization.text("explorer.title"));
        title.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);

        var folderName = new Label();
        folderName.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_SMALL);
        folderName.textProperty().bind(Bindings.createStringBinding(() -> {
            Path root = viewModel.getProjectRoot();
            return root == null || root.getFileName() == null ? "" : root.getFileName().toString();
        }, viewModel.projectRootProperty()));

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var actions = new HBox(1,
                action(ExplorerIcons.newFile(), "explorer.newFile", this::newFile),
                action(ExplorerIcons.newFolder(), "explorer.newFolder", this::newFolder),
                action(ExplorerIcons.refresh(), "explorer.refresh", viewModel::refresh),
                action(ExplorerIcons.collapseAll(), "explorer.collapseAll", this::collapseAll));
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.visibleProperty().bind(viewModel.projectRootProperty().isNotNull());
        actions.managedProperty().bind(actions.visibleProperty());

        var folderRow = new HBox(4, folderName, spacer, actions);
        folderRow.setAlignment(Pos.CENTER_LEFT);

        var header = new VBox(4, title, folderRow);
        header.setPadding(new Insets(8, 4, 4, 10));
        return header;
    }

    private Button action(Node icon, String tooltipKey, Runnable action) {
        var button = new Button(null, icon);
        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        io.github.dinamo541.idearm.app.ui.HoverHelp.install(button, localization, tooltipKey, "");
        button.getStyleClass().add("icon-button");
        button.setFocusTraversable(false);
        button.setOnAction(event -> action.run());
        return button;
    }

    private Node createEmptyState() {
        var message = new Label();
        message.textProperty().bind(localization.text("explorer.empty"));
        message.setWrapText(true);
        message.getStyleClass().add(Styles.TEXT_MUTED);

        var open = new Button();
        open.textProperty().bind(localization.text("explorer.openFolder"));
        open.getStyleClass().add(Styles.ACCENT);
        open.setMaxWidth(Double.MAX_VALUE);
        open.setOnAction(event -> {
            if (onOpenFolderRequested != null) {
                onOpenFolderRequested.run();
            }
        });

        var emptyState = new VBox(10, message, open);
        emptyState.setPadding(new Insets(12));
        emptyState.setAlignment(Pos.TOP_LEFT);
        emptyState.visibleProperty().bind(viewModel.projectRootProperty().isNull());
        tree.visibleProperty().bind(viewModel.projectRootProperty().isNotNull());
        return emptyState;
    }

    private ContextMenu contextMenu() {
        var newFile = menuItem("explorer.newFile", this::newFile);
        var newFolder = menuItem("explorer.newFolder", this::newFolder);
        var open = menuItem("explorer.open", this::openSelected);
        var copyPath = menuItem("explorer.copyPath",
                () -> selectedEntry().ifPresent(node -> copy(node.path().toString())));
        var copyRelative = menuItem("explorer.copyRelativePath",
                () -> selectedEntry().ifPresent(node -> copy(viewModel.relativePath(node.path()))));
        var rename = menuItem("explorer.rename", this::renameSelected);
        var delete = menuItem("explorer.delete", this::deleteSelected);
        var refresh = menuItem("explorer.refresh", viewModel::refresh);

        var menu = new ContextMenu(newFile, newFolder, new SeparatorMenuItem(), open, new SeparatorMenuItem(),
                copyPath, copyRelative, new SeparatorMenuItem(), rename, delete, new SeparatorMenuItem(), refresh);
        menu.setOnShowing(event -> {
            boolean editable = viewModel.canEdit();
            Optional<ExplorerNode> selected = selectedEntry();
            newFile.setDisable(!editable);
            newFolder.setDisable(!editable);
            open.setDisable(selected.isEmpty() || selected.get().directory());
            copyPath.setDisable(selected.isEmpty());
            copyRelative.setDisable(selected.isEmpty());
            rename.setDisable(!editable || selected.isEmpty());
            delete.setDisable(!editable || selected.isEmpty());
        });
        return menu;
    }

    private MenuItem menuItem(String key, Runnable action) {
        var item = new MenuItem();
        item.textProperty().bind(localization.text(key));
        item.setOnAction(event -> action.run());
        return item;
    }

    // ------------------------------------------------------------------ tree contents

    private List<TreeItem<ExplorerNode>> load(Path directory) {
        var items = new ArrayList<TreeItem<ExplorerNode>>();
        for (Entry entry : viewModel.children(directory)) {
            items.add(new ExplorerItem(ExplorerNode.of(entry)));
        }
        return items;
    }

    /** Reloads what is visible, keeping expanded folders and the selection, as VS Code does after a change. */
    private void refresh() {
        Path root = viewModel.getProjectRoot();
        if (root == null) {
            tree.setRoot(null);
            return;
        }
        if (tree.getEditingItem() != null) {
            // Never pull the field away from someone typing; refresh when they finish.
            refreshPending = true;
            return;
        }
        refreshPending = false;
        TreeItem<ExplorerNode> current = tree.getRoot();
        if (current == null || !current.getValue().path().equals(root)) {
            var rootItem = new ExplorerItem(new ExplorerNode(root, true, null));
            rootItem.setExpanded(true);
            tree.setRoot(rootItem);
            return;
        }
        Path selected = selectedEntry().map(ExplorerNode::path).orElse(null);
        reload(current);
        if (selected != null) {
            reveal(selected);
        }
    }

    private void reload(TreeItem<ExplorerNode> folder) {
        Map<Path, TreeItem<ExplorerNode>> existing = new HashMap<>();
        for (TreeItem<ExplorerNode> child : folder.getChildren()) {
            existing.put(child.getValue().path(), child);
        }
        var children = new ArrayList<TreeItem<ExplorerNode>>();
        for (Entry entry : viewModel.children(folder.getValue().path())) {
            TreeItem<ExplorerNode> kept = existing.get(entry.path());
            boolean same = kept != null && kept.getValue().directory() == entry.directory()
                    && kept.getValue().name().equals(entry.name());
            TreeItem<ExplorerNode> child = same ? kept : new ExplorerItem(ExplorerNode.of(entry));
            if (same && child instanceof ExplorerItem item && item.getValue().directory() && item.isLoaded()) {
                if (item.isExpanded()) {
                    reload(item);
                } else {
                    item.invalidate();
                }
            }
            children.add(child);
        }
        folder.getChildren().setAll(children);
    }

    /** Expands the folders above an entry, selects it and scrolls it into view. */
    private void reveal(Path target) {
        TreeItem<ExplorerNode> item = findItem(target, true);
        if (item == null || item == tree.getRoot()) {
            return;
        }
        tree.getSelectionModel().select(item);
        int row = tree.getRow(item);
        if (row >= 0) {
            tree.scrollTo(Math.max(0, row - 3));
        }
    }

    private TreeItem<ExplorerNode> findItem(Path target, boolean expand) {
        TreeItem<ExplorerNode> current = tree.getRoot();
        Path root = viewModel.getProjectRoot();
        if (current == null || root == null || target == null || !target.startsWith(root)) {
            return null;
        }
        if (target.equals(root)) {
            return current;
        }
        for (Path part : root.relativize(target)) {
            if (expand) {
                current.setExpanded(true);
            }
            Path wanted = current.getValue().path().resolve(part);
            TreeItem<ExplorerNode> next = null;
            for (TreeItem<ExplorerNode> child : current.getChildren()) {
                if (!child.getValue().isPending() && child.getValue().path().equals(wanted)) {
                    next = child;
                    break;
                }
            }
            if (next == null) {
                return null;
            }
            current = next;
        }
        return current;
    }

    private static void collapse(TreeItem<ExplorerNode> item) {
        if (item.isLeaf()) {
            return;
        }
        item.setExpanded(false);
        if (item instanceof ExplorerItem explorerItem && explorerItem.isLoaded()) {
            for (TreeItem<ExplorerNode> child : item.getChildren()) {
                collapse(child);
            }
        }
    }

    // ------------------------------------------------------------------ operations

    private Optional<ExplorerNode> selectedEntry() {
        TreeItem<ExplorerNode> item = tree.getSelectionModel().getSelectedItem();
        return item == null || item.getValue().isPending() ? Optional.empty() : Optional.of(item.getValue());
    }

    private void startCreate(Kind kind) {
        if (!viewModel.canEdit() || tree.getRoot() == null) {
            return;
        }
        if (tree.getEditingItem() != null) {
            tree.edit(null);
        }
        Optional<ExplorerNode> selected = selectedEntry();
        Path folder = viewModel.targetFolder(selected.map(ExplorerNode::path).orElse(null),
                selected.map(ExplorerNode::directory).orElse(true));
        TreeItem<ExplorerNode> parent = findItem(folder, true);
        if (parent == null) {
            return;
        }
        parent.setExpanded(true);
        var field = new ExplorerItem(new ExplorerNode(folder.resolve("new"), kind == Kind.FOLDER, kind));
        parent.getChildren().addFirst(field);
        beginEdit(field);
    }

    private void renameSelected() {
        TreeItem<ExplorerNode> item = tree.getSelectionModel().getSelectedItem();
        if (item != null && !item.getValue().isPending() && viewModel.canEdit() && item != tree.getRoot()) {
            beginEdit(item);
        }
    }

    private void beginEdit(TreeItem<ExplorerNode> item) {
        tree.getSelectionModel().select(item);
        int row = tree.getRow(item);
        if (row >= 0) {
            tree.scrollTo(Math.max(0, row - 3));
        }
        editRequest = item;
        // A row added just now gets its cell on the next layout pass.
        Platform.runLater(() -> {
            tree.layout();
            tree.edit(item);
        });
    }

    /** Applies what was typed, once the field is gone. */
    private void applyEdit(TreeItem<ExplorerNode> item, String typed, boolean keepOnlyIfValid) {
        editRequest = null;
        ExplorerNode node = item.getValue();
        removePending(item);
        boolean unchanged = typed == null || typed.isBlank() || (!node.isPending() && typed.equals(node.name()));
        if (!unchanged) {
            NameFeedback check = check(node, typed);
            if (!check.blocking()) {
                perform(node, typed);
            } else if (!keepOnlyIfValid) {
                showError(check.message());
            }
        }
        if (refreshPending) {
            refresh();
        }
        tree.requestFocus();
    }

    private void perform(ExplorerNode node, String typed) {
        Path parent = node.path().getParent();
        try {
            Path target;
            if (node.isPending()) {
                target = node.pending() == Kind.FOLDER
                        ? viewModel.createFolder(parent, typed)
                        : viewModel.createFile(parent, typed);
                status(Message.of("status.explorer.created", viewModel.relativePath(target)));
            } else {
                target = viewModel.rename(node.path(), typed);
                status(Message.of("status.explorer.renamed", target.getFileName().toString()));
            }
            refresh();
            reveal(target);
        } catch (RuntimeException failure) {
            showError(ProjectExplorerViewModel.failureMessage(failure));
            refresh();
        }
    }

    private NameFeedback check(ExplorerNode node, String typed) {
        Kind kind = node.isPending() ? node.pending() : (node.directory() ? Kind.FOLDER : Kind.FILE);
        return viewModel.check(node.path().getParent(), typed, kind, node.isPending() ? null : node.path());
    }

    private static void removePending(TreeItem<ExplorerNode> item) {
        if (item != null && item.getValue().isPending() && item.getParent() != null) {
            item.getParent().getChildren().remove(item);
        }
    }

    private void openSelected() {
        TreeItem<ExplorerNode> item = tree.getSelectionModel().getSelectedItem();
        if (item == null || item.getValue().isPending()) {
            return;
        }
        if (item.getValue().directory()) {
            item.setExpanded(!item.isExpanded());
        } else if (onFileSelected != null) {
            onFileSelected.accept(item.getValue().path());
        }
    }

    private void deleteSelected() {
        TreeItem<ExplorerNode> item = tree.getSelectionModel().getSelectedItem();
        Optional<ExplorerNode> selected = selectedEntry();
        if (selected.isEmpty() || !viewModel.canEdit() || item == tree.getRoot()) {
            return;
        }
        ExplorerNode node = selected.get();
        boolean trash = viewModel.trashAvailable();
        String question = localization.get(!trash ? "explorer.delete.permanentQuestion"
                : node.directory() ? "explorer.delete.folderTrashQuestion" : "explorer.delete.trashQuestion",
                node.name());
        String detail = localization.get(trash ? "explorer.delete.trashDetail" : "explorer.delete.permanentDetail");
        String action = localization.get(trash ? "explorer.delete.moveToTrash" : "explorer.delete.permanently");
        if (!confirm(question, detail, action)) {
            return;
        }
        try {
            Deletion deletion = viewModel.delete(node.path(), !trash);
            status(Message.of(deletion == Deletion.MOVED_TO_TRASH ? "status.explorer.trashed"
                    : "status.explorer.deleted", node.name()));
        } catch (RuntimeException failure) {
            if (!trash) {
                showError(ProjectExplorerViewModel.failureMessage(failure));
                return;
            }
            // Some entries cannot be recycled (network drives, very large folders): ask before destroying them.
            if (confirm(localization.get("explorer.delete.trashFailed", node.name()),
                    localization.get("explorer.delete.permanentDetail"),
                    localization.get("explorer.delete.permanently"))) {
                try {
                    viewModel.delete(node.path(), true);
                    status(Message.of("status.explorer.deleted", node.name()));
                } catch (RuntimeException permanentFailure) {
                    showError(ProjectExplorerViewModel.failureMessage(permanentFailure));
                }
            }
        }
    }

    private boolean confirm(String question, String detail, String confirmText) {
        var confirmButton = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        var cancelButton = new ButtonType(localization.get("explorer.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        var alert = new Alert(Alert.AlertType.WARNING, detail, confirmButton, cancelButton);
        alert.setTitle(localization.get("explorer.delete.title"));
        alert.setHeaderText(question);
        if (getScene() != null) {
            alert.initOwner(getScene().getWindow());
        }
        return alert.showAndWait().filter(confirmButton::equals).isPresent();
    }

    private void showError(Message message) {
        var alert = new Alert(Alert.AlertType.ERROR, localization.get(message));
        alert.setHeaderText(null);
        if (getScene() != null) {
            alert.initOwner(getScene().getWindow());
        }
        alert.showAndWait();
    }

    private void status(Message message) {
        if (onStatus != null) {
            onStatus.accept(message);
        }
    }

    private static void copy(String text) {
        var content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static Node iconFor(TreeItem<ExplorerNode> item) {
        ExplorerNode node = item.getValue();
        if (node.directory()) {
            return ExplorerIcons.folder(item.isExpanded());
        }
        return ExplorerIcons.file(node.isPending() ? "" : node.name());
    }

    // ------------------------------------------------------------------ cells

    private final class ExplorerCell extends TreeCell<ExplorerNode> {

        private final InvalidationListener expansion = obs -> showEntry();
        private TreeItem<ExplorerNode> observed;
        private TextField field;
        private Label feedback;
        /** Set once an edit is confirmed or dropped, so Enter and focus loss never both act. */
        private boolean settled;

        ExplorerCell() {
            treeItemProperty().addListener((obs, old, item) -> {
                if (observed != null) {
                    observed.expandedProperty().removeListener(expansion);
                }
                observed = item;
                if (item != null) {
                    item.expandedProperty().addListener(expansion);
                }
            });
            setOnMouseClicked(event -> {
                // Folders already toggle on double-click; files open.
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !isEmpty()
                        && !isEditing() && getItem() != null && !getItem().directory()) {
                    openSelected();
                }
            });
        }

        @Override
        protected void updateItem(ExplorerNode node, boolean empty) {
            super.updateItem(node, empty);
            if (!isEditing()) {
                showEntry();
            }
        }

        private void showEntry() {
            if (isEditing()) {
                return;
            }
            ExplorerNode node = getItem();
            TreeItem<ExplorerNode> item = getTreeItem();
            if (isEmpty() || node == null || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(node.isPending() ? "" : node.name());
            setGraphic(iconFor(item));
        }

        @Override
        public void startEdit() {
            TreeItem<ExplorerNode> item = getTreeItem();
            if (item == null || item != editRequest || !viewModel.canEdit()) {
                return;
            }
            super.startEdit();
            if (!isEditing()) {
                return;
            }
            settled = false;
            ExplorerNode node = item.getValue();
            field = new TextField(node.isPending() ? "" : node.name());
            field.getStyleClass().add(Styles.SMALL);
            field.setStyle("-fx-background-color: -color-bg-default; -fx-border-color: -color-accent-emphasis;"
                    + " -fx-border-width: 1; -fx-background-radius: 0; -fx-border-radius: 0; -fx-padding: 1 4 1 4;");
            feedback = new Label();
            feedback.setWrapText(true);
            feedback.setMaxWidth(Double.MAX_VALUE);
            feedback.setPadding(new Insets(3, 6, 3, 6));
            // A wrapped label reports a one-line height unless its height is taken from its actual width.
            Label message = feedback;
            message.widthProperty().addListener((obs, old, width) -> fitHeight(message));
            message.textProperty().addListener((obs, old, text) -> fitHeight(message));
            showFeedback(NameFeedback.NONE);

            field.textProperty().addListener((obs, old, text) -> showFeedback(check(node, text)));
            field.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    confirm();
                    event.consume();
                } else if (event.getCode() == KeyCode.ESCAPE) {
                    drop();
                    event.consume();
                }
            });
            field.focusedProperty().addListener((obs, was, focused) -> {
                if (!focused && isEditing()) {
                    cancelEdit();
                }
            });

            var box = new VBox(2, field, feedback);
            HBox.setHgrow(box, Priority.ALWAYS);
            var row = new HBox(6, iconFor(item), box);
            row.setAlignment(Pos.TOP_LEFT);
            // The field and its message use the rest of the row, as in VS Code, instead of their text width.
            box.prefWidthProperty().bind(widthProperty().subtract(row.layoutXProperty()).subtract(34));
            box.minWidthProperty().bind(box.prefWidthProperty());
            box.maxWidthProperty().bind(box.prefWidthProperty());
            setText(null);
            setGraphic(row);
            field.requestFocus();
            if (node.isPending()) {
                field.selectAll();
            } else {
                // Like VS Code, select the name without its extension.
                String name = node.name();
                int dot = node.directory() ? -1 : name.lastIndexOf('.');
                field.selectRange(0, dot > 0 ? dot : name.length());
            }
        }

        /**
         * The tree also ends edits on its own (a click elsewhere, a scroll). Like VS Code, leaving the field keeps a
         * valid name and drops anything else.
         */
        @Override
        public void cancelEdit() {
            if (settled || field == null) {
                super.cancelEdit();
                showEntry();
                return;
            }
            settled = true;
            TreeItem<ExplorerNode> item = getTreeItem();
            String typed = field.getText();
            end();
            Platform.runLater(() -> applyEdit(item, typed, true));
        }

        /** Enter: an invalid name keeps the field open with its message. */
        private void confirm() {
            NameFeedback result = check(getItem(), field.getText());
            if (result.blocking() && !field.getText().isBlank()) {
                showFeedback(result);
                field.requestFocus();
                return;
            }
            settled = true;
            TreeItem<ExplorerNode> item = getTreeItem();
            String typed = field.getText();
            end();
            applyEdit(item, typed, false);
        }

        /** Escape: nothing changes. */
        private void drop() {
            settled = true;
            TreeItem<ExplorerNode> item = getTreeItem();
            end();
            applyEdit(item, null, true);
        }

        private void end() {
            field = null;
            feedback = null;
            super.cancelEdit();
            if (tree.getEditingItem() != null) {
                tree.edit(null);
            }
            showEntry();
        }

        private void fitHeight(Label label) {
            double width = label.getWidth();
            if (width > 0) {
                label.setMinHeight(label.prefHeight(width));
                requestLayout();
            }
        }

        private void showFeedback(NameFeedback result) {
            if (feedback == null) {
                return;
            }
            boolean show = result.visible();
            feedback.setVisible(show);
            feedback.setManaged(show);
            if (show) {
                feedback.setText(localization.get(result.message()));
                feedback.setStyle(feedbackStyle(result.severity()));
            }
        }
    }

    /** VS Code's inline message colours, for the theme in use. */
    private static String feedbackStyle(EntryNames.Severity severity) {
        boolean light = Optional.ofNullable(Application.getUserAgentStylesheet())
                .map(sheet -> sheet.toLowerCase(Locale.ROOT).contains("light"))
                .orElse(false);
        String border;
        String background;
        switch (severity) {
            case ERROR -> {
                border = "#be1100";
                background = light ? "#f2dede" : "#5a1d1d";
            }
            case WARNING -> {
                border = "#b89500";
                background = light ? "#f6f5d2" : "#352a05";
            }
            default -> {
                border = "#007acc";
                background = light ? "#d6ecf2" : "#063b49";
            }
        }
        String text = light ? "#1f1f1f" : "#e6e6e6";
        return "-fx-background-color: " + background + "; -fx-border-color: " + border + "; -fx-text-fill: "
                + text + "; -fx-font-size: 0.9em;";
    }
}
