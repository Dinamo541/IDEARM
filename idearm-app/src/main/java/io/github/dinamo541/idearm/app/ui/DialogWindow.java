package io.github.dinamo541.idearm.app.ui;

import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Shared window behaviour for the form dialogs.
 *
 * <p>A dialog whose height is written into the source hides its own buttons the moment it gains a row, and a
 * fixed window cannot even be dragged open again: that is how the Create button disappeared from New Project.
 * Here the width stays a decision and the height becomes a consequence of the rows, whatever the theme's font
 * size or the desktop's text scaling turns out to be.
 */
public final class DialogWindow {

    private DialogWindow() {
    }

    /** The workbench look, so a dialog reads as part of the IDE and its rows are as tall as the ones inside it. */
    public static void theme(Region root, Window owner) {
        WorkbenchTheme.apply(root, owner);
        root.getStyleClass().add("workbench-dialog");
    }

    public static javafx.scene.Node heading(io.github.dinamo541.idearm.app.i18n.Localization text,
                                             String titleKey, String descriptionKey, WorkbenchIcons icon) {
        var title = new javafx.scene.control.Label();
        title.textProperty().bind(text.text(titleKey));
        title.getStyleClass().add("dialog-title");
        var description = new javafx.scene.control.Label();
        description.textProperty().bind(text.text(descriptionKey));
        description.setWrapText(true);
        description.getStyleClass().add("dialog-description");
        var labels = new javafx.scene.layout.VBox(5, title, description);
        labels.setMinWidth(0);
        javafx.scene.layout.HBox.setHgrow(labels, javafx.scene.layout.Priority.ALWAYS);
        var heading = new javafx.scene.layout.HBox(14, icon.create(28), labels);
        heading.getStyleClass().add("dialog-heading");
        return heading;
    }

    /**
     * Gives the stage a scene as tall as its content needs, and a floor it cannot be dragged below.
     *
     * <p>The style sheets are applied before measuring, because the workbench font is smaller than the theme's
     * default and a dialog measured without it comes out needlessly tall.
     */
    public static void fitToContent(Stage stage, Region root, double preferredWidth) {
        root.setPrefWidth(preferredWidth);
        stage.setScene(new Scene(root));
        root.applyCss();
        root.layout();
        stage.setResizable(true);
        stage.sizeToScene();
        stage.setOnShown(shown -> {
            stage.setMinWidth(stage.getWidth());
            stage.setMinHeight(stage.getHeight());
        });
    }
}
