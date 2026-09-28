package io.github.dinamo541.idearm.language.lexer;

import io.github.dinamo541.idearm.language.knowledge.Corpus;
import io.github.dinamo541.idearm.language.knowledge.Dialect;
import java.util.*;

/**
 * High-speed tokenizer for x86 Assembly supporting Borland TASM and Microsoft MASM dialects.
 */
public final class AssemblyLexer {

    private final Dialect dialect;

    public AssemblyLexer() {
        this(null);
    }

    public AssemblyLexer(Dialect dialect) {
        this.dialect = dialect;
    }

    public static final Set<String> REGISTERS = Set.of(
            "AX", "BX", "CX", "DX", "AH", "AL", "BH", "BL", "CH", "CL", "DH", "DL",
            "SI", "DI", "SP", "BP", "CS", "DS", "ES", "SS", "IP", "FLAGS",
            "EAX", "EBX", "ECX", "EDX", "ESI", "EDI", "ESP", "EBP", "FS", "GS", "EIP", "EFLAGS",
            "RAX", "RBX", "RCX", "RDX", "RSI", "RDI", "RSP", "RBP", "RIP", "RFLAGS",
            "R8", "R9", "R10", "R11", "R12", "R13", "R14", "R15",
            "R8D", "R9D", "R10D", "R11D", "R12D", "R13D", "R14D", "R15D",
            "R8W", "R9W", "R10W", "R11W", "R12W", "R13W", "R14W", "R15W",
            "R8B", "R9B", "R10B", "R11B", "R12B", "R13B", "R14B", "R15B",
            "SIL", "DIL", "BPL", "SPL"
    );

    public static final Set<String> COMMON_DIRECTIVES = Set.of(
            "DB", "DW", "DD", "DQ", "DT", "EQU", "ORG", "ALIGN", "EVEN",
            "GLOBAL", "EXTERN", "EXTRN", "INCLUDE", "END"
    );

    public static final Set<String> MASM_TASM_DIRECTIVES = Set.of(
            ".MODEL", ".STACK", ".DATA", ".CODE", ".STARTUP", ".EXIT",
            ".8086", ".186", ".286", ".386", ".486", ".586",
            "PROC", "ENDP", "SEGMENT", "ENDS", "ASSUME", "GROUP", "LABEL", "STRUC",
            "DB", "DW", "DD", "DQ", "DT", "EQU", "ORG", "ALIGN", "EVEN",
            "OFFSET", "PTR", "BYTE", "WORD", "DWORD", "QWORD", "TBYTE",
            "NEAR", "FAR", "PUBLIC", "EXTRN", "EXTERN", "GLOBAL",
            "INCLUDE", "INCLUDELIB", "MACRO", "ENDM", "LOCAL", "LOCALS",
            "DUP", "SEG", "END",
            "IDEAL", "P386", "P486", "P586", "QUIRKS", "OPTION", "INVOKE", "PROTO", "TITLE", "SUBTTL", "PAGE"
    );

    public static final Set<String> NASM_DIRECTIVES = Set.of(
            "SECTION", "SEGMENT", "DEFAULT", "REL", "ABS", "GLOBAL", "EXTERN", "COMMON", "CPU", "BITS",
            "USE16", "USE32", "USE64", "STRUC", "ENDSTRUC", "ISTROC", "IEND", "ALIGN", "EVEN",
            "DB", "DW", "DD", "DQ", "DT", "RESB", "RESW", "RESD", "RESQ", "REST", "RESO", "RESY", "RESZ",
            "EQU", "TIMES", "ORG", "END",
            "%MACRO", "%ENDMACRO", "%DEFINE", "%UNDEF", "%INCLUDE", "%IF", "%ELIF", "%ELSE", "%ENDIF",
            "%REP", "%ENDREP", "%STRLEN", "%SUBSTR"
    );

