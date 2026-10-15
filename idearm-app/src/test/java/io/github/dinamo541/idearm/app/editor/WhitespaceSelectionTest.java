package io.github.dinamo541.idearm.app.editor;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.fxmisc.richtext.TextExt;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WhitespaceSelectionTest {

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
    void testWhitespaceSelectionRenderer() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                var editor = new RichTextFxEditorComponent();
                var stage = new Stage();
                var scene = new Scene(new StackPane(editor.getNode()), 600, 400);
                stage.setScene(scene);
                stage.show();

                String initialCode = ".CODE\nINCLUDE menu.inc\n    mov ax, 0013h\n    ret\nmain PROC\n    mov ax, @data";
                editor.setText(initialCode);
                var area = editor.getCodeArea();
                area.layout();

                var renderer = new WhitespaceSelectionRenderer(area);

                // Select from 'INCLUDE' down to 'ret'
                int start = initialCode.indexOf("INCLUDE");
                int end = initialCode.indexOf("    ret") + "    ret".length();
                area.selectRange(start, end);
                area.layout();
                renderer.updateWhitespace();

                // Check text of TextExt nodes
                var boxes = new ArrayList<>(area.lookupAll(".paragraph-box"));
                boxes.sort(java.util.Comparator.comparingDouble(n -> n.getBoundsInParent().getMinY()));
                
                // Paragraph 0 (.CODE) -> no selection, no dots
                // Paragraph 1 (INCLUDE menu.inc) -> selected, should have "INCLUDE·menu.inc"
                // Paragraph 2 (    mov ax, 0013h) -> selected, should have "····mov·ax,·0013h"
                // Paragraph 4 (main PROC) -> not selected, should have normal space
                // Paragraph 5 (    mov ax, @data) -> not selected, should have 4 normal spaces

                String par1Text = getBoxRenderedText(boxes.get(1));
                String par2Text = getBoxRenderedText(boxes.get(2));
                String par4Text = getBoxRenderedText(boxes.get(4));
                String par5Text = getBoxRenderedText(boxes.get(5));

                System.out.println("Par 1 rendered: " + par1Text);
                System.out.println("Par 2 rendered: " + par2Text);
                System.out.println("Par 4 rendered: " + par4Text);
                System.out.println("Par 5 rendered: " + par5Text);

                assertTrue(par1Text.contains("INCLUDE\u00B7menu.inc"), "Par 1 should contain middle dot");
                assertTrue(par2Text.startsWith("\u00B7\u00B7\u00B7\u00B7"), "Par 2 should have 4 middle dots for indent");
                assertTrue(par2Text.contains("mov\u00B7ax,\u00B70013h") || par2Text.contains("mov\u00B7ax,\u00B7"), "Par 2 spaces should be middle dots");
                assertFalse(par4Text.contains("\u00B7"), "Unselected Par 4 must NOT contain middle dots");
                assertTrue(par5Text.startsWith("    "), "Unselected Par 5 must have normal spaces");

                // Clear selection
                area.deselect();
                area.layout();
                renderer.updateWhitespace();

                String par1Cleared = getBoxRenderedText(boxes.get(1));
                String par2Cleared = getBoxRenderedText(boxes.get(2));
                System.out.println("Par 1 cleared: " + par1Cleared);
                System.out.println("Par 2 cleared: " + par2Cleared);

                assertFalse(par1Cleared.contains("\u00B7"), "Par 1 must have no middle dots after deselect");
                assertFalse(par2Cleared.contains("\u00B7"), "Par 2 must have no middle dots after deselect");
                assertEquals(initialCode, editor.getText(), "Underlying editor text must never change");

                stage.close();
            } catch (Throwable t) {
                t.printStackTrace();
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS), "Timed out waiting for test to complete");
        if (error.get() != null) throw new AssertionError(error.get());
    }

    private static String getBoxRenderedText(Node box) {
        StringBuilder sb = new StringBuilder();
        if (box instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (child instanceof TextFlow flow) {
                    for (Node seg : flow.getChildren()) {
                        if (seg instanceof TextExt te) {
                            sb.append(te.getText());
                        }
                    }
                }
            }
        }
        return sb.toString();
    }
}
