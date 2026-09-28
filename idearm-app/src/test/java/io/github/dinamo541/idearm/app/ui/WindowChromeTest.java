package io.github.dinamo541.idearm.app.ui;

import io.github.dinamo541.idearm.app.i18n.Localization;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.Path;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

// A bare Xvfb display has no window manager to implement maximize/minimize transitions.
@org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
class WindowChromeTest {
    @TempDir Path directory;
    private Stage stage;
    private HBox header;
    private Region title;
    private Localization text;

    @BeforeAll static void startFx() throws Exception {
        var started = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); });
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @BeforeEach void open() throws Exception {
        fx(() -> {
            text = new Localization(); stage = new Stage(StageStyle.UNDECORATED);
            title = new Region(); title.setPrefSize(400, 36);
            header = new HBox(title, WindowChrome.controls(stage, text));
            var root = new BorderPane(); root.setTop(header);
            stage.setScene(new Scene(root, 900, 600));
            stage.setMinWidth(400); stage.setMinHeight(300);
            WindowChrome.install(stage, header, text);
            stage.show(); stage.setX(60); stage.setY(60);
        });
        settle();
    }

    @AfterEach void close() throws Exception { fx(() -> stage.hide()); }

    @Test void maximizeRestoreAndMinimizePreserveSize() throws Exception {
        fx(() -> button("window-maximize").fire()); settle();
        fx(() -> { assertTrue(stage.isMaximized()); button("window-maximize").fire(); }); settle();
        fx(() -> {
            assertFalse(stage.isMaximized()); assertEquals(900, stage.getWidth(), 3); assertEquals(600, stage.getHeight(), 3);
            button("window-minimize").fire();
        }); settle();
        fx(() -> { assertTrue(stage.isIconified()); stage.setIconified(false); });
    }

    @Test void fullScreenButtonShortcutAndRestoreStaySynchronized() throws Exception {
        fx(() -> button("window-fullscreen").fire()); settle();
        fx(() -> {
            assertTrue(stage.isFullScreen());
            assertEquals(text.get("window.exitFullScreen"), button("window-maximize").getAccessibleText());
            assertEquals(new KeyCodeCombination(KeyCode.ESCAPE), stage.getFullScreenExitKeyCombination());
            // Double-clicking the title in full screen must not change the underlying maximize state.
            mouse(title, MouseEvent.MOUSE_CLICKED, 100, 18, 100, 18, false, 2);
            assertFalse(stage.isMaximized());
            button("window-maximize").fire();
        }); settle();
        fx(() -> {
            assertFalse(stage.isFullScreen()); assertEquals(900, stage.getWidth(), 3);
            Event.fireEvent(stage.getScene(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F11, false, true, true, false));
        }); settle();
        fx(() -> { assertTrue(stage.isFullScreen()); button("window-fullscreen").fire(); });
    }

    @Test void fullScreenReturnsToMaximizedState() throws Exception {
        fx(() -> stage.setMaximized(true)); settle();
        fx(() -> button("window-fullscreen").fire()); settle();
        fx(() -> button("window-fullscreen").fire()); settle();
        fx(() -> assertTrue(stage.isMaximized()));
    }

    @Test void closeButtonAndAltF4HonorCancellation() throws Exception {
        fx(() -> {
            stage.setOnCloseRequest(Event::consume);
            button("window-close").fire(); assertTrue(stage.isShowing());
            Event.fireEvent(stage.getScene(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F4, false, false, true, false));
            assertTrue(stage.isShowing());
            stage.setOnCloseRequest(null); WindowChrome.requestClose(stage); assertFalse(stage.isShowing());
        });
    }

    @Test void tinyPointerMovementAndInteractiveHeaderDoNotDragWindow() throws Exception {
        fx(() -> {
            double x = stage.getX(); double y = stage.getY();
            mouse(title, MouseEvent.MOUSE_PRESSED, 100, 18, x + 100, y + 18, true, 1);
            mouse(title, MouseEvent.MOUSE_DRAGGED, 101, 19, x + 101, y + 19, true, 1);
            assertEquals(x, stage.getX()); assertEquals(y, stage.getY());
            mouse(title, MouseEvent.MOUSE_RELEASED, 101, 19, x + 101, y + 19, false, 1);
            var combo = new ComboBox<String>(); header.getChildren().add(combo);
            mouse(combo, MouseEvent.MOUSE_PRESSED, 100, 18, x + 100, y + 18, true, 1);
            mouse(combo, MouseEvent.MOUSE_DRAGGED, 140, 48, x + 140, y + 48, true, 1);
            assertEquals(x, stage.getX()); assertEquals(y, stage.getY());
        });
    }

    @ParameterizedTest @CsvSource({"1,300,-30,0,930,600", "899,300,30,0,930,600",
            "450,1,0,-30,900,630", "450,599,0,30,900,630", "1,1,-30,-30,930,630",
            "899,1,30,-30,930,630", "1,599,-30,30,930,630", "899,599,30,30,930,630"})
    void resizesAllEightEdges(double x, double y, double dx, double dy, double width, double height) throws Exception {
        fx(() -> {
            var root = stage.getScene().getRoot(); double sx = stage.getX() + x, sy = stage.getY() + y;
            mouse(root, MouseEvent.MOUSE_PRESSED, x, y, sx, sy, true, 1);
            mouse(root, MouseEvent.MOUSE_DRAGGED, x + dx, y + dy, sx + dx, sy + dy, true, 1);
            mouse(root, MouseEvent.MOUSE_RELEASED, x + dx, y + dy, sx + dx, sy + dy, false, 1);
            assertEquals(width, stage.getWidth(), 1); assertEquals(height, stage.getHeight(), 1);
        });
    }

    @Test void secondaryStagesAndAlertsReceiveVisibleIcons() throws Exception {
        fx(() -> {
            BrandLogo.install(); BrandLogo.apply(stage);
            var child = new Stage(); child.initOwner(stage); child.setScene(new Scene(new HBox(), 250, 150));
            var alert = new Alert(Alert.AlertType.INFORMATION); alert.initOwner(stage);
            try {
                child.show(); alert.show();
                for (Stage window : new Stage[] { stage, child, (Stage) alert.getDialogPane().getScene().getWindow() }) {
                    assertEquals(7, window.getIcons().size());
                    for (var icon : window.getIcons()) {
                        assertFalse(icon.isError());
                        assertTrue(icon.getPixelReader().getColor((int) icon.getWidth() / 2, (int) icon.getHeight() / 2).getOpacity() > .9);
                    }
                }
            } finally { alert.close(); child.close(); }
        });
    }

    @Test void savedMaximizedSessionKeepsNormalBounds() throws Exception {
        var file = directory.resolve("window.properties");
        fx(() -> { stage.hide(); WindowSession.install(stage, file); stage.show(); stage.setWidth(960); stage.setHeight(640); });
        settle();
        fx(() -> stage.setMaximized(true)); settle();
        fx(() -> stage.hide());
        var saved = WindowSession.read(file);
        assertTrue(saved.maximized()); assertEquals(960, saved.width(), 3); assertEquals(640, saved.height(), 3);
    }

    private Button button(String id) { return (Button) stage.getScene().lookup("#" + id); }
    private static void settle() throws InterruptedException { Thread.sleep(350); }
    private static void fx(Runnable action) throws Exception {
        var task = new FutureTask<Void>(() -> { action.run(); return null; }); Platform.runLater(task);
        try { task.get(10, TimeUnit.SECONDS); }
        catch (ExecutionException failure) {
            if (failure.getCause() instanceof AssertionError error) throw error;
            throw failure;
        }
    }
    private static void mouse(javafx.scene.Node target, javafx.event.EventType<MouseEvent> type,
                              double x, double y, double sx, double sy, boolean down, int clicks) {
        Event.fireEvent(target, new MouseEvent(type, x, y, sx, sy, MouseButton.PRIMARY, clicks,
                false, false, false, false, down, false, false, false, false, true, null));
    }
}
