package io.github.dinamo541.idearm.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.domain.model.Project;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectPropertiesDialogTest {

    @TempDir
    Path tempDir;

    private final List<Project> saved = new ArrayList<>();

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        FxDialogs.startJavaFx();
    }

    @Test
    void showsTheIncludeFoldersTheProjectDeclares() throws Exception {
        project("src/main.asm", List.of("src/*.asm"), List.of("sprite"));

        List<?> folders = onDialog(dialog ->
                ((ListView<?>) lookup(dialog, "#includeFolders")).getItems().stream().toList());

        assertEquals(List.of("sprite"), folders);
    }

    /** The row the user asked for: which .asm runs, chosen without editing idearm.toml by hand. */
    @Test
    void offersEveryAssemblySourceAsTheMainFile() throws Exception {
        project("src/main.asm", List.of("src/*.asm"), List.of());
        write("src/main.asm");
        write("src/ejercicio2.asm");
        write("sprite/manzana.inc");
        write("build/debug/obj/main.asm");

        List<?> sources = onDialog(dialog ->
                ((ComboBox<?>) lookup(dialog, "#mainFileChooser")).getItems().stream().toList());

        // Only .asm, and nothing out of a generated folder.
        assertEquals(List.of("src/ejercicio2.asm", "src/main.asm"), sources);
    }

    @Test
    void theSwitchReflectsWhetherTheProjectBuildsOnlyTheMainFile() throws Exception {
        project("src/main.asm", List.of("src/*.asm"), List.of());
        write("src/main.asm");

        boolean withModules = onDialog(dialog -> ((CheckBox) lookup(dialog, "#buildOnlyMainFile")).isSelected());
        assertFalse(withModules, "a project that builds several modules is not building only the main file");

        project("src/main.asm", List.of(), List.of());
        boolean alone = onDialog(dialog -> ((CheckBox) lookup(dialog, "#buildOnlyMainFile")).isSelected());
        assertTrue(alone, "an empty module list means the main file is the whole program");
    }

    /** The bug this dialog shares with New Project: a window too short to show its own button. */
    @Test
    void everyRowFitsAndTheCloseButtonIsVisible() throws Exception {
        project("src/main.asm", List.of("src/*.asm"), List.of("sprite"));
        write("src/main.asm");

        Boolean fits = onDialog(dialog -> {
            FxDialogs.assertFitsInsideItsWindow(dialog, lookup(dialog, "#closeDialog"), "the Close button");
            assertTrue(dialog.isResizable(), "a dialog the user cannot enlarge cannot recover from a clipped row");
            return true;
        });

        assertTrue(fits);
    }

    // ---------------------------------------------------------------- fixture

    private Project current;

    private void project(String entry, List<String> modules, List<String> include) throws IOException {
        Files.writeString(tempDir.resolve("idearm.toml"), "schema = 1", StandardCharsets.UTF_8);
        current = FxDialogs.withSources(Project.hello("MANZANA"), entry, modules, include);
    }

    private void write(String relative) throws IOException {
        Path file = tempDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "; test\n", StandardCharsets.UTF_8);
    }

    private static Node lookup(javafx.stage.Stage dialog, String id) {
        Node found = dialog.getScene().lookup(id);
        if (found == null) {
            throw new AssertionError("no control with id " + id);
        }
        return found;
    }

    /** Builds, shows and closes the dialog on the JavaFX thread, so measurements are the ones the user sees. */
    private <T> T onDialog(FxDialogs.DialogCall<T> body) throws InterruptedException {
        return FxDialogs.onFxThread(() -> {
            var viewModel = FxDialogs.workbench(tempDir, current, saved);
            viewModel.openProject(tempDir);
            var dialog = new ProjectPropertiesDialog(null, viewModel, new Localization());
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