    public static final Set<String> DIRECTIVES = Set.of(
            ".MODEL", ".STACK", ".DATA", ".CODE", ".STARTUP", ".EXIT",
            ".8086", ".186", ".286", ".386", ".486", ".586",
            "PROC", "ENDP", "END", "SEGMENT", "ENDS", "ASSUME", "GROUP",
            "DB", "DW", "DD", "DQ", "DT", "EQU", "LABEL", "ORG",
            "OFFSET", "PTR", "BYTE", "WORD", "DWORD", "QWORD", "TBYTE",
            "NEAR", "FAR", "PUBLIC", "EXTRN", "EXTERN", "GLOBAL",
            "INCLUDE", "INCLUDELIB", "MACRO", "ENDM", "LOCAL", "LOCALS",
            "DUP", "SEG", "ALIGN", "EVEN", "IDEAL", "P386", "P486", "P586",
            "QUIRKS", "OPTION", "INVOKE", "PROTO", "TITLE", "SUBTTL", "PAGE",
            "DEFAULT", "REL", "ABS", "SECTION", "RESB", "RESW", "RESD", "RESQ",
            "REST", "RESO", "RESY", "RESZ", "TIMES", "COMMON", "CPU", "BITS",
            "USE16", "USE32", "USE64", "STRUC", "ENDSTRUC",
            "%MACRO", "%ENDMACRO", "%DEFINE", "%UNDEF", "%INCLUDE", "%IF", "%ELIF", "%ELSE", "%ENDIF",
            "%REP", "%ENDREP", "%STRLEN", "%SUBSTR"
    );

    /**
     * Checks if a mnemonic is recognized under the active dialect (ADR-011, ADR-013).
     */
    private boolean isInstruction(String upper) {
        return Corpus.get().analysisView().isRecognized(upper, dialect);
    }

    private boolean isDirective(String upper) {
        return isDirective(upper, dialect);
    }

    public static boolean isDirective(String word, Dialect dialect) {
        if (word == null || word.isBlank()) return false;
        String upper = word.trim().toUpperCase(Locale.ROOT);
        try {
            Set<String> set = Corpus.get().analysisView().recognizedDirectives(dialect);
            if (set != null && !set.isEmpty()) {
                return set.contains(upper);
            }
        } catch (Exception ignored) {
        }
        if (dialect == Dialect.NASM) {
            return NASM_DIRECTIVES.contains(upper);
        } else if (dialect == Dialect.MASM || dialect == Dialect.TASM) {
            return MASM_TASM_DIRECTIVES.contains(upper);
        }
        return DIRECTIVES.contains(upper);
    }

    public static boolean isRegister(String word) {
        if (word == null || word.isBlank()) return false;
        String upper = word.trim().toUpperCase(Locale.ROOT);
        if (REGISTERS.contains(upper)) return true;
        try {
            return Corpus.get().getAllRegisterNames().contains(upper);
        } catch (Exception ignored) {
            return false;
        }
    }

    public List<Token> tokenize(String source) {
        if (source == null || source.isEmpty()) {
            return List.of(new Token(TokenType.EOF, "", 1, 1, 0));
        }

        List<Token> tokens = new ArrayList<>();
        String[] lines = source.split("\\R", -1);

        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            int lineNumber = lineIdx + 1;
            String line = lines[lineIdx];
            tokenizeLine(line, lineNumber, tokens);
            tokens.add(new Token(TokenType.EOL, "\n", lineNumber, line.length() + 1, 1));
        }

