package io.github.dinamo541.idearm.app;

import io.github.dinamo541.idearm.app.view.WorkbenchView;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * A scripted walk through the explorer that saves screenshots, for checking the UI without a person at the
 * screen. It only runs when {@code IDEARM_SMOKE_SNAPSHOT} names an output folder, and it creates one file in the
 * project it was given ({@code IDEARM_SMOKE_PROJECT}), so point it at a scratch copy.
 */
final class SmokeSnapshot {

    private SmokeSnapshot() {
    }

    /** Native pointer regression: the empty toolbar area must drag despite its control skin. */
    static void dragWindow(Stage stage, WorkbenchView view) {
        var robot = new javafx.scene.robot.Robot();
        var pointer = robot.getMousePosition();
        double[] initial = new double[4];
        boolean[] moved = {false};
        step(600, () -> {
            stage.toFront(); stage.requestFocus();
            var zone = view.lookup("#title-drag-area");
            var bounds = zone.localToScreen(zone.getBoundsInLocal());
            require(bounds.getWidth() > 20 && bounds.getHeight() > 10, "Pickable header drag area");
            initial[0] = stage.getX(); initial[1] = stage.getY();
            initial[2] = bounds.getCenterX(); initial[3] = bounds.getCenterY();
            robot.mouseMove(initial[2], initial[3]);
        }, () -> step(150, () -> robot.mousePress(javafx.scene.input.MouseButton.PRIMARY),
                () -> step(300, () -> robot.mouseMove(initial[2] + 40, initial[3] + 30),
                        () -> step(300, () -> robot.mouseRelease(javafx.scene.input.MouseButton.PRIMARY),
                                () -> step(300, () -> {
                                    require(Math.abs(stage.getX() - initial[0] - 40) < 3
                                            && Math.abs(stage.getY() - initial[1] - 30) < 3, "Hold and drag title bar");
                                    moved[0] = true;
                                    initial[0] = stage.getX(); initial[1] = stage.getY();
                                    robot.mouseMove(initial[2] + 70, initial[3] + 50);
                                }, () -> step(150, () -> {
                                    require(moved[0] && Math.abs(stage.getX() - initial[0]) < 3
                                            && Math.abs(stage.getY() - initial[1]) < 3, "Release stops dragging");
                                    System.out.println("DRAG SMOKE: native title hold, movement and release PASS");
                                }, () -> snapToTop(stage, view, robot, pointer)))))));
    }

    /** Dragging the title to the top edge of the screen must maximize the window, as native windows do. */
    private static void snapToTop(Stage stage, WorkbenchView view, javafx.scene.robot.Robot robot,
                                  javafx.geometry.Point2D restore) {
        require(!stage.isMaximized(), "Window is restored before the snap gesture");
        var zone = view.lookup("#title-drag-area");
        var bounds = zone.localToScreen(zone.getBoundsInLocal());
        double top = javafx.stage.Screen.getPrimary().getVisualBounds().getMinY();
        robot.mouseMove(bounds.getCenterX(), bounds.getCenterY());
        step(150, () -> robot.mousePress(javafx.scene.input.MouseButton.PRIMARY),
                () -> step(300, () -> robot.mouseMove(bounds.getCenterX(), top + 1),
                        () -> step(300, () -> robot.mouseRelease(javafx.scene.input.MouseButton.PRIMARY),
                                () -> step(300, () -> {
                                    require(stage.isMaximized(), "Dragging the title to the top edge maximizes the window");
                                    System.out.println("DRAG SMOKE: drag-to-top maximize PASS");
                                }, () -> { robot.mouseMove(restore); Platform.exit(); }))));
    }

