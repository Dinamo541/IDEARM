package io.github.dinamo541.idearm.app.editor;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import javafx.beans.InvalidationListener;
import javafx.scene.AccessibleRole;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import org.fxmisc.richtext.CodeArea;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A bounded, pulse-rendered overview of the styled document; navigation never changes the selection. */
public final class CodeMinimap extends Region {
    private static final double INSET = 8;
    private static final double COLUMN_WIDTH = 1.35;
    private final CodeArea area;
    private final Canvas code = new Canvas();
    private final Region viewport = new Region();
    private final Map<String, Rectangle> colors = new LinkedHashMap<>();
    private List<Diagnostic> diagnostics = List.of();
    private Set<Integer> breakpoints = Set.of();
    private Integer executionLine;
    private boolean contentDirty = true;
    private double dragOffset;

    public CodeMinimap(CodeArea area) {
        this.area = area;
        getStyleClass().add("code-minimap");
        setId("code-minimap");
        setMinWidth(112);
        setPrefWidth(112);
        setMaxWidth(112);
        setMinHeight(0);
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.SCROLL_BAR);
        code.setMouseTransparent(true);
        viewport.setMouseTransparent(true);
        viewport.setManaged(false);
        viewport.getStyleClass().add("minimap-viewport");
        getChildren().addAll(code, viewport);
        // CSS supplies the same token colours as the editor, including live theme changes.
        for (String kind : List.of("plain", "instruction", "register", "directive", "number", "string",
                "comment", "label", "error", "warning", "breakpoint", "execution", "caret")) {
            var swatch = new Rectangle(0, 0, Color.GRAY);
            swatch.setManaged(false);
            swatch.setMouseTransparent(true);
            swatch.getStyleClass().add("minimap-" + kind);
            swatch.fillProperty().addListener(obs -> invalidateContent());
            colors.put(kind, swatch);
            getChildren().add(swatch);
        }
        area.richChanges().subscribe(change -> invalidateContent());
        area.estimatedScrollYProperty().addListener(obs -> requestLayout());
        area.getVisibleParagraphs().addListener((InvalidationListener) obs -> requestLayout());
        area.currentParagraphProperty().addListener(obs -> invalidateContent());
        visibleProperty().addListener(obs -> invalidateContent());
        sceneProperty().addListener(obs -> invalidateContent());
        setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            double y = event.getY();
            dragOffset = y >= viewport.getLayoutY() && y <= viewport.getLayoutY() + viewport.getHeight()
                    ? y - viewport.getLayoutY() : viewport.getHeight() / 2;
            navigate(y);
            area.requestFocus();
            event.consume();
        });
        setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown()) { navigate(event.getY()); event.consume(); }
        });
        setOnScroll(event -> { area.scrollYBy(-event.getDeltaY()); event.consume(); });
        setOnKeyPressed(event -> {
            int first = firstVisible();
            int page = Math.max(1, area.getVisibleParagraphs().size() - 1);
            int target = switch (event.getCode()) {
                case UP -> first - 1;
                case DOWN -> first + 1;
                case PAGE_UP -> first - page;
                case PAGE_DOWN -> first + page;
                case HOME -> 0;
                case END -> area.getParagraphs().size() - 1;
                default -> -1;
            };
            if (target != -1 || event.getCode() == javafx.scene.input.KeyCode.UP
                    || event.getCode() == javafx.scene.input.KeyCode.PAGE_UP) {
                area.showParagraphAtTop(Math.clamp(target, 0, area.getParagraphs().size() - 1));
                event.consume();
            }
        });
    }

    public void setLocalization(Localization localization) {
        accessibleTextProperty().bind(localization.text("editor.minimap"));
        accessibleHelpProperty().bind(localization.text("editor.minimap.help"));
    }

    public void setDiagnostics(List<Diagnostic> marks) {
        diagnostics = marks == null ? List.of() : List.copyOf(marks);
        invalidateContent();
    }

    public void setDebugMarks(Set<Integer> lines, Integer execution) {
        breakpoints = Set.copyOf(lines);
        executionLine = execution;
        invalidateContent();
    }

    private void invalidateContent() { contentDirty = true; requestLayout(); }

    private int firstVisible() {
        return area.getVisibleParagraphs().isEmpty() ? 0 : area.visibleParToAllParIndex(0);
    }

    private double lineHeight() {
        return Math.min(3, Math.max(1, getHeight() - 2 * INSET) / area.getParagraphs().size());
    }

    private void navigate(double y) {
        int count = area.getParagraphs().size();
        int visible = Math.max(1, area.getVisibleParagraphs().size());
        double track = Math.max(0, count * lineHeight() - viewport.getHeight());
        double fraction = track == 0 ? 0 : Math.clamp((y - INSET - dragOffset) / track, 0, 1);
        area.showParagraphAtTop((int) Math.round(fraction * Math.max(0, count - visible)));
        requestLayout();
    }

    @Override protected void layoutChildren() {
        if (!isVisible() || getWidth() <= 0 || getHeight() <= 0) return;
        if (code.getWidth() != getWidth() || code.getHeight() != getHeight()) {
            code.setWidth(getWidth()); code.setHeight(getHeight()); contentDirty = true;
        }
        int count = area.getParagraphs().size();
        int visible = Math.max(1, area.getVisibleParagraphs().size());
        double scale = lineHeight();
        double contentHeight = count * scale;
        double thumbHeight = Math.min(contentHeight, Math.max(14, visible * scale));
        double progress = count <= visible ? 0 : Math.clamp((double) firstVisible() / (count - visible), 0, 1);
        viewport.resizeRelocate(2, INSET + progress * (contentHeight - thumbHeight), getWidth() - 9, thumbHeight);
        if (contentDirty) { draw(scale, count); contentDirty = false; }
    }

    private Paint color(String kind) { return colors.getOrDefault(kind, colors.get("plain")).getFill(); }

    private void draw(double scale, int count) {
        var gc = code.getGraphicsContext2D();
        gc.clearRect(0, 0, code.getWidth(), code.getHeight());
        // At most one sampled source line per screen pixel and only the columns that fit the map.
        // Reuse RichTextFX's styled segments: no duplicate lexer, document copy, or second editor.
        double rowStep = Math.max(1, scale);
        for (double y = 0; y < count * scale; y += rowStep) {
            int line = Math.min(count - 1, (int) (y / scale));
            double x = INSET;
            outer: for (var segment : area.getParagraph(line).getStyledSegments()) {
                String kind = "plain";
                for (String style : segment.getStyle()) if (colors.containsKey(style)) { kind = style; break; }
                gc.setFill(color(kind));
                String text = segment.getSegment();
                for (int i = 0; i < text.length(); i++) {
                    char ch = text.charAt(i);
                    if (x >= getWidth() - 13) break outer;
                    if (!Character.isWhitespace(ch)) gc.fillRect(x, INSET + y, 1.15, Math.min(1.6, rowStep));
                    x += ch == '\t' ? 4 * COLUMN_WIDTH : COLUMN_WIDTH;
                }
            }
        }
        mark(area.getCurrentParagraph() + 1, scale, count, "caret", 2);
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.location() != null && diagnostic.location().line() != null) {
                mark(diagnostic.location().line(), scale, count,
                        diagnostic.severity() == io.github.dinamo541.idearm.domain.diagnostic.Severity.ERROR
                                ? "error" : "warning", 3);
            }
        }
        for (int line : breakpoints) mark(line, scale, count, "breakpoint", 4);
        if (executionLine != null) mark(executionLine, scale, count, "execution", 4);
    }

    private void mark(int line, double scale, int count, String kind, double height) {
        if (line < 1 || line > count) return;
        var gc = code.getGraphicsContext2D();
        gc.setFill(color(kind));
        gc.fillRect(getWidth() - 6, INSET + (line - 1) * scale, 4, height);
    }
}
