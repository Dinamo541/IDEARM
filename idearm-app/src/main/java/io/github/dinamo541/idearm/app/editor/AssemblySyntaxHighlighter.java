package io.github.dinamo541.idearm.app.editor;

import io.github.dinamo541.idearm.language.catalog.InstructionCatalog;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import io.github.dinamo541.idearm.language.knowledge.Dialect;

/**
 * Fast regex-based Assembly lexer and syntax highlighter for RichTextFX.
 *
 * <p>Highlighting is designed to be computed <b>per paragraph</b> (ADR-005)
 * because x86 Assembly comments and string literals never span lines.
 * This guarantees typing latency far below NFR-03 (&lt; 50 ms).
 */
public final class AssemblySyntaxHighlighter {

    private static final String COMMON_DIRECTIVES = String.join("|",
            "DB", "DW", "DD", "DQ", "DT", "EQU", "ORG", "ALIGN", "EVEN", "GLOBAL", "EXTERN", "EXTRN", "INCLUDE", "END");

    private static final String MASM_TASM_DIRECTIVES = COMMON_DIRECTIVES + "|" + String.join("|",
            "PROC", "ENDP", "SEGMENT", "ENDS", "ASSUME", "GROUP", "OFFSET", "PTR", "BYTE", "WORD", "DWORD", "QWORD", "TBYTE",
            "NEAR", "FAR", "PUBLIC", "INCLUDELIB", "MACRO", "ENDM", "LOCAL", "LOCALS", "DUP", "SEG", "STRUC",
            "IDEAL", "P386", "P486", "P586", "QUIRKS", "OPTION", "INVOKE", "PROTO", "TITLE", "SUBTTL", "PAGE");

    private static final String NASM_DIRECTIVES = COMMON_DIRECTIVES + "|" + String.join("|",
            "SECTION", "DEFAULT", "REL", "ABS", "RESB", "RESW", "RESD", "RESQ", "REST", "RESO", "RESY", "RESZ",
            "TIMES", "COMMON", "CPU", "BITS", "USE16", "USE32", "USE64", "STRUC", "ENDSTRUC", "ISTROC", "IEND");

    private static final String NASM_PREPROCESSOR = "%(?:macro|endmacro|define|undef|include|if|elif|else|endif|rep|endrep)\\b";

    private static final String ALL_DIRECTIVES = MASM_TASM_DIRECTIVES + "|" + NASM_DIRECTIVES;

    /**
     * Taken from the instruction catalog, so a mnemonic is never coloured differently from how the linter judges it
     * (ADR-011). Longest first, which keeps the alternation deterministic no matter how the catalog is ordered.
     */
    private static final String INSTRUCTIONS = InstructionCatalog.knownMnemonics().stream()
            .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
            .collect(Collectors.joining("|"));

    private static final String REGISTERS = String.join("|",
            "AX", "BX", "CX", "DX", "AH", "AL", "BH", "BL", "CH", "CL", "DH", "DL", "SI", "DI", "SP", "BP", "CS",
            "DS", "ES", "SS", "IP", "EAX", "EBX", "ECX", "EDX", "ESI", "EDI", "ESP", "EBP", "FS", "GS",
            "RAX", "RBX", "RCX", "RDX", "RSI", "RDI", "RSP", "RBP", "RIP", "R8", "R9", "R10", "R11", "R12", "R13", "R14", "R15");

    /**
     * Strings come first, as in the lexer: {@code ';'} is a character, not a comment, and a string left open runs to
     * the end of the line, so {@code 'it;s} is not coloured as a comment from the {@code ;} on.
     */
    private static final String COMMON_PATTERN_PREFIX =
            "(?<STRING>'[^'\\n]*(?:'|$)|\"[^\"\\n]*(?:\"|$))"
                    + "|(?<COMMENT>;[^\\n]*)";

    private static final String COMMON_PATTERN_SUFFIX =
            "|(?<INSTRUCTION>\\b(?:" + INSTRUCTIONS + ")\\b)"
                    + "|(?<REGISTER>\\b(?:" + REGISTERS + ")\\b)"
                    + "|(?<LABEL>^[ \\t]*[a-zA-Z_@?$][a-zA-Z0-9_@?$]*(?=:))"
                    // The forms NumericLiteral reads, NASM prefixes included.
                    + "|(?<NUMBER>\\b(?:0[xXhH][0-9A-Fa-f]+|0[bB][01]+|0[oOqQ][0-7]+|0[dD]\\d+"
                    + "|\\d[0-9A-Fa-f]*[hH]|\\d+[dD]?|[01]+[bB]|[0-7]+[oOqQ])\\b)";

    private static final Pattern PATTERN_MASM_TASM = Pattern.compile(
            COMMON_PATTERN_PREFIX
                    + "|(?<DIRECTIVE>\\.(?:MODEL|STACK|DATA|CODE|EXIT|STARTUP|8086|186|286|386|486|586|686|8087|287|387)\\b|\\b(?:" + MASM_TASM_DIRECTIVES + ")\\b)"
                    + COMMON_PATTERN_SUFFIX,
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern PATTERN_NASM = Pattern.compile(
            COMMON_PATTERN_PREFIX
                    + "|(?<DIRECTIVE>\\b(?:" + NASM_DIRECTIVES + ")\\b|" + NASM_PREPROCESSOR + ")"
                    + COMMON_PATTERN_SUFFIX,
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern PATTERN_DEFAULT = Pattern.compile(
            COMMON_PATTERN_PREFIX
                    + "|(?<DIRECTIVE>\\.(?:MODEL|STACK|DATA|CODE|EXIT|STARTUP|8086|186|286|386|486|586|686|8087|287|387)\\b|\\b(?:" + ALL_DIRECTIVES + ")\\b|" + NASM_PREPROCESSOR + ")"
                    + COMMON_PATTERN_SUFFIX,
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private AssemblySyntaxHighlighter() {
    }

    /**
     * Computes the style spans for the specified text block with default dialect rules.
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        return computeHighlighting(text, null);
    }

    /**
     * Computes the style spans for the specified text block taking dialect into account.
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text, Dialect dialect) {
        Pattern pattern = PATTERN_DEFAULT;
        if (dialect == Dialect.MASM || dialect == Dialect.TASM) {
            pattern = PATTERN_MASM_TASM;
        } else if (dialect == Dialect.NASM) {
            pattern = PATTERN_NASM;
        }

        Matcher matcher = pattern.matcher(text);
        var builder = new StyleSpansBuilder<Collection<String>>();
        int last = 0;

        while (matcher.find()) {
            String styleClass;
            if (matcher.group("COMMENT") != null) {
                styleClass = "comment";
            } else if (matcher.group("STRING") != null) {
                styleClass = "string";
            } else if (matcher.group("DIRECTIVE") != null) {
                styleClass = "directive";
            } else if (matcher.group("INSTRUCTION") != null) {
                styleClass = "instruction";
            } else if (matcher.group("REGISTER") != null) {
                styleClass = "register";
            } else if (matcher.group("LABEL") != null) {
                styleClass = "label";
            } else if (matcher.group("NUMBER") != null) {
                styleClass = "number";
            } else {
                styleClass = null;
            }

            int start = matcher.start();
            int end = matcher.end();

            // Gap before matching token
            builder.add(Collections.emptyList(), start - last);
            // Matching token
            if (styleClass != null) {
                builder.add(List.of(styleClass), end - start);
            } else {
                builder.add(Collections.emptyList(), end - start);
            }
            last = end;
        }

        // Remaining unstyled tail
        builder.add(Collections.emptyList(), text.length() - last);
        return builder.create();
    }
}
