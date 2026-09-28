package io.github.dinamo541.idearm.app.editor;

import io.github.dinamo541.idearm.application.editor.CompletionItem;
import io.github.dinamo541.idearm.application.editor.CompletionKind;
import io.github.dinamo541.idearm.application.editor.HoverInfo;
import io.github.dinamo541.idearm.application.editor.HoverKind;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CompletionPopupTest {

    @BeforeAll
    static void initJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.setImplicitExit(false);
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void navigationKeysWrapCircularly() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                var popup = new CompletionPopup();
                var items = List.of(
                        CompletionItem.of("AX", CompletionKind.REGISTER, "Register"),
                        CompletionItem.of("BX", CompletionKind.REGISTER, "Register"),
                        CompletionItem.of("CX", CompletionKind.REGISTER, "Register")
                );

                var stage = new Stage();
                var scene = new Scene(new StackPane(), 300, 300);
                stage.setScene(scene);
                stage.show();

                popup.showCompletions(scene.getRoot(), 50, 50, items, selected -> {});

                // Initially index 0 (AX)
                // UP should wrap to last index: 2 (CX)
                KeyEvent upKey = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.UP, false, false, false, false);
                boolean consumedUp = popup.handleEditorKeyEvent(upKey);
                assertTrue(consumedUp);

                AtomicReference<String> committed = new AtomicReference<>();
                popup.showCompletions(scene.getRoot(), 50, 50, items, item -> committed.set(item.label()));

                popup.handleEditorKeyEvent(upKey);
                KeyEvent enterKey = new KeyEvent(KeyEvent.KEY_PRESSED, "\r", "\r", KeyCode.ENTER, false, false, false, false);
                popup.handleEditorKeyEvent(enterKey);
                assertEquals("CX", committed.get(), "UP from first item must wrap to last item CX");

                // Now test DOWN wrap from CX to AX
                popup.showCompletions(scene.getRoot(), 50, 50, items, item -> committed.set(item.label()));
                popup.handleEditorKeyEvent(upKey); // now at CX (index 2)
                KeyEvent downKey = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false);
                popup.handleEditorKeyEvent(downKey); // wraps to index 0 (AX)
                popup.handleEditorKeyEvent(enterKey);
                assertEquals("AX", committed.get(), "DOWN from last item must wrap to first item AX");

                popup.hide();
                stage.close();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (error.get() != null) throw new AssertionError(error.get());
    }

    @Test
    void enterInsertsCommandInEditor() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                var editor = new RichTextFxEditorComponent();
                var stage = new Stage();
                var scene = new Scene(new StackPane(editor.getNode()), 600, 400);
                stage.setScene(scene);
                stage.show();

                editor.setText("m");
                editor.getCodeArea().moveTo(1);
                editor.setCompletionProvider(prefix -> List.of(
                        CompletionItem.of("MOV", CompletionKind.INSTRUCTION, "Instruction"),
                        CompletionItem.of("MUL", CompletionKind.INSTRUCTION, "Instruction")
                ));

                // Trigger Ctrl+Space
                KeyEvent ctrlSpace = new KeyEvent(KeyEvent.KEY_PRESSED, " ", " ", KeyCode.SPACE, false, true, false, false);
                Event.fireEvent(editor.getCodeArea(), ctrlSpace);

                // Press DOWN to select MUL
                KeyEvent down = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false);
                Event.fireEvent(editor.getCodeArea(), down);

                // Press ENTER
                KeyEvent enter = new KeyEvent(KeyEvent.KEY_PRESSED, "\r", "\r", KeyCode.ENTER, false, false, false, false);
                Event.fireEvent(editor.getCodeArea(), enter);

                assertEquals("MUL", editor.getText(), "Enter must replace prefix with selected command MUL");

                stage.close();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (error.get() != null) throw new AssertionError(error.get());
    }

    @Test
    void outsideClickDismissesPopup() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                var editor = new RichTextFxEditorComponent();
                var stage = new Stage();
                var scene = new Scene(new StackPane(editor.getNode()), 600, 400);
                stage.setScene(scene);
                stage.setX(100);
                stage.setY(100);
                stage.show();

                editor.setText("mov ");
                editor.getCodeArea().moveTo(4);
                editor.setCompletionProvider(prefix -> List.of(
                        CompletionItem.of("AX", CompletionKind.REGISTER, "Register")
                ));

                // Trigger Ctrl+Space
                KeyEvent ctrlSpace = new KeyEvent(KeyEvent.KEY_PRESSED, " ", " ", KeyCode.SPACE, false, true, false, false);
                Event.fireEvent(editor.getCodeArea(), ctrlSpace);

                var field = RichTextFxEditorComponent.class.getDeclaredField("completionPopup");
                field.setAccessible(true);
                CompletionPopup popup = (CompletionPopup) field.get(editor);

                assertTrue(popup.isShowing(), "Popup should be open");

                // Click outside popup bounds
                MouseEvent outsideClick = new MouseEvent(MouseEvent.MOUSE_PRESSED,
                        9999, 9999, 9999, 9999, MouseButton.PRIMARY, 1,
                        false, false, false, false, true, false, false, false, false, false, null);
                Event.fireEvent(scene, outsideClick);

                assertFalse(popup.isShowing(), "Popup must be dismissed when clicking outside");

                stage.close();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (error.get() != null) throw new AssertionError(error.get());
    }

    @Test
    void editorScrollDismissesPopup() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                var editor = new RichTextFxEditorComponent();
                var stage = new Stage();
                var scene = new Scene(new StackPane(editor.getNode()), 600, 400);
                stage.setScene(scene);
                stage.show();

                editor.setText("mov ");
                editor.getCodeArea().moveTo(4);
                editor.setCompletionProvider(prefix -> List.of(
                        CompletionItem.of("AX", CompletionKind.REGISTER, "Register")
                ));

                // Open completion popup
                KeyEvent ctrlSpace = new KeyEvent(KeyEvent.KEY_PRESSED, " ", " ", KeyCode.SPACE, false, true, false, false);
                Event.fireEvent(editor.getCodeArea(), ctrlSpace);

                var field = RichTextFxEditorComponent.class.getDeclaredField("completionPopup");
                field.setAccessible(true);
                CompletionPopup popup = (CompletionPopup) field.get(editor);

                assertTrue(popup.isShowing(), "Popup should be open");

                // Scroll event
                ScrollEvent scroll = new ScrollEvent(ScrollEvent.SCROLL, 0, 0, 0, 0, false, false, false, false,
                        false, false, 0, -10, 0, -10, ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                        ScrollEvent.VerticalTextScrollUnits.LINES, 1, 0, null);
                Event.fireEvent(editor.getCodeArea(), scroll);

                assertFalse(popup.isShowing(), "Scrolling editor must dismiss completion popup");

                stage.close();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (error.get() != null) throw new AssertionError(error.get());
    }

    @Test
    void hoverCardDismissesOnOutsideClick() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                var hoverPopup = new HoverCardPopup();
                var stage = new Stage();
                var scene = new Scene(new StackPane(), 600, 400);
                stage.setScene(scene);
                stage.show();

                HoverInfo info = new HoverInfo("MOV", "mov dest, src", "Copy src to dest", null, null, HoverKind.INSTRUCTION);
                hoverPopup.showHover(scene.getRoot(), 100, 100, info, null);

                assertTrue(hoverPopup.isShowing(), "Hover card should be open");

                // Click outside hover bounds
                MouseEvent outsideClick = new MouseEvent(MouseEvent.MOUSE_PRESSED,
                        9999, 9999, 9999, 9999, MouseButton.PRIMARY, 1,
                        false, false, false, false, true, false, false, false, false, false, null);
                Event.fireEvent(scene, outsideClick);

                assertFalse(hoverPopup.isShowing(), "Hover card must dismiss on outside click");

                stage.close();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        if (error.get() != null) throw new AssertionError(error.get());
    }
}
