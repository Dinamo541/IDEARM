package io.github.dinamo541.idearm.app.editor;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LineEditingTest {
    private String apply(String text, LineEditing.Edit edit) {
        return edit == null ? text : text.substring(0, edit.start()) + edit.text() + text.substring(edit.end());
    }
    @Test void movesLineAndPreservesCaretColumn() {
        var edit = LineEditing.move("one\ntwo\nthree", 5, 5, true);
        assertEquals("one\nthree\ntwo", apply("one\ntwo\nthree", edit));
        assertEquals(11, edit.caret());
        assertEquals("two\none\nthree", apply("one\ntwo\nthree", LineEditing.move("one\ntwo\nthree", 5, 5, false)));
    }
    @Test void selectedBlockExcludesNextLineWhenSelectionEndsAtColumnZero() {
        String text = "a\nb\nc\nd";
        assertEquals(new LineEditing.Lines(2, 5), LineEditing.lines(text, 2, 6));
        var edit = LineEditing.move(text, 6, 2, false);
        assertEquals("b\nc\na\nd", apply(text, edit));
        assertTrue(edit.anchor() > edit.caret(), "Reverse selection must remain reversed");
    }
    @Test void movesAtBoundariesAreNoOpsAndBlankLinesArePreserved() {
        assertNull(LineEditing.move("first\nlast", 0, 0, false));
        assertNull(LineEditing.move("first\nlast", 8, 8, true));
        assertEquals("\nx", apply("x\n", LineEditing.move("x\n", 0, 0, true)));
    }
    @Test void duplicateWorksOnLastLineWithoutFinalNewline() {
        var edit = LineEditing.duplicate("a\nlast", 4, 4, true);
        assertEquals("a\nlast\nlast", apply("a\nlast", edit));
        assertEquals(9, edit.caret());
    }
    @Test void deleteLastLineDoesNotLeavePhantomEmptyLine() {
        assertEquals("a", apply("a\nb", LineEditing.delete("a\nb", 2, 2)));
        assertEquals("", apply("a", LineEditing.delete("a", 0, 0)));
        assertEquals("b", apply("a\nb", LineEditing.delete("a\nb", 0, 0)));
    }
    @Test void insertCopiesIndentation() {
        assertEquals("    mov ax, bx\n    ", apply("    mov ax, bx", LineEditing.insert("    mov ax, bx", 7, false)));
        assertEquals("    \n    mov ax, bx", apply("    mov ax, bx", LineEditing.insert("    mov ax, bx", 7, true)));
    }
    @Test void assemblyCommentRoundTripKeepsIndentationAndBlankLines() {
        String source = "  mov ax, 1\n\n  int 21h";
        var comment = LineEditing.transform(source, 0, source.length(), "comment");
        String commented = apply(source, comment);
        assertEquals("  ; mov ax, 1\n\n  ; int 21h", commented);
        assertEquals(source, apply(commented, LineEditing.transform(commented, 0, commented.length(), "comment")));
    }
    @Test void allShortcutDefinitionsParseAndAreUnique() {
        assertEquals(EditorCommand.values().length, java.util.Arrays.stream(EditorCommand.values())
                .map(EditorCommand::shortcut).distinct().count());
    }
}
