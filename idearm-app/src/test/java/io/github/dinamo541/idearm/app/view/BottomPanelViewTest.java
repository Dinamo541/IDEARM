package io.github.dinamo541.idearm.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BottomPanelViewTest {

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        FxDialogs.startJavaFx();
    }

    @Test
    void pasteReachesDosKeyboardOnceButLineModeWaitsForEnter() throws Exception {
        FxDialogs.onFxThread(() -> {
            var model = new io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel();
            var view = new BottomPanelView(model, new io.github.dinamo541.idearm.app.i18n.Localization());
            var output = (javafx.scene.layout.BorderPane) view.getTabs().get(2).getContent();
            var field = (javafx.scene.control.TextField) output.getBottom().lookup("#program-input");
            var stage = new javafx.stage.Stage();
            stage.setScene(new javafx.scene.Scene(view, 600, 400));
            stage.show();
            var received = new java.util.ArrayList<String>();
            var clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            var previous = new java.util.HashMap<javafx.scene.input.DataFormat, Object>();
            clipboard.getContentTypes().forEach(type -> {
                Object val = clipboard.getContent(type);
                if (val != null) previous.put(type, val);
            });
            try {
                model.setProgramInput(io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel.ProgramInput.KEYS,
                        received::add);
                var content = new javafx.scene.input.ClipboardContent();
                content.putString("Ana\r\nLuis\nFin\r");
                clipboard.setContent(content);
                if (!clipboard.hasString() || clipboard.getString() == null) {
                    view.setClipboardSupplier(() -> "Ana\r\nLuis\nFin\r");
                }
                field.paste();
                assertEquals(List.of("Ana\rLuis\rFin\r"), received);
                assertEquals("", field.getText(), "The program owns keyboard echo");
                assertEquals("", model.outputTextProperty().get());

                received.clear();
                model.setProgramInput(io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel.ProgramInput.LINE,
                        received::add);
                content.putString("Ana");
                clipboard.setContent(content);
                if (!clipboard.hasString() || clipboard.getString() == null) {
                    view.setClipboardSupplier(() -> "Ana");
                }
                field.paste();
                assertEquals("Ana", field.getText());
                assertTrue(received.isEmpty(), "Pipe input is buffered until Enter");
                field.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                        "", "", javafx.scene.input.KeyCode.ENTER, false, false, false, false));
                assertEquals(List.of("Ana\n"), received);
                assertEquals("", field.getText());
            } finally {
                view.setClipboardSupplier(null);
                stage.close();
                model.setProgramInput(io.github.dinamo541.idearm.app.viewmodel.BottomPanelViewModel.ProgramInput.NONE, null);
                if (!previous.isEmpty()) {
                    clipboard.setContent(previous);
                }
            }
            return null;
        });
    }

    @Test
    void extractEntityFromDiagnosticSingleArgument() {
        var d = new Diagnostic(Severity.WARNING, "lint.cpu-baseline-register64", "Register RAX is 64-bit",
                new Location("main.asm", 1, 1), "linter", "mov rax, 1", List.of("RAX", "8086"));
        assertEquals("RAX", BottomPanelView.extractEntityFromDiagnostic(d));
    }

    @Test
    void extractEntityFromDiagnosticCompositeArgument() {
        var d = new Diagnostic(Severity.ERROR, "lint.long-mode-invalid", "PUSH CS is invalid",
                new Location("main.asm", 1, 1), "linter", "push cs", List.of("PUSH CS"));
        assertEquals("PUSH", BottomPanelView.extractEntityFromDiagnostic(d));
    }

    @Test
    void extractEntityFromDiagnosticFallbackToRawLine() {
        var d = new Diagnostic(Severity.ERROR, "build.failed", "syntax error",
                new Location("main.asm", 1, 1), "nasm", "mov ax, 1");
        assertEquals("mov", BottomPanelView.extractEntityFromDiagnostic(d));
    }

    @Test
    void extractEntityFromDiagnosticNullOrBlank() {
        assertNull(BottomPanelView.extractEntityFromDiagnostic(null));
        var d = new Diagnostic(Severity.INFO, "info", "info", null, "tool", "");
        assertNull(BottomPanelView.extractEntityFromDiagnostic(d));
    }
}
