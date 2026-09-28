package io.github.dinamo541.idearm.app.editor;

import atlantafx.base.theme.PrimerDark;
import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CodeMinimapTest {
    @BeforeAll static void startFx() throws Exception {
        var latch = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); latch.countDown(); });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test void pointerAndKeyboardNavigateLongFileWithoutMovingSelection() throws Exception {
        onFx(() -> {
            var editor = new RichTextFxEditorComponent();
            editor.setText("    mov ax, 1 ; a styled instruction\n".repeat(5000));
            var stage = show(editor);
            try {
                var area = editor.getCodeArea();
                var map = (CodeMinimap) editor.getNode().lookup("#code-minimap");
                area.selectRange(4, 10);
                var selection = area.getSelection();
                layout(stage);
                double x = map.getWidth() / 2;
                double y = map.getHeight() * .7;
                Event.fireEvent(map, pointer(MouseEvent.MOUSE_PRESSED, x, y));
                layout(stage);
                assertTrue(area.visibleParToAllParIndex(0) > 3000, "Click navigates proportionally through long source");
                assertEquals(selection, area.getSelection(), "Overview navigation preserves the selected code");
                Event.fireEvent(map, pointer(MouseEvent.MOUSE_DRAGGED, x, map.getHeight()));
                layout(stage);
                assertTrue(area.visibleParToAllParIndex(0) > 4900, "Drag reaches the final part of the document");
                Event.fireEvent(map, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.HOME, false, false, false, false));
                layout(stage);
                assertEquals(0, area.visibleParToAllParIndex(0));
                assertEquals(selection, area.getSelection());
                area.showParagraphAtTop(2500);
                layout(stage);
                var viewport = (Region) map.lookup(".minimap-viewport");
                assertTrue(viewport.getLayoutY() > 100, "Viewport follows editor scrolling");
                assertTrue(viewport.getLayoutY() + viewport.getHeight() <= map.getHeight());
            } finally { stage.close(); }
        });
    }

    @Test void editsMarksThemeAndVisibilityStayInSync() throws Exception {
        onFx(() -> {
            var editor = new RichTextFxEditorComponent();
            editor.setText("mov ax, 1\n\n; comment\n\nret");
            var stage = show(editor);
            try {
                var map = (CodeMinimap) editor.getNode().lookup("#code-minimap");
                var canvas = (Canvas) map.getChildrenUnmodifiable().getFirst();
                var firstPaint = canvas.snapshot(null, null).getPixelReader().getColor(8, 8);
                editor.getCodeArea().replaceText(0, 3, "   ");
                layout(stage);
                assertNotEquals(firstPaint, canvas.snapshot(null, null).getPixelReader().getColor(8, 8),
                        "Typing repaints the miniature source");
                editor.setDiagnostics(List.of(new Diagnostic(Severity.ERROR, "test", "Problem",
                        new Location("test.asm", 3, 1), "test", "")));
                map.setDebugMarks(Set.of(5), 4);
                layout(stage);
                Color error = (Color) ((Rectangle) map.lookup(".minimap-error")).getFill();
                assertEquals(error, canvas.snapshot(null, null).getPixelReader().getColor((int) map.getWidth() - 4, 14));
                Color dark = (Color) ((Rectangle) map.lookup(".minimap-instruction")).getFill();
                stage.getScene().getRoot().getStyleClass().add("light");
                layout(stage);
                assertNotEquals(dark, ((Rectangle) map.lookup(".minimap-instruction")).getFill());
                double width = editor.getCodeArea().getWidth();
                editor.setMinimapVisible(false);
                layout(stage);
                assertFalse(map.isManaged());
                assertTrue(editor.getCodeArea().getWidth() > width + 90);
                editor.setMinimapVisible(true);
                editor.setText("");
                layout(stage);
                assertTrue(map.isManaged());
                var viewport = (Region) map.lookup(".minimap-viewport");
                assertTrue(viewport.getHeight() > 0);
                var locale = new Localization();
                editor.setLocalization(locale);
                locale.localeProperty().set(Localization.SPANISH);
                assertEquals("Minimapa del código", map.getAccessibleText());
            } finally { stage.close(); }
        });
    }

    private static Stage show(RichTextFxEditorComponent editor) {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        var root = new StackPane(editor.getNode());
        root.getStyleClass().add("workbench");
        root.getStylesheets().add(CodeMinimapTest.class.getResource("/io/github/dinamo541/idearm/app/view/workbench.css").toExternalForm());
        var stage = new Stage();
        stage.setScene(new Scene(root, 900, 600));
        stage.show();
        layout(stage);
        return stage;
    }

    private static void layout(Stage stage) {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        stage.getScene().getRoot().layout();
    }

    private static MouseEvent pointer(javafx.event.EventType<MouseEvent> type, double x, double y) {
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, true, null);
    }

    private static void onFx(Runnable action) throws Exception {
        var task = new FutureTask<Void>(() -> { action.run(); return null; });
        Platform.runLater(task);
        task.get(20, TimeUnit.SECONDS);
    }
}
