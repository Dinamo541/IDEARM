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

    @Test
    void coloursEveryInstructionTheCatalogKnows() {
        // These come from the catalog rather than a list of its own (ADR-011), so they used to render as plain text
        // while the linter considered them perfectly valid.
        for (String mnemonic : new String[] {"BSWAP", "MOVZX", "XADD", "CPUID"}) {
            StyleSpans<Collection<String>> spans = AssemblySyntaxHighlighter.computeHighlighting(mnemonic + " eax");

            assertTrue(spans.getStyleSpan(0).getStyle().contains("instruction"), mnemonic + " should be coloured");
            assertEquals(mnemonic.length(), spans.getStyleSpan(0).getLength());
        }
    }

    @Test
    void leavesAMistypedMnemonicUncoloured() {
        StyleSpans<Collection<String>> spans = AssemblySyntaxHighlighter.computeHighlighting("MUV ax, 1");

        assertTrue(spans.getStyleSpan(0).getStyle().isEmpty(), "a word no instruction is called is not an instruction");
    }

    @Test
    void dialectDiscriminationForSectionAndMacro() {
        // In dos-exe-16 (TASM/MASM), "section" should NOT be coloured as a directive
        StyleSpans<Collection<String>> dosSpans = AssemblySyntaxHighlighter.computeHighlighting(
                "section .text", io.github.dinamo541.idearm.language.knowledge.Dialect.TASM);
        assertTrue(dosSpans.getStyleSpan(0).getStyle().isEmpty(),
                "In TASM/MASM, 'section' is not a directive and should not be coloured as one");

        // In win-pe64-console (NASM), "section" MUST be coloured as a directive
        StyleSpans<Collection<String>> nasmSpans = AssemblySyntaxHighlighter.computeHighlighting(
                "section .text", io.github.dinamo541.idearm.language.knowledge.Dialect.NASM);
        assertTrue(nasmSpans.getStyleSpan(0).getStyle().contains("directive"),
                "In NASM, 'section' must be coloured as directive");

        // NASM preprocessor %macro
        StyleSpans<Collection<String>> macroSpans = AssemblySyntaxHighlighter.computeHighlighting(
                "%macro foo 1", io.github.dinamo541.idearm.language.knowledge.Dialect.NASM);
        assertTrue(macroSpans.getStyleSpan(0).getStyle().contains("directive"),
                "In NASM, '%macro' must be coloured as directive");
    }

    /** The highlighter checked comments first, so a semicolon inside a string started a comment. */
    @Test
    void aSemicolonInsideAStringIsNotAComment() {
        String line = "MOV AL, ';'";
        for (StyleSpan<Collection<String>> span : AssemblySyntaxHighlighter.computeHighlighting(line)) {
            assertTrue(!span.getStyle().contains("comment"), "no comment in " + line);
        }
    }

    @Test
    void anUnterminatedStringRunsToTheEndOfTheLineAsInTheLexer() {
        String line = "DB 'it;s";
        var spans = AssemblySyntaxHighlighter.computeHighlighting(line);
        var last = spans.getStyleSpan(spans.getSpanCount() - 1);
        assertTrue(last.getStyle().contains("string"), last.toString());
        assertEquals("'it;s".length(), last.getLength());
    }

    @Test
    void processorDirectivesOfLaterCpusAreDirectives() {
        for (String directive : new String[] {".486", ".586", ".686"}) {
            var span = AssemblySyntaxHighlighter.computeHighlighting(directive).getStyleSpan(0);
            assertTrue(span.getStyle().contains("directive"), directive);
        }
    }
}
