package io.github.dinamo541.idearm.app.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.dinamo541.idearm.application.editor.OutlineItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class EditorBreadcrumbsTest {

    /** The hello example: simplified segments run until the next one; the procedure ends at its ENDP. */
    private static final List<OutlineItem> HELLO = List.of(
            new OutlineItem("STACK", "segment", 4, 1, List.of()),
            new OutlineItem("DATA", "segment", 6, 1, List.of()),
            new OutlineItem("CODE", "segment", 9, 1, List.of()),
            new OutlineItem("main", "procedure", 10, 1, List.of(), 18));

    @Test
    void simplifiedSegmentsRunUntilTheNextSegment() {
        assertEquals(List.of(), names(2), "nothing contains the lines before the first segment");
        assertEquals(List.of("DATA"), names(7));
        assertEquals(List.of("CODE"), names(9));
    }

    @Test
    void aProcedureCoversItsLinesUpToEndp() {
        assertEquals(List.of("CODE", "main"), names(10));
        assertEquals(List.of("CODE", "main"), names(18));
        assertEquals(List.of("CODE"), names(19), "END main sits after ENDP, outside the procedure");
    }

    @Test
    void aFullSegmentEndsAtEnds() {
        var items = List.of(
                new OutlineItem("CSEG", "segment", 3, 1, List.of(), 20),
                new OutlineItem("start", "procedure", 5, 1, List.of(), 10));
        assertEquals(List.of("CSEG", "start"),
                EditorBreadcrumbs.symbolsAt(items, 7).stream().map(OutlineItem::name).toList());
        assertEquals(List.of("CSEG"),
                EditorBreadcrumbs.symbolsAt(items, 12).stream().map(OutlineItem::name).toList());
        assertEquals(List.of(), EditorBreadcrumbs.symbolsAt(items, 22));
    }

    private static List<String> names(int line) {
        return EditorBreadcrumbs.symbolsAt(HELLO, line).stream().map(OutlineItem::name).toList();
    }
}
