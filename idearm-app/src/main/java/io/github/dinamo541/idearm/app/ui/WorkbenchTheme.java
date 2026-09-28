package io.github.dinamo541.idearm.app.ui;

import java.util.Objects;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.scene.layout.Region;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Shares the workbench palette with dialogs and popups, including already-open reference windows. */
public final class WorkbenchTheme {
    private static final String STYLESHEET = Objects.requireNonNull(WorkbenchTheme.class.getResource(
            "/io/github/dinamo541/idearm/app/view/workbench.css")).toExternalForm();
    private static boolean installed;

    private WorkbenchTheme() { }

    public static void apply(Region root, Window owner) {
        if (!root.getStyleClass().contains("workbench")) root.getStyleClass().add("workbench");
        if (!root.getStylesheets().contains(STYLESHEET)) root.getStylesheets().add(STYLESHEET);
        if (owner != null && owner.getScene() != null) {
            boolean light = owner.getScene().getRoot().getStyleClass().contains("light");
            if (light && !root.getStyleClass().contains("light")) root.getStyleClass().add("light");
            if (!light) root.getStyleClass().remove("light");
        }
    }

    public static void install() {
        if (installed) return;
        installed = true;
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) change.getAddedSubList().forEach(WorkbenchTheme::followOwner);
        });
        Window.getWindows().forEach(WorkbenchTheme::followOwner);
    }

    private static void followOwner(Window window) {
        Window owner = window instanceof Stage stage ? stage.getOwner()
                : window instanceof PopupWindow popup ? popup.getOwnerWindow() : null;
        if (owner == null || owner.getScene() == null || window.getScene() == null
                || !(window.getScene().getRoot() instanceof Region root)) return;
        apply(root, owner);
        var classes = owner.getScene().getRoot().getStyleClass();
        InvalidationListener themeChanged = ignored -> apply(root, owner);
        classes.addListener(themeChanged);
        window.showingProperty().addListener(new ChangeListener<Boolean>() {
            @Override public void changed(javafx.beans.value.ObservableValue<? extends Boolean> value,
                                          Boolean before, Boolean showing) {
                if (!showing) {
                    classes.removeListener(themeChanged);
                    window.showingProperty().removeListener(this);
                }
            }
        });
    }
}
