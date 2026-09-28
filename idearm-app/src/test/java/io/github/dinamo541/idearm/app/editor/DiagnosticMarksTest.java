package io.github.dinamo541.idearm.app.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import java.util.Collection;
import java.util.List;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.Test;

class DiagnosticMarksTest {

    private static Diagnostic warning(int column, Integer length) {
        return new Diagnostic(Severity.WARNING, "lint.unknown-instruction", "No instruction is called MUV.",
                new Location("main.asm", 1, column, length), "idearm-linter", "MUV", List.of("MUV"));
    }

    private static StyleSpans<Collection<String>> overlay(String line, List<Diagnostic> marks) {
        return DiagnosticMarks.overlay(AssemblySyntaxHighlighter.computeHighlighting(line), marks, line);
    }

    /** The style classes of every character of the line, so a span layout can be asserted position by position. */
    private static List<Collection<String>> perCharacter(StyleSpans<Collection<String>> spans, int length) {
        List<Collection<String>> styles = new java.util.ArrayList<>();
        int position = 0;
        for (var span : spans) {
            for (int i = 0; i < span.getLength() && position < length; i++, position++) {
                styles.add(span.getStyle());
            }
        }
        return styles;
    }

    @Test
    void underlinesExactlyTheReportedSpan() {
        String line = "  MUV bx, 1";
        var styles = perCharacter(overlay(line, List.of(warning(3, 3))), line.length());

        assertFalse(styles.get(1).contains(DiagnosticMarks.WARNING_STYLE), "the space before the word is untouched");
        for (int column = 2; column <= 4; column++) {
            assertTrue(styles.get(column).contains(DiagnosticMarks.WARNING_STYLE), "column " + column);
        }
        assertFalse(styles.get(5).contains(DiagnosticMarks.WARNING_STYLE), "the space after the word is untouched");
    }

    @Test
    void keepsTheSyntaxColourUnderTheUnderline() {
        // A valid instruction can still carry a warning, and it must stay coloured as an instruction.
        String line = "  mov ax, 300";
        var styles = perCharacter(overlay(line, List.of(warning(3, 3))), line.length());

        assertTrue(styles.get(2).contains("instruction"));
        assertTrue(styles.get(2).contains(DiagnosticMarks.WARNING_STYLE));
    }

    @Test
    void underlinesTheWordWhenNoSpanWasReported() {
        // What an assembler gives: a column and nothing more.
        String line = "  undefined_symbol, 1";
        var styles = perCharacter(overlay(line, List.of(warning(3, null))), line.length());

        assertTrue(styles.get(2).contains(DiagnosticMarks.WARNING_STYLE));
        assertTrue(styles.get(17).contains(DiagnosticMarks.WARNING_STYLE), "the whole word is covered");
        assertFalse(styles.get(18).contains(DiagnosticMarks.WARNING_STYLE), "the comma is not part of the word");
    }

    @Test
    void marksAnErrorApartFromAWarning() {
        String line = "  MUV bx, 1";
        Diagnostic error = new Diagnostic(Severity.ERROR, "nasm.error", "invalid combination",
                new Location("main.asm", 1, 3, 3), "nasm", "raw", List.of());
        var styles = perCharacter(overlay(line, List.of(error)), line.length());

        assertTrue(styles.get(2).contains(DiagnosticMarks.ERROR_STYLE));
    }

    @Test
    void marksTwoWordsOfTheSameLine() {
        String line = "MUV bx, ADDD";
        var styles = perCharacter(overlay(line, List.of(warning(9, 4), warning(1, 3))), line.length());

        assertTrue(styles.get(0).contains(DiagnosticMarks.WARNING_STYLE), "reported out of order, laid in order");
        assertFalse(styles.get(4).contains(DiagnosticMarks.WARNING_STYLE));
        assertTrue(styles.get(11).contains(DiagnosticMarks.WARNING_STYLE));
    }

    @Test
    void ignoresAMarkThatCannotBeDrawn() {
        String line = "  mov ax, bx";
        var syntax = AssemblySyntaxHighlighter.computeHighlighting(line);

        assertSame(syntax, DiagnosticMarks.overlay(syntax, List.of(), line), "nothing to mark");
        assertSame(syntax, DiagnosticMarks.overlay(syntax, null, line));
        assertSame(syntax, DiagnosticMarks.overlay(syntax, List.of(warning(500, 3)), line), "past the end of the line");
        assertSame(syntax, DiagnosticMarks.overlay(syntax, List.of(
                new Diagnostic(Severity.WARNING, "lint.missing-exit", "no exit",
                        new Location("main.asm", 1, null), "idearm-linter", "raw", List.of())), line),
                "a diagnostic without a column has nothing to underline");
    }

    @Test
    void doesNotChangeTheLengthOfTheLine() {
        String line = "  MUV bx, 1   ; comentario";
        var spans = overlay(line, List.of(warning(3, 3)));

        int total = 0;
        for (var span : spans) {
            total += span.getLength();
        }
        assertEquals(line.length(), total, "RichTextFX rejects spans that do not cover the paragraph exactly");
    }

    @Test
    void knowsWhetherTheMouseIsOverAMark() {
        String line = "  MUV bx, 1";
        Diagnostic mark = warning(3, 3);

        assertFalse(DiagnosticMarks.covers(mark, line, 1));
        assertTrue(DiagnosticMarks.covers(mark, line, 2));
        assertTrue(DiagnosticMarks.covers(mark, line, 4));
        assertFalse(DiagnosticMarks.covers(mark, line, 5));
    }
}
