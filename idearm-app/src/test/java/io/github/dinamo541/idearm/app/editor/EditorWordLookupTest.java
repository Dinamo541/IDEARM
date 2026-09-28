package io.github.dinamo541.idearm.app.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The word under the pointer, looked up in its own line rather than in a copy of the whole document. */
class EditorWordLookupTest {

    @Test
    void findsTheWordAroundAColumn() {
        String line = "    mov ax, [bx+si]";
        assertEquals("mov", RichTextFxEditorComponent.wordIn(line, 5));
        assertEquals("mov", RichTextFxEditorComponent.wordIn(line, 7), "just after the word still names it");
        assertEquals("ax", RichTextFxEditorComponent.wordIn(line, 8));
        assertEquals("[", RichTextFxEditorComponent.wordIn(line, 12));
        assertEquals("", RichTextFxEditorComponent.wordIn("", 0));
        assertEquals("", RichTextFxEditorComponent.wordIn("mov", 9));
    }

    @Test
    void wordsInCommentsAndStringsAreProse() {
        String line = "    mov dx, OFFSET msg ; mov the address";
        assertFalse(RichTextFxEditorComponent.inCommentOrString(line, 5));
        assertTrue(RichTextFxEditorComponent.inCommentOrString(line, line.indexOf("the")));

        String data = "msg DB 'push ax; not code', '$'";
        assertTrue(RichTextFxEditorComponent.inCommentOrString(data, data.indexOf("push")));
        assertTrue(RichTextFxEditorComponent.inCommentOrString(data, data.indexOf("not")),
                "a semicolon inside a string does not start a comment, but the text is still in the string");
        assertFalse(RichTextFxEditorComponent.inCommentOrString(data, data.indexOf("DB")));
    }
}