        tokens.add(new Token(TokenType.EOF, "", lines.length, lines[lines.length - 1].length() + 1, 0));
        return List.copyOf(tokens);
    }

    public List<Token> tokenizeLine(String line, int lineNumber) {
        List<Token> tokens = new ArrayList<>();
        tokenizeLine(line, lineNumber, tokens);
        return List.copyOf(tokens);
    }

    private void tokenizeLine(String line, int lineNumber, List<Token> tokens) {
        int length = line.length();
        int i = 0;

        while (i < length) {
            char ch = line.charAt(i);

            // Whitespace
            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }

            int startCol = i + 1;

            // Comment
            if (ch == ';') {
                String commentText = line.substring(i);
                tokens.add(new Token(TokenType.COMMENT, commentText, lineNumber, startCol, commentText.length()));
                break;
            }

            // String literal
            if (ch == '\'' || ch == '"') {
                char quote = ch;
                int start = i;
                i++;
                while (i < length && line.charAt(i) != quote) {
                    i++;
                }
                if (i < length && line.charAt(i) == quote) {
                    i++; // include closing quote
                }
                String strText = line.substring(start, i);
                tokens.add(new Token(TokenType.STRING, strText, lineNumber, startCol, strText.length()));
                continue;
            }

            // Punctuation and single-char operators
            switch (ch) {
                case ',' -> { tokens.add(new Token(TokenType.COMMA, ",", lineNumber, startCol, 1)); i++; continue; }
                case ':' -> { tokens.add(new Token(TokenType.COLON, ":", lineNumber, startCol, 1)); i++; continue; }
                case '[' -> { tokens.add(new Token(TokenType.LBRACKET, "[", lineNumber, startCol, 1)); i++; continue; }
                case ']' -> { tokens.add(new Token(TokenType.RBRACKET, "]", lineNumber, startCol, 1)); i++; continue; }
                case '(' -> { tokens.add(new Token(TokenType.LPAREN, "(", lineNumber, startCol, 1)); i++; continue; }
                case ')' -> { tokens.add(new Token(TokenType.RPAREN, ")", lineNumber, startCol, 1)); i++; continue; }
                case '+' -> { tokens.add(new Token(TokenType.PLUS, "+", lineNumber, startCol, 1)); i++; continue; }
                case '-' -> { tokens.add(new Token(TokenType.MINUS, "-", lineNumber, startCol, 1)); i++; continue; }
                case '*' -> { tokens.add(new Token(TokenType.STAR, "*", lineNumber, startCol, 1)); i++; continue; }
                case '/' -> { tokens.add(new Token(TokenType.SLASH, "/", lineNumber, startCol, 1)); i++; continue; }
                case '=' -> { tokens.add(new Token(TokenType.EQUALS, "=", lineNumber, startCol, 1)); i++; continue; }
                case '?' -> { tokens.add(new Token(TokenType.QUESTION, "?", lineNumber, startCol, 1)); i++; continue; }
            }

            // Number or Identifier or Directive (starts with . or letter or @ or _)
            int start = i;
            if (ch == '%') {
                i++;
                while (i < length && isIdentifierPart(line.charAt(i))) {
                    i++;
                }
                String word = line.substring(start, i);
                String upper = word.toUpperCase(Locale.ROOT);
                if (isDirective(upper)) {
                    tokens.add(new Token(TokenType.DIRECTIVE, word, lineNumber, startCol, word.length()));
                } else {
                    tokens.add(new Token(TokenType.IDENTIFIER, word, lineNumber, startCol, word.length()));
                }
                continue;
            }

            if (ch == '.') {
                i++;
                while (i < length && isIdentifierPart(line.charAt(i))) {
                    i++;
                }
                String word = line.substring(start, i);
                String upper = word.toUpperCase(Locale.ROOT);
                if (isDirective(upper)) {
                    tokens.add(new Token(TokenType.DIRECTIVE, word, lineNumber, startCol, word.length()));
                } else {
                    tokens.add(new Token(TokenType.DOT, ".", lineNumber, startCol, 1));
                    if (word.length() > 1) {
                        tokens.add(new Token(TokenType.IDENTIFIER, word.substring(1), lineNumber, startCol + 1, word.length() - 1));
                    }
                }
                continue;
            }

            // Read alphanumeric or symbol word
            while (i < length && isWordChar(line.charAt(i))) {
                i++;
            }

            String word = line.substring(start, i);
            if (word.isEmpty()) {
                // Unknown character, skip ahead
                i++;
                continue;
            }

            String upper = word.toUpperCase(Locale.ROOT);

            // Determine token type
            if (isNumber(word)) {
                tokens.add(new Token(TokenType.NUMBER, word, lineNumber, startCol, word.length()));
            } else if (isRegister(upper)) {
                tokens.add(new Token(TokenType.REGISTER, word, lineNumber, startCol, word.length()));
            } else if (isInstruction(upper)) {
                tokens.add(new Token(TokenType.INSTRUCTION, word, lineNumber, startCol, word.length()));
            } else if (isDirective(upper)) {
                tokens.add(new Token(TokenType.DIRECTIVE, word, lineNumber, startCol, word.length()));
            } else {
                tokens.add(new Token(TokenType.IDENTIFIER, word, lineNumber, startCol, word.length()));
            }
        }
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '@' || c == '?' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '@' || c == '?' || c == '$';
    }

    /**
     * Whether a word is a numeric literal ({@code 0Ah}, {@code 21h}, {@code 1010b}, {@code 0x1F}, {@code 0b101}); it
     * starts with a digit, so registers such as {@code AH} and labels such as {@code each} are not numbers.
     */
    public static boolean isNumberLiteral(String text) {
        return NumericLiteral.isLiteral(text);
    }

    private static boolean isNumber(String text) {
        return NumericLiteral.isLiteral(text);
    }
}