    /** Verifies real editor key events and renders both themes without saving source edits. */
    static void visual(Stage stage, WorkbenchView view, Path outputFolder) {
        step(900, () -> {
            try {
                var document = view.getViewModel().getEditorArea().getActiveDocument();
                var editor = (io.github.dinamo541.idearm.app.editor.RichTextFxEditorComponent) document.getEditor();
                var area = editor.getCodeArea();
                String original = editor.getText();
                try {
                    editor.setText("one\ntwo\nthree");
                    area.moveTo(5);
                    Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, true, false));
                    require(area.getText().equals("one\nthree\ntwo"), "Alt+Down");
                    Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.UP, true, false, true, false));
                    require(area.getText().equals("one\nthree\ntwo\ntwo"), "Shift+Alt+Up");
                    Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.Z, false, true, false, false));
                    require(area.getText().equals("one\nthree\ntwo"), "Ctrl+Z");
                    Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SLASH, false, true, false, false));
                    require(area.getText().contains("; two"), "Ctrl+/");
                    Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.K, true, true, false, false));
                    require(area.getText().equals("one\nthree"), "Ctrl+Shift+K");
                    var clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                    var previous = new java.util.HashMap<javafx.scene.input.DataFormat, Object>();
                    clipboard.getContentTypes().forEach(format -> {
                        Object value = clipboard.getContent(format);
                        if (value != null) previous.put(format, value);
                    });
                    try {
                        editor.setText("one\ntwo"); area.moveTo(1);
                        Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.X, false, true, false, false));
                        require(area.getText().equals("two") && "one\n".equals(clipboard.getString()), "Ctrl+X empty selection");
                    } finally { clipboard.setContent(previous); }
                    editor.setText("one one");
                    editor.execute(io.github.dinamo541.idearm.app.editor.EditorCommand.REPLACE);
                    stage.getScene().getRoot().applyCss();
                    ((TextField) editor.getNode().lookup("#editor-find")).setText("one");
                    ((TextField) editor.getNode().lookup("#editor-replace")).setText("three");
                    editor.getNode().lookupAll(".editor-search .button").stream()
                            .filter(javafx.scene.control.Button.class::isInstance)
                            .map(javafx.scene.control.Button.class::cast)
                            .filter(button -> view.getLocalization().get("editor.replaceAll").equals(button.getText()))
                            .findFirst().orElseThrow().fire();
                    require(area.getText().equals("three three"), "Replace All with length changes");
                    area.undo();
                    require(area.getText().equals("one one"), "Replace All single undo");
                    Event.fireEvent(editor.getNode().lookup("#editor-find"),
                            new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
                    System.out.println("VISUAL SMOKE: editor shortcuts PASS");
                } finally { editor.setText(original); }
                save(stage, outputFolder.resolve("workbench-dark.png"));
                view.lookupAll(".workbench-toolbar .button").stream()
                        .filter(javafx.scene.control.Button.class::isInstance)
                        .map(javafx.scene.control.Button.class::cast)
                        .filter(button -> "theme-action".equals(button.getId())).findFirst().orElseThrow().fire();
                view.getLocalization().localeProperty().set(io.github.dinamo541.idearm.app.i18n.Localization.SPANISH);
            } catch (RuntimeException | AssertionError failure) {
                System.out.println("VISUAL SMOKE FAILED: " + failure);
            }
        }, () -> step(500, () -> save(stage, outputFolder.resolve("workbench-light-es.png")),
                () -> experience(stage, view, outputFolder)));
    }

    /** Real JavaFX coverage for MRU navigation, chrome, delayed hover, and the exported app mark. */
    private static void experience(Stage stage, WorkbenchView view, Path folder) {
        try {
            require(stage.getStyle() == javafx.stage.StageStyle.UNDECORATED, "Client-rendered title bar");
            var model = view.getViewModel();
            var currentProject = model.getCurrentProjectPath();
            var missing = new io.github.dinamo541.idearm.domain.model.RecentItem(
                    io.github.dinamo541.idearm.domain.model.RecentItem.Kind.PROJECT, folder.resolve("missing-project"));
            require(!model.openRecent(missing) && model.getCurrentProjectPath().equals(currentProject), "Missing recent project preserves workbench");
            Files.createDirectories(folder.resolve("sample-project/src"));
            Path file = Files.writeString(folder.resolve("sample-project/src/recientes.asm"), "; Recent navigation smoke fixture\nmov ax, 1\n");
            model.getEditorArea().openFile(file);
            model.openProject(folder.resolve("sample-project"));
            // Return to the real example without saving its in-memory keyboard-test buffer.
            model.openProject(currentProject);
            model.getRecentItems().remember(io.github.dinamo541.idearm.domain.model.RecentItem.Kind.FILE, file);
            saveImage(io.github.dinamo541.idearm.app.ui.BrandLogo.image(512), folder.resolve("idearm-logo.png"));
            saveImage(io.github.dinamo541.idearm.app.ui.BrandLogo.image(256), folder.resolve("idearm-icon-256.png"));
            var dialog = new io.github.dinamo541.idearm.app.view.OpenRecentDialog(stage, model, view.getLocalization());
            dialog.show();
            step(350, () -> {
                var field = (TextField) dialog.getScene().lookup("#recent-search");
                var list = (javafx.scene.control.ListView<?>) dialog.getScene().lookup("#recent-list");
                field.setText("recientes.asm");
                require(list.getItems().size() == 1, "Recent search filters full history");
                field.setText("");
                save(dialog, folder.resolve("recent-light-es.png"));
                field.setText("recientes.asm");
                Event.fireEvent(field, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
                require(!dialog.isShowing(), "Enter opens recent file");
                require(model.getEditorArea().getActiveDocument().getFilePath().equals(file.toAbsolutePath().normalize()), "Correct recent file activated");
                ((javafx.scene.control.Button) view.lookup("#theme-action")).fire();
                view.getLocalization().localeProperty().set(io.github.dinamo541.idearm.app.i18n.Localization.ENGLISH);
                var dark = new io.github.dinamo541.idearm.app.view.OpenRecentDialog(stage, model, view.getLocalization());
                dark.show();
                step(300, () -> { save(dark, folder.resolve("recent-dark.png")); dark.close(); },
                        () -> verifyChrome(stage, view, folder));
            }, () -> {});
        } catch (IOException | RuntimeException | AssertionError failure) {
            System.out.println("EXPERIENCE SMOKE FAILED: " + failure); Platform.exit();
        }
    }

    private static void verifyChrome(Stage stage, WorkbenchView view, Path folder) {
        var maximize = (javafx.scene.control.Button) view.lookup("#window-maximize");
        double width = stage.getWidth();
        maximize.fire();
        step(350, () -> {
            require(stage.isMaximized(), "Maximize control");
            maximize.fire();
        }, () -> step(350, () -> {
            require(!stage.isMaximized() && Math.abs(stage.getWidth() - width) < 5, "Restore previous window size");
            ((javafx.scene.control.Button) view.lookup("#window-minimize")).fire();
        }, () -> step(350, () -> {
            require(stage.isIconified(), "Minimize control"); stage.setIconified(false); stage.toFront();
        }, () -> step(350, () -> {
            var button = (javafx.scene.control.Button) view.lookup("#recent-action");
            require(button.getTooltip().getShowDelay().toMillis() == 450, "Delayed hover help");
            var bounds = button.localToScreen(button.getBoundsInLocal());
            var robot = new javafx.scene.robot.Robot();
            robot.mouseMove(bounds.getCenterX(), bounds.getCenterY());
        }, () -> step(1100, () -> {
            var tip = ((javafx.scene.control.Button) view.lookup("#recent-action")).getTooltip();
            require(tip.isShowing(), "Hover help appears after pointer rests");
            saveImage(tip.getScene().snapshot(null), folder.resolve("hover-help.png")); tip.hide();
            // A dirty buffer must allow cancellation through the same event used by the close button.
            var doc = view.getViewModel().getEditorArea().getActiveDocument();
            ((io.github.dinamo541.idearm.app.editor.RichTextFxEditorComponent) doc.getEditor())
                    .getCodeArea().appendText("; unsaved smoke edit\n");
            require(doc.isModified(), "Unsaved buffer fixture");
            Platform.runLater(() -> javafx.stage.Window.getWindows().stream()
                    .filter(window -> window != stage && window.getScene() != null)
                    .map(window -> window.getScene().getRoot()).filter(javafx.scene.control.DialogPane.class::isInstance)
                    .map(javafx.scene.control.DialogPane.class::cast).findFirst().ifPresent(pane ->
                        pane.getButtonTypes().stream().filter(type -> type.getButtonData() == javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE)
                                .findFirst().ifPresent(type -> ((javafx.scene.control.Button) pane.lookupButton(type)).fire())));
            ((javafx.scene.control.Button) view.lookup("#window-close")).fire();
            require(stage.isShowing(), "Cancel closing dirty editor");
            System.out.println("EXPERIENCE SMOKE: recent picker, window controls, delayed hover and close cancellation PASS");
        }, Platform::exit)))));
    }

    private static void require(boolean condition, String action) {
        if (!condition) throw new AssertionError(action);
    }

    static void run(Stage stage, WorkbenchView view, Path outputFolder) {
        step(900, () -> view.getExplorerView().newFile(), () ->
                step(700, () -> typeInExplorer(view, "Utils.ASM"), () ->
                        step(600, () -> save(stage, outputFolder.resolve("explorer-new-file.png")), () ->
                                step(100, () -> pressEnter(view), () ->
                                        step(900, () -> save(stage, outputFolder.resolve("explorer-created.png")),
                                                Platform::exit)))));
    }

    private static void step(double millis, Runnable action, Runnable next) {
        var pause = new PauseTransition(Duration.millis(millis));
        // Modal dialogs cannot enter a nested loop from an animation pulse.
        pause.setOnFinished(event -> Platform.runLater(() -> {
            try {
                action.run();
            } catch (RuntimeException | AssertionError failure) {
                System.out.println("SMOKE STEP FAILED: " + failure);
            }
            next.run();
        }));
        pause.play();
    }

    private static void typeInExplorer(WorkbenchView view, String text) {
        field(view).setText(text);
    }

    private static void pressEnter(WorkbenchView view) {
        TextField field = field(view);
        Event.fireEvent(field, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
    }

    private static TextField field(WorkbenchView view) {
        return view.getExplorerView().lookupAll(".text-field").stream()
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The explorer is not editing a name."));
    }

    private static void save(Stage stage, Path file) {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        WritableImage image = stage.getScene().snapshot(null);
        saveImage(image, file);
    }

    private static void saveImage(WritableImage image, Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                writePng(image, out);
            }
            System.out.println("SMOKE SNAPSHOT " + file);
        } catch (IOException failure) {
            System.out.println("SMOKE SNAPSHOT FAILED: " + failure);
        }
    }

    /** A plain RGBA PNG; JavaFX has no image writer without the Swing bridge. */
    private static void writePng(WritableImage image, OutputStream out) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader pixels = image.getPixelReader();

        var raw = new ByteArrayOutputStream();
        try (var compressed = new DeflaterOutputStream(raw)) {
            byte[] row = new byte[1 + width * 4];
            for (int y = 0; y < height; y++) {
                row[0] = 0;
                for (int x = 0; x < width; x++) {
                    int argb = pixels.getArgb(x, y);
                    int offset = 1 + x * 4;
                    row[offset] = (byte) (argb >> 16);
                    row[offset + 1] = (byte) (argb >> 8);
                    row[offset + 2] = (byte) argb;
                    row[offset + 3] = (byte) (argb >>> 24);
                }
                compressed.write(row);
            }
        }

        var header = new ByteArrayOutputStream();
        var headerData = new DataOutputStream(header);
        headerData.writeInt(width);
        headerData.writeInt(height);
        headerData.writeByte(8);
        headerData.writeByte(6);
        headerData.writeByte(0);
        headerData.writeByte(0);
        headerData.writeByte(0);

        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        chunk(out, "IHDR", header.toByteArray());
        chunk(out, "IDAT", raw.toByteArray());
        chunk(out, "IEND", new byte[0]);
    }

    private static void chunk(OutputStream out, String type, byte[] data) throws IOException {
        var stream = new DataOutputStream(out);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        stream.writeInt(data.length);
        stream.write(typeBytes);
        stream.write(data);
        var crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        stream.writeInt((int) crc.getValue());
    }
}
