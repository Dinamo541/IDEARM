package io.github.dinamo541.idearm.app.editor;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Lays diagnostic underlines over the syntax colours of one paragraph.
 *
 * <p>The two have to end up in a single span sequence because {@code setStyleSpans} overwrites a paragraph
 * wholesale: an underline applied on its own would be wiped by the next keystroke on that line. Kept apart from
 * the editor component so it can be tested without a JavaFX toolkit.
 */
final class DiagnosticMarks {

    static final String ERROR_STYLE = "idearm-diagnostic-error";
    static final String WARNING_STYLE = "idearm-diagnostic-warning";

    private DiagnosticMarks() {
    }

    /**
     * The syntax spans with the marks of this paragraph laid over them, or the syntax spans unchanged when there is
     * nothing to mark.
     *
     * @param syntax        spans covering exactly {@code paragraphText}
     * @param marks         the diagnostics reported on this paragraph, in any order
     * @param paragraphText the text the spans describe
     */
    static StyleSpans<Collection<String>> overlay(StyleSpans<Collection<String>> syntax, List<Diagnostic> marks,
                                                  String paragraphText) {
        if (paragraphText.isEmpty() || marks == null || marks.isEmpty()) {
            return syntax;
        }
        StyleSpans<Collection<String>> underlines = underlines(marks, paragraphText);
        return underlines == null ? syntax : syntax.overlay(underlines, DiagnosticMarks::merge);
    }

    /**
     * The underline layer alone, or null when nothing could be marked. Marks are laid left to right and one that
     * overlaps the previous is dropped, because RichTextFX spans are a flat sequence rather than a nesting.
     */
    private static StyleSpans<Collection<String>> underlines(List<Diagnostic> marks, String paragraphText) {
        List<Diagnostic> ordered = marks.stream()
                .filter(diagnostic -> diagnostic.location() != null && diagnostic.location().column() != null)
                .sorted(Comparator.comparingInt(diagnostic -> diagnostic.location().column()))
                .toList();

        var builder = new StyleSpansBuilder<Collection<String>>();
        int last = 0;
        boolean marked = false;
        for (Diagnostic diagnostic : ordered) {
            int start = diagnostic.location().column() - 1;
            if (start < last || start >= paragraphText.length()) {
                continue;
            }
            int end = Math.min(start + spanLength(diagnostic, paragraphText, start), paragraphText.length());
            if (end <= start) {
                continue;
            }
            builder.add(Collections.emptyList(), start - last);
            builder.add(List.of(styleClassFor(diagnostic.severity())), end - start);
            last = end;
            marked = true;
        }
        if (!marked) {
            return null;
        }
        builder.add(Collections.emptyList(), paragraphText.length() - last);
        return builder.create();
    }

    /**
     * How many characters to underline. The linter reports the span; an assembler never does, so its diagnostics
     * fall back to the word the column points at, and to a single character when that is not a word either.
     */
    private static int spanLength(Diagnostic diagnostic, String paragraphText, int start) {
        Integer reported = diagnostic.location().length();
        if (reported != null) {
            return reported;
        }
        int end = start;
        while (end < paragraphText.length() && isWordChar(paragraphText.charAt(end))) {
            end++;
        }
        return Math.max(1, end - start);
    }

    /** Whether this column falls inside the underline of that diagnostic. */
    static boolean covers(Diagnostic diagnostic, String paragraphText, int column) {
        if (diagnostic.location() == null || diagnostic.location().column() == null) {
            return false;
        }
        int start = diagnostic.location().column() - 1;
        return column >= start && column < start + spanLength(diagnostic, paragraphText, start);
    }

    private static String styleClassFor(Severity severity) {
        return severity == Severity.WARNING || severity == Severity.INFO ? WARNING_STYLE : ERROR_STYLE;
    }

    /** Keeps the token's own style class and adds the underline on top of it. */
    private static Collection<String> merge(Collection<String> syntax, Collection<String> mark) {
        if (mark.isEmpty()) {
            return syntax;
        }
        if (syntax.isEmpty()) {
            return mark;
        }
        List<String> merged = new ArrayList<>(syntax);
        merged.addAll(mark);
        return List.copyOf(merged);
    }

    private static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '@' || ch == '$' || ch == '?' || ch == '.';
    }
}
