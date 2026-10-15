package io.github.dinamo541.idearm.app.editor;

import atlantafx.base.theme.Styles;
import io.github.dinamo541.idearm.application.editor.CompletionItem;
import io.github.dinamo541.idearm.application.editor.CompletionKind;
import io.github.dinamo541.idearm.language.catalog.CpuLevel;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Popup;
import javafx.stage.Window;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Autocompletion dropdown popup offering instructions, registers, directives, and project symbols.
 */
public final class CompletionPopup extends Popup {

    private final ListView<CompletionItem> listView;
    private Consumer<CompletionItem> onSelected;

    private Scene installedScene;
    private Window installedWindow;
    private Node installedOwner;
    private EventHandler<MouseEvent> outsideClickFilter;
    private ChangeListener<Boolean> windowFocusListener;
    private ChangeListener<Boolean> ownerFocusListener;

    public CompletionPopup() {
        setAutoHide(true);
        setHideOnEscape(true);

        this.listView = new ListView<>();
        this.listView.setPrefWidth(350);
        this.listView.setPrefHeight(220);
        this.listView.setMaxHeight(260);
        // The look lives in workbench.css (.completion-list), which WorkbenchTheme applies on showing.
        this.listView.getStyleClass().addAll(Styles.DENSE, "completion-list");

        this.listView.setCellFactory(lv -> new CompletionListCell(this::commitSelection));

        // Keyboard navigation and commit when focus is on listView
        this.listView.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            KeyCode code = event.getCode();
            int size = listView.getItems().size();
            if (code == KeyCode.UP) {
                if (size > 0) {
                    int current = listView.getSelectionModel().getSelectedIndex();
                    int prev = (current <= 0) ? size - 1 : current - 1;
                    listView.getSelectionModel().select(prev);
                    listView.scrollTo(prev);
                }
                event.consume();
            } else if (code == KeyCode.DOWN) {
                if (size > 0) {
                    int current = listView.getSelectionModel().getSelectedIndex();
                    int next = (current < 0 || current >= size - 1) ? 0 : current + 1;
                    listView.getSelectionModel().select(next);
                    listView.scrollTo(next);
                }
                event.consume();
            } else if (code == KeyCode.PAGE_UP) {
                if (size > 0) {
                    int current = listView.getSelectionModel().getSelectedIndex();
                    int prev = Math.max(0, current - 6);
                    listView.getSelectionModel().select(prev);
                    listView.scrollTo(prev);
                }
                event.consume();
            } else if (code == KeyCode.PAGE_DOWN) {
                if (size > 0) {
                    int current = listView.getSelectionModel().getSelectedIndex();
                    int next = Math.min(size - 1, current + 6);
                    listView.getSelectionModel().select(next);
                    listView.scrollTo(next);
                }
                event.consume();
            } else if (code == KeyCode.ENTER || code == KeyCode.TAB) {
                commitSelection();
                event.consume();
            } else if (code == KeyCode.ESCAPE) {
                hide();
                event.consume();
            }
        });

        setOnHiding(event -> cleanUpListeners());

        getContent().add(listView);
    }

    public void showCompletions(Node owner, double screenX, double screenY,
                                List<CompletionItem> items, Consumer<CompletionItem> onSelected) {
        Objects.requireNonNull(owner, "owner cannot be null");
        Objects.requireNonNull(items, "items cannot be null");

        io.github.dinamo541.idearm.app.ui.WorkbenchTheme.apply(listView,
                owner.getScene() == null ? null : owner.getScene().getWindow());

        if (items.isEmpty()) {
            hide();
            return;
        }

        this.onSelected = onSelected;
        this.listView.setItems(FXCollections.observableArrayList(items));
        this.listView.getSelectionModel().select(0);

        if (isShowing()) {
            hide();
        }
        show(owner, screenX, screenY);
        installDismissListeners(owner);
    }

    public void updateItems(List<CompletionItem> items) {
        if (items == null || items.isEmpty()) {
            hide();
            return;
        }
        int previousIndex = listView.getSelectionModel().getSelectedIndex();
        listView.setItems(FXCollections.observableArrayList(items));
        int newIndex = Math.clamp(previousIndex, 0, items.size() - 1);
        listView.getSelectionModel().select(newIndex);
        listView.scrollTo(newIndex);
    }

    private void installDismissListeners(Node owner) {
        cleanUpListeners();

        this.installedOwner = owner;
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

        if (installedOwner != null) {
            ownerFocusListener = (obs, oldVal, newVal) -> {
                if (!newVal && !listView.isFocused()) {
                    hide();
                }
            };
            installedOwner.focusedProperty().addListener(ownerFocusListener);
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
        if (installedOwner != null && ownerFocusListener != null) {
            installedOwner.focusedProperty().removeListener(ownerFocusListener);
            ownerFocusListener = null;
        }
        installedScene = null;
        installedWindow = null;
        installedOwner = null;
    }

    /**
     * Handles a key event dispatched from the editor when the popup is visible.
     *
     * @return true if the event was consumed by the completion popup.
     */
    public boolean handleEditorKeyEvent(KeyEvent event) {
        if (!isShowing()) {
            return false;
        }

        KeyCode code = event.getCode();
        int size = listView.getItems().size();

        if (code == KeyCode.UP) {
            if (size > 0) {
                int current = listView.getSelectionModel().getSelectedIndex();
                int prev = (current <= 0) ? size - 1 : current - 1;
                listView.getSelectionModel().select(prev);
                listView.scrollTo(prev);
            }
            event.consume();
            return true;
        } else if (code == KeyCode.DOWN) {
            if (size > 0) {
                int current = listView.getSelectionModel().getSelectedIndex();
                int next = (current < 0 || current >= size - 1) ? 0 : current + 1;
                listView.getSelectionModel().select(next);
                listView.scrollTo(next);
            }
            event.consume();
            return true;
        } else if (code == KeyCode.PAGE_UP) {
            if (size > 0) {
                int current = listView.getSelectionModel().getSelectedIndex();
                int prev = Math.max(0, current - 6);
                listView.getSelectionModel().select(prev);
                listView.scrollTo(prev);
            }
            event.consume();
            return true;
        } else if (code == KeyCode.PAGE_DOWN) {
            if (size > 0) {
                int current = listView.getSelectionModel().getSelectedIndex();
                int next = Math.min(size - 1, current + 6);
                listView.getSelectionModel().select(next);
                listView.scrollTo(next);
            }
            event.consume();
            return true;
        } else if (code == KeyCode.ENTER || code == KeyCode.TAB) {
            commitSelection();
            event.consume();
            return true;
        } else if (code == KeyCode.ESCAPE) {
            hide();
            event.consume();
            return true;
        } else if (code == KeyCode.LEFT || code == KeyCode.RIGHT || code == KeyCode.HOME || code == KeyCode.END) {
            hide();
            return false;
        }
        return false;
    }

    public void commitSelection() {
        CompletionItem item = listView.getSelectionModel().getSelectedItem();
        if (item == null && !listView.getItems().isEmpty()) {
            item = listView.getItems().get(0);
        }
        if (item != null && onSelected != null) {
            hide();
            onSelected.accept(item);
        } else {
            hide();
        }
    }

    private static final class CompletionListCell extends ListCell<CompletionItem> {

        private final HBox root;
        private final Label badge;
        private final Label label;
        private final Label detail;
        private final Label cpuBadge;
        private final Runnable onCommit;

        CompletionListCell(Runnable onCommit) {
            this.onCommit = onCommit;
            this.badge = new Label();
            this.badge.getStyleClass().add("kind-badge");
            this.badge.setMinWidth(Region.USE_PREF_SIZE);

            this.label = new Label();
            this.label.getStyleClass().add("completion-label");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            this.detail = new Label();
            this.detail.getStyleClass().add("completion-detail");
            this.detail.setMinWidth(0);

            this.cpuBadge = new Label();
            this.cpuBadge.getStyleClass().addAll("kind-badge", "kind-warning");
            this.cpuBadge.setMinWidth(Region.USE_PREF_SIZE);

            this.root = new HBox(8, badge, label, spacer, detail, cpuBadge);
            this.root.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            this.root.setPadding(new Insets(2, 4, 2, 4));

            this.root.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && !isEmpty() && getItem() != null) {
                    if (this.onCommit != null) {
                        getListView().getSelectionModel().select(getItem());
                        this.onCommit.run();
                        event.consume();
                    }
                }
            });
        }

        @Override
        protected void updateItem(CompletionItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                label.setText(item.label());
                detail.setText(item.detail() != null ? item.detail() : "");

                configureBadge(item.kind());

                if (item.minCpu() != null && item.minCpu() != CpuLevel.CPU_8086) {
                    cpuBadge.setText(item.minCpu().name().replace("CPU_", ""));
                    cpuBadge.setVisible(true);
                    cpuBadge.setManaged(true);
                } else {
                    cpuBadge.setVisible(false);
                    cpuBadge.setManaged(false);
                }

                setText(null);
                setGraphic(root);
            }
        }

        private void configureBadge(CompletionKind kind) {
            badge.getStyleClass().removeAll("kind-success", "kind-warning", "kind-violet", "kind-teal",
                    "kind-pink", "kind-neutral");
            String tone = switch (kind) {
                case INSTRUCTION -> null;
                case REGISTER -> "kind-success";
                case DIRECTIVE -> "kind-violet";
                case PROCEDURE -> "kind-warning";
                case VARIABLE -> "kind-teal";
                case CONSTANT -> "kind-pink";
                case LABEL -> "kind-neutral";
            };
            badge.setText(switch (kind) {
                case INSTRUCTION -> "INS";
                case REGISTER -> "REG";
                case DIRECTIVE -> "DIR";
                case PROCEDURE -> "PROC";
                case VARIABLE -> "VAR";
                case CONSTANT -> "CONST";
                case LABEL -> "LBL";
            });
            if (tone != null) badge.getStyleClass().add(tone);
        }
    }
}
