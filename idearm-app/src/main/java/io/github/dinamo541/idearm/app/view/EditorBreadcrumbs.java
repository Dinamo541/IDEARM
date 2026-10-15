package io.github.dinamo541.idearm.app.view;

import io.github.dinamo541.idearm.app.ui.WorkbenchIcons;
import io.github.dinamo541.idearm.app.viewmodel.EditorDocumentViewModel;
import io.github.dinamo541.idearm.application.editor.OutlineItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

/**
 * The path above the code, as in VS Code: the folder, the file and then the segment and procedure the caret is in.
 *
 * <p>The symbols follow the caret and refresh a moment after typing stops. Clicking one moves the caret to its
 * declaration. Very large files keep the symbols of their last refresh instead of parsing on every pause.
 */
final class EditorBreadcrumbs extends HBox {
    /** Above this many characters the symbols refresh only when the file is opened again. */
    private static final int LIVE_LIMIT = 400_000;

    private final EditorDocumentViewModel doc;
    private final Function<String, List<OutlineItem>> outline;
    private final HBox path = new HBox();
    private final HBox symbols = new HBox();
    private final PauseTransition settle = new PauseTransition(Duration.millis(450));
    private List<OutlineItem> items = List.of();
    private List<OutlineItem> shown = List.of();

    EditorBreadcrumbs(EditorDocumentViewModel doc, Function<String, List<OutlineItem>> outline) {
        this.doc = doc;
        this.outline = outline;
        getStyleClass().add("editor-breadcrumbs");
        setAlignment(Pos.CENTER_LEFT);
        setMinWidth(0);
        path.setAlignment(Pos.CENTER_LEFT);
        symbols.setAlignment(Pos.CENTER_LEFT);
        path.setMinWidth(0);
        symbols.setMinWidth(0);
        getChildren().addAll(path, symbols);

        showPath();
        doc.filePathProperty().addListener(obs -> showPath());
        doc.getEditor().caretLineProperty().addListener(obs -> showSymbols());
        if (doc.getEditor() instanceof io.github.dinamo541.idearm.app.editor.RichTextFxEditorComponent editor) {
            editor.getCodeArea().textProperty().addListener(obs -> settle.playFromStart());
        }
        settle.setOnFinished(event -> refresh());
        refresh();
    }

    /** Parses the outline again, for instance when the provider becomes available. */
    void refresh() {
        String text = doc.getEditor().getText();
        if (text != null && (items.isEmpty() || text.length() <= LIVE_LIMIT)) {
            items = outline.apply(text);
        }
        showSymbols();
    }

    private void showPath() {
        var file = doc.getFilePath();
        path.getChildren().clear();
        if (file == null) return;
        if (file.getParent() != null && file.getParent().getFileName() != null) {
            path.getChildren().addAll(crumb(file.getParent().getFileName().toString(), null, "breadcrumb-folder"),
                    chevron());
        }
        String name = file.getFileName().toString();
        path.getChildren().add(crumb(name, ExplorerIcons.file(name), "breadcrumb-file"));
    }

    private void showSymbols() {
        var current = symbolsAt(items, doc.getEditor().caretLineProperty().get());
        if (current.equals(shown)) return;
        shown = current;
        symbols.getChildren().clear();
        for (OutlineItem symbol : current) {
            var crumb = crumb(symbol.name(), icon(symbol.kind()), "breadcrumb-" + symbol.kind());
            crumb.setCursor(Cursor.HAND);
            crumb.setOnMouseClicked(event -> {
                doc.getEditor().goToLine(symbol.line());
                doc.getEditor().requestFocus();
            });
            symbols.getChildren().addAll(chevron(), crumb);
        }
    }

    /**
     * The segment and the procedure that contain a line, outermost first. A simplified segment such as {@code .CODE}
     * runs until the next segment starts; a procedure ends at its ENDP.
     */
    static List<OutlineItem> symbolsAt(List<OutlineItem> items, int line) {
        OutlineItem segment = null;
        OutlineItem procedure = null;
        for (int i = 0; i < items.size(); i++) {
            OutlineItem item = items.get(i);
            if (item.line() > line) break;
            switch (item.kind()) {
                case "segment" -> {
                    int end = item.endLine() > item.line() ? item.endLine() : nextSegmentLine(items, i) - 1;
                    segment = line <= end ? item : null;
                }
                case "procedure" -> procedure = line <= item.endLine() ? item : procedure;
                default -> { }
            }
        }
        var path = new ArrayList<OutlineItem>(2);
        if (segment != null) path.add(segment);
        if (procedure != null) path.add(procedure);
        return List.copyOf(path);
    }

    private static int nextSegmentLine(List<OutlineItem> items, int index) {
        for (int i = index + 1; i < items.size(); i++) {
            if ("segment".equals(items.get(i).kind())) return items.get(i).line();
        }
        return Integer.MAX_VALUE;
    }

    private static Label crumb(String text, Node graphic, String style) {
        var label = new Label(text, graphic);
        label.getStyleClass().addAll("breadcrumb", style);
        label.setMinWidth(Label.USE_PREF_SIZE);
        return label;
    }

    private static Node chevron() {
        var icon = WorkbenchIcons.RIGHT.create(12);
        icon.getStyleClass().add("breadcrumb-separator");
        return icon;
    }

    private static Node icon(String kind) {
        return switch (kind) {
            case "segment" -> WorkbenchIcons.REGISTER_SEGMENT.create(13);
            case "procedure" -> cube();
            default -> WorkbenchIcons.REFERENCES.create(13);
        };
    }

    /** VS Code marks methods with a small cube; a procedure is the Assembly equivalent. */
    private static Node cube() {
        var outline = new javafx.scene.shape.SVGPath();
        outline.setContent("M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4"
                + "a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z M3.3 7l8.7 5 8.7-5 M12 22V12");
        outline.getStyleClass().add("breadcrumb-symbol-shape");
        outline.setScaleX(13 / 24.0);
        outline.setScaleY(13 / 24.0);
        var canvas = new javafx.scene.layout.StackPane(new javafx.scene.Group(outline));
        canvas.setMinSize(13, 13);
        canvas.setPrefSize(13, 13);
        canvas.setMaxSize(13, 13);
        return canvas;
    }
}
