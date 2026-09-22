package io.github.dinamo541.idearm.app.editor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Fast regex-based Assembly lexer and syntax highlighter for RichTextFX.
 *
 * <p>Highlighting is designed to be computed <b>per paragraph</b> (ADR-005)
 * because x86 Assembly comments and string literals never span lines.
 * This guarantees typing latency far below NFR-03 (&lt; 50 ms).
 */
public final class AssemblySyntaxHighlighter {

    private static final String DIRECTIVES = String.join("|",
            "PROC", "ENDP", "END", "SEGMENT", "ENDS", "ASSUME", "DB", "DW", "DD", "DQ", "DT", "EQU", "OFFSET", "PTR",
            "BYTE", "WORD", "DWORD", "NEAR", "FAR", "PUBLIC", "EXTRN", "INCLUDE", "MACRO", "ENDM", "LOCAL", "DUP",
            "SEG", "ORG");

    private static final String INSTRUCTIONS = String.join("|",
            "MOV", "PUSH", "POP", "XCHG", "LEA", "ADD", "ADC", "SUB", "SBB", "INC", "DEC", "NEG", "CMP", "MUL",
            "IMUL", "DIV", "IDIV", "AND", "OR", "XOR", "NOT", "TEST", "SHL", "SHR", "SAL", "SAR", "ROL", "ROR",
            "RCL", "RCR", "JMP", "JE", "JNE", "JZ", "JNZ", "JA", "JAE", "JB", "JBE", "JG", "JGE", "JL", "JLE", "JC",
            "JNC", "JCXZ", "LOOP", "LOOPE", "LOOPNE", "CALL", "RET", "RETF", "INT", "IRET", "CLC", "STC", "CLD",
            "STD", "CLI", "STI", "NOP", "HLT", "IN", "OUT", "LODSB", "LODSW", "STOSB", "STOSW", "MOVSB", "MOVSW",
            "REP", "REPE", "REPNE", "CBW", "CWD", "PUSHF", "POPF");

    private static final String REGISTERS = String.join("|",
            "AX", "BX", "CX", "DX", "AH", "AL", "BH", "BL", "CH", "CL", "DH", "DL", "SI", "DI", "SP", "BP", "CS",
            "DS", "ES", "SS", "IP", "EAX", "EBX", "ECX", "EDX", "ESI", "EDI", "ESP", "EBP", "FS", "GS");

    private static final Pattern PATTERN = Pattern.compile(
            "(?<COMMENT>;[^\\n]*)"
                    + "|(?<STRING>'[^'\\n]*'|\"[^\"\\n]*\")"
                    + "|(?<DIRECTIVE>\\.(?:MODEL|STACK|DATA|CODE|EXIT|STARTUP|8086|186|286|386)\\b|\\b(?:" + DIRECTIVES + ")\\b)"
                    + "|(?<INSTRUCTION>\\b(?:" + INSTRUCTIONS + ")\\b)"
                    + "|(?<REGISTER>\\b(?:" + REGISTERS + ")\\b)"
                    + "|(?<LABEL>^[ \\t]*[a-zA-Z_@?$][a-zA-Z0-9_@?$]*(?=:))"
                    + "|(?<NUMBER>\\b(?:0x[0-9A-Fa-f]+|\\d[0-9A-Fa-f]*[hH]|\\d+[dD]?|[01]+[bB]|[0-7]+[oOqQ])\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private AssemblySyntaxHighlighter() {
    }

    /**
     * Computes the style spans for the specified text block (single paragraph or entire file).
     *
     * @param text text to highlight
     * @return RichTextFX style spans
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
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
