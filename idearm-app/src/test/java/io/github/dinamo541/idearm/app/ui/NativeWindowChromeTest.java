package io.github.dinamo541.idearm.app.ui;

import io.github.dinamo541.idearm.app.i18n.Localization;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;

import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real Glass/Win32 frame without injecting desktop keyboard or pointer input. */
@EnabledOnOs(OS.WINDOWS)
@SuppressWarnings("deprecation")
class NativeWindowChromeTest {
    @TempDir Path directory;
    private Stage stage;
    private Region dragArea;
    private Button action;

    @BeforeAll static void startFx() throws Exception {
        var started = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); });
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @BeforeEach void open() throws Exception {
        fx(() -> {
            assertEquals(StageStyle.EXTENDED, WindowChrome.stageStyle(), "Native Windows frame must be enabled");
            stage = new Stage(WindowChrome.stageStyle());
            stage.setTitle("IDEARM native window test " + UUID.randomUUID());
            dragArea = new Region(); dragArea.setPrefSize(300, 40);
            HBox.setHgrow(dragArea, javafx.scene.layout.Priority.ALWAYS);
            WindowChrome.draggable(dragArea);
            action = new Button("Menu action");
            var header = new HBox(action, dragArea, WindowChrome.controls(stage, new Localization()));
            var root = new BorderPane(); root.setTop(WindowChrome.header(header));
            stage.setScene(new Scene(root, 900, 600));
            stage.setMinWidth(500); stage.setMinHeight(360);
            WindowChrome.install(stage, header, new Localization());
            stage.show(); stage.setX(80); stage.setY(80);
        });
        settle();
    }

    @AfterEach void close() throws Exception { fx(() -> { if (stage != null) stage.hide(); }); }

    @Test void windowsRecognizesCaptionMaximizeAndInteractiveExclusions() throws Exception {
        fx(() -> {
            try (var probe = new WindowProbe(stage)) {
                assertEquals(2, probe.hit(dragArea), "HTCAPTION starts the native drag/snap loop");
                assertEquals(9, probe.hit(button("window-maximize")), "HTMAXBUTTON enables the OS Snap Layout flyout");
                assertEquals(1, probe.hit(action), "Menu/toolbar controls must remain HTCLIENT");
                assertEquals(1, probe.hit(button("window-fullscreen")), "Full-screen remains an application action");
                assertTrue((probe.style() & 0x00040000) != 0, "WS_THICKFRAME enables native sizing");
                assertTrue((probe.style() & 0x00010000) != 0, "WS_MAXIMIZEBOX enables snap/maximize");
                assertTrue((probe.style() & 0x00080000) != 0, "WS_SYSMENU enables the real window menu");
            }
        });
    }

    @Test void clickActivatesOnlyOnceAndReleaseOutsideCancels() throws Exception {
        fx(() -> release(button("window-maximize"), true)); settle();
        fx(() -> assertTrue(stage.isMaximized(), "Mouse release must not toggle maximize twice"));
        fx(() -> release(button("window-maximize"), false)); settle();
        fx(() -> assertTrue(stage.isMaximized(), "Dragging out of the button cancels activation"));
        fx(() -> release(button("window-maximize"), true)); settle();
        fx(() -> assertFalse(stage.isMaximized()));
    }

    @Test void windowsOwnsAllEightResizeDirections() throws Exception {
        fx(() -> {
            try (var probe = new WindowProbe(stage)) {
                assertArrayEquals(new long[]{13, 12, 14, 10, 11, 16, 15, 17}, probe.frameHits(),
                        "HTTOPLEFT, HTTOP, HTTOPRIGHT, HTLEFT, HTRIGHT, HTBOTTOMLEFT, HTBOTTOM, HTBOTTOMRIGHT");
            }
        });
    }

    @Test void fullScreenAndCloseCancellationPreserveExistingActions() throws Exception {
        fx(() -> button("window-fullscreen").fire()); settle();
        fx(() -> {
            assertTrue(stage.isFullScreen());
            assertTrue(button("window-maximize").isVisible());
            release(button("window-maximize"), true);
        }); settle();
        fx(() -> {
            assertFalse(stage.isFullScreen());
            int[] requests = {0};
            stage.setOnCloseRequest(e -> { requests[0]++; e.consume(); });
            release(button("window-close"), true);
            assertEquals(1, requests[0]);
            assertTrue(stage.isShowing());
            try (var probe = new WindowProbe(stage)) { probe.requestClose(); }
            assertEquals(2, requests[0], "The native WM_CLOSE path also reaches close cancellation");
            assertTrue(stage.isShowing());
            stage.setOnCloseRequest(null);
            button("window-close").fire();
            assertFalse(stage.isShowing());
        });
    }

    @Test void maximizeFullScreenMinimizeAndSavedBoundsRoundTrip() throws Exception {
        var file = directory.resolve("window.properties");
        fx(() -> { stage.hide(); WindowSession.install(stage, file); stage.show(); stage.setWidth(960); stage.setHeight(640); });
        settle();
        fx(() -> button("window-maximize").fire()); settle();
        fx(() -> button("window-fullscreen").fire()); settle();
        fx(() -> button("window-fullscreen").fire()); settle();
        fx(() -> { assertTrue(stage.isMaximized()); button("window-minimize").fire(); }); settle();
        fx(() -> { assertTrue(stage.isIconified()); stage.setIconified(false); }); settle();
        fx(() -> stage.hide());
        var saved = WindowSession.read(file);
        assertTrue(saved.maximized());
        assertEquals(960, saved.width(), 3); assertEquals(640, saved.height(), 3);
    }

    private Button button(String id) { return (Button) stage.getScene().lookup("#" + id); }

    private static void release(Button button, boolean inside) {
        button.arm();
        double x = inside ? button.getWidth() / 2 : button.getWidth() + 20;
        var point = button.localToScene(x, button.getHeight() / 2);
        Event.fireEvent(button, new MouseEvent(MouseEvent.MOUSE_RELEASED, point.getX(), point.getY(), 0, 0,
                MouseButton.PRIMARY, 1, false, false, false, false, false, false, false, false, false, true, null));
    }

    private static void settle() throws InterruptedException { Thread.sleep(300); }

    private static void fx(Runnable action) throws Exception {
        var task = new FutureTask<Void>(() -> { action.run(); return null; });
        Platform.runLater(task);
        try { task.get(10, TimeUnit.SECONDS); }
        catch (ExecutionException failure) {
            if (failure.getCause() instanceof AssertionError error) throw error;
            throw failure;
        }
    }

    /** Native protocol checks restricted to the uniquely titled test stage. */
    private static final class WindowProbe implements AutoCloseable {
        private final Arena arena = Arena.ofConfined();
        private final SymbolLookup user32 = SymbolLookup.libraryLookup("user32", arena);
        private final Stage stage;
        private final MemorySegment hwnd;

        WindowProbe(Stage stage) {
            this.stage = stage;
            hwnd = (MemorySegment) call("FindWindowW", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS),
                    MemorySegment.NULL, arena.allocateFrom(stage.getTitle(), StandardCharsets.UTF_16LE));
            assertNotEquals(0, hwnd.address(), "The test's native window exists");
        }

        int style() {
            return (int) call("GetWindowLongW", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT), hwnd, -16);
        }

        void requestClose() {
            call("SendMessageW", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG), hwnd, 0x0010, 0L, 0L);
        }

        long hit(Node node) {
            var bounds = node.localToScene(node.getBoundsInLocal());
            var point = arena.allocate(8, 4);
            point.set(JAVA_INT, 0, (int) Math.round(bounds.getCenterX() * stage.getOutputScaleX()));
            point.set(JAVA_INT, 4, (int) Math.round(bounds.getCenterY() * stage.getOutputScaleY()));
            assertNotEquals(0, call("ClientToScreen", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS), hwnd, point));
            return hitPixel(point.get(JAVA_INT, 0), point.get(JAVA_INT, 4));
        }

        long[] frameHits() {
            var rect = arena.allocate(16, 4);
            assertNotEquals(0, call("GetWindowRect", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS), hwnd, rect));
            int left = rect.get(JAVA_INT, 0) + 1, top = rect.get(JAVA_INT, 4) + 1;
            int right = rect.get(JAVA_INT, 8) - 2, bottom = rect.get(JAVA_INT, 12) - 2;
            int cx = (left + right) / 2, cy = (top + bottom) / 2;
            return new long[]{hitPixel(left, top), hitPixel(cx, top), hitPixel(right, top),
                    hitPixel(left, cy), hitPixel(right, cy), hitPixel(left, bottom), hitPixel(cx, bottom), hitPixel(right, bottom)};
        }

        private long hitPixel(int x, int y) {
            long packed = (x & 0xffffL) | ((y & 0xffffL) << 16);
            return (long) call("SendMessageW", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT, JAVA_LONG, JAVA_LONG),
                    hwnd, 0x0084, 0L, packed);
        }

        private Object call(String name, FunctionDescriptor descriptor, Object... args) {
            MethodHandle function = Linker.nativeLinker().downcallHandle(user32.find(name).orElseThrow(), descriptor);
            try { return function.invokeWithArguments(args); }
            catch (Throwable failure) { throw new AssertionError(name, failure); }
        }

        @Override public void close() { arena.close(); }
    }
}
