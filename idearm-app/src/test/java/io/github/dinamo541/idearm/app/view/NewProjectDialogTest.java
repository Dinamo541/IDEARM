package io.github.dinamo541.idearm.app.view;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.domain.model.Project;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The dialog used to fix its own height in the source. Six rows of form did not fit in it, so the button bar was
 * pushed out of the window and Create became unreachable; the window was not resizable either, so there was no
 * way back. These tests keep the height a consequence of the rows.
 */
class NewProjectDialogTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        FxDialogs.startJavaFx();
    }

    @Test
    void theCreateButtonIsInsideTheWindow() throws Exception {
        Boolean fits = onDialog(dialog -> {
            FxDialogs.assertFitsInsideItsWindow(dialog, create(dialog), "the Create button");
            return true;
        });

        assertTrue(fits);
    }

    @Test
    void theWindowHasAFloorSoDraggingItCannotHideTheButtonAgain() throws Exception {
        Boolean guarded = onDialog(dialog -> {
            assertTrue(dialog.isResizable(), "the user must be able to enlarge the dialog");
            Node create = create(dialog);
            double needed = create.localToScene(create.getBoundsInLocal()).getMaxY();
            assertTrue(dialog.getMinHeight() >= needed,
                    "a floor of " + dialog.getMinHeight() + " does not cover content reaching " + needed);
            return true;
        });

        assertTrue(guarded);
    }

    /** Every row of the form has to be reachable, not only the buttons below them. */
    @Test
    void everyFormRowIsInsideTheWindow() throws Exception {
        Boolean fits = onDialog(dialog -> {
            double sceneHeight = dialog.getScene().getHeight();
            for (Node row : dialog.getScene().getRoot().lookupAll(".combo-box")) {
                double bottom = row.localToScene(row.getBoundsInLocal()).getMaxY();
                assertTrue(bottom <= sceneHeight + 1,
                        "a form row reaches " + bottom + " in a window " + sceneHeight + " tall");
            }
            return true;
        });

        assertTrue(fits);
    }

    private static Node create(javafx.stage.Stage dialog) {
        Node found = dialog.getScene().lookup("#createProject");
        if (found == null) {
            throw new AssertionError("the dialog has no Create button");
        }
        return found;
    }

    private <T> T onDialog(FxDialogs.DialogCall<T> body) throws Exception {
        Files.writeString(tempDir.resolve("idearm.toml"), "schema = 1", StandardCharsets.UTF_8);
        Project project = FxDialogs.withSources(Project.hello("SAMPLE"), "src/main.asm",
                List.of("src/*.asm"), List.of());
        return FxDialogs.onFxThread(() -> {
            var viewModel = FxDialogs.workbench(tempDir, project, new ArrayList<>());
            viewModel.openProject(tempDir);
            var dialog = new NewProjectDialog(null, viewModel, new Localization());
            try {
                dialog.show();
                dialog.getScene().getRoot().applyCss();
                dialog.getScene().getRoot().layout();
                return body.apply(dialog);
            } finally {
                dialog.hide();
                viewModel.dispose();
            }
        });
    }
}
