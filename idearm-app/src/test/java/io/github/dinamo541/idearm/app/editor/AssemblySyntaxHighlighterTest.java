package io.github.dinamo541.idearm.app.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.Test;

class AssemblySyntaxHighlighterTest {

    @Test
    void highlightsInstructionsAndRegisters() {
        String line = "MOV AX, BX";
        StyleSpans<Collection<String>> spans = AssemblySyntaxHighlighter.computeHighlighting(line);

        assertTrue(spans.getSpanCount() >= 3);
        // First span is MOV
        StyleSpan<Collection<String>> span0 = spans.getStyleSpan(0);
        assertEquals(3, span0.getLength());
        assertTrue(span0.getStyle().contains("instruction"));
    }

    @Test
    void highlightsDirectives() {
        String line = ".MODEL small";
        StyleSpans<Collection<String>> spans = AssemblySyntaxHighlighter.computeHighlighting(line);

        StyleSpan<Collection<String>> span0 = spans.getStyleSpan(0);
        assertEquals(6, span0.getLength());
        assertTrue(span0.getStyle().contains("directive"));
    }

    @Test
    void highlightsComments() {
        String line = "; this is a test comment";
        StyleSpans<Collection<String>> spans = AssemblySyntaxHighlighter.computeHighlighting(line);

        assertEquals(1, spans.getSpanCount());
        StyleSpan<Collection<String>> span0 = spans.getStyleSpan(0);
        assertEquals(line.length(), span0.getLength());
        assertTrue(span0.getStyle().contains("comment"));
    }

    @Test
    void highlightsStrings() {
        String line = "DB 'Hello, World$'";
        StyleSpans<Collection<String>> spans = AssemblySyntaxHighlighter.computeHighlighting(line);

        boolean foundString = false;
        for (StyleSpan<Collection<String>> span : spans) {
            if (span.getStyle().contains("string")) {
                foundString = true;
                assertEquals(15, span.getLength());
            }
        }
        assertTrue(foundString, "Expected string style span");
    }

    @Test
    void highlightsHexAndDecNumbers() {
        String line = "INT 21h";
        StyleSpans<Collection<String>> spans = AssemblySyntaxHighlighter.computeHighlighting(line);

        boolean foundNumber = false;
        for (StyleSpan<Collection<String>> span : spans) {
            if (span.getStyle().contains("number")) {
                foundNumber = true;
                assertEquals(3, span.getLength());
            }
        }
        assertTrue(foundNumber, "Expected number style span");
    }

    @Test
    void handlesEmptyOrWhitespace() {
        StyleSpans<Collection<String>> spans = AssemblySyntaxHighlighter.computeHighlighting("   \t  ");
        assertEquals(1, spans.getSpanCount());
        assertTrue(spans.getStyleSpan(0).getStyle().isEmpty());
    }
}
