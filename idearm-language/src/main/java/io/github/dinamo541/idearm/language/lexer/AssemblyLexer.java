package io.github.dinamo541.idearm.language.lexer;

import java.util.*;
import java.util.regex.Pattern;

/**
 * High-speed tokenizer for x86 Assembly supporting Borland TASM and Microsoft MASM dialects.
 */
public final class AssemblyLexer {

    private static final Set<String> REGISTERS = Set.of(
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

    private static final Set<String> DIRECTIVES = Set.of(
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
            "USE16", "USE32", "USE64"
    );

    private static final Set<String> INSTRUCTIONS = Set.of(
            "MOV", "PUSH", "POP", "XCHG", "XLAT", "XLATB", "LEA", "LDS", "LES", "LAHF", "SAHF",
            "PUSHF", "POPF", "IN", "OUT",
            "ADD", "ADC", "SUB", "SBB", "INC", "DEC", "NEG", "CMP",
            "MUL", "IMUL", "DIV", "IDIV", "CBW", "CWD", "CWDE", "CDQ", "CQO", "CDQE",
            "AAA", "AAS", "AAM", "AAD", "DAA", "DAS",
            "AND", "OR", "XOR", "NOT", "TEST",
            "SHL", "SHR", "SAL", "SAR", "ROL", "ROR", "RCL", "RCR",
            "JMP", "CALL", "RET", "RETF", "RETN",
            "JE", "JZ", "JNE", "JNZ", "JA", "JNBE", "JAE", "JNB", "JNC",
            "JB", "JNAE", "JC", "JBE", "JNA", "JG", "JNLE", "JGE", "JNL",
            "JL", "JNGE", "JLE", "JNG", "JS", "JNS", "JO", "JNO", "JP", "JPE", "JNP", "JPO",
            "JCXZ", "JECXZ", "JRCXZ", "LOOP", "LOOPE", "LOOPZ", "LOOPNE", "LOOPNZ",
            "INT", "INTO", "IRET", "SYSCALL", "SYSRET",
            "MOVS", "MOVSB", "MOVSW", "CMPS", "CMPSB", "CMPSW",
            "SCAS", "SCASB", "SCASW", "LODS", "LODSB", "LODSW", "STOS", "STOSB", "STOSW",
            "REP", "REPE", "REPZ", "REPNE", "REPNZ",
            "CLC", "STC", "CMC", "CLD", "STD", "CLI", "STI", "HLT", "WAIT", "NOP", "LOCK",
            "ENTER", "LEAVE", "PUSHA", "POPA", "BOUND", "INS", "OUTS",
            "MOVZX", "MOVSX", "MOVABS", "BSF", "BSR", "BT", "BTC", "BTR", "BTS", "SETCC", "SHLD", "SHRD"
    );

    private static final Pattern HEX_PATTERN = Pattern.compile("^(?:0x[0-9a-fA-F]+|[0-9][0-9a-fA-F]*[hH])$");
    private static final Pattern BIN_PATTERN = Pattern.compile("^[01]+[bB]$");
    private static final Pattern OCT_PATTERN = Pattern.compile("^[0-7]+[oOqQ]$");
    private static final Pattern DEC_PATTERN = Pattern.compile("^[0-9]+[dD]?$");

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
            if (ch == '.') {
                i++;
                while (i < length && isIdentifierPart(line.charAt(i))) {
                    i++;
                }
                String word = line.substring(start, i);
                String upper = word.toUpperCase(Locale.ROOT);
                if (DIRECTIVES.contains(upper)) {
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
            } else if (REGISTERS.contains(upper)) {
                tokens.add(new Token(TokenType.REGISTER, word, lineNumber, startCol, word.length()));
            } else if (INSTRUCTIONS.contains(upper)) {
                tokens.add(new Token(TokenType.INSTRUCTION, word, lineNumber, startCol, word.length()));
            } else if (DIRECTIVES.contains(upper)) {
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
     * Whether a word is a numeric literal as MASM and TASM read it: it starts with a digit ({@code 0Ah}, {@code 21h},
     * {@code 1010b}, {@code 0x1F}), so registers such as {@code AH} and labels such as {@code each} are not numbers.
     */
    public static boolean isNumberLiteral(String text) {
        return text != null && isNumber(text.trim());
    }

    private static boolean isNumber(String text) {
        if (text.isEmpty()) return false;
        char first = text.charAt(0);
        if (Character.isDigit(first)) {
            return HEX_PATTERN.matcher(text).matches()
                    || BIN_PATTERN.matcher(text).matches()
                    || OCT_PATTERN.matcher(text).matches()
                    || DEC_PATTERN.matcher(text).matches();
        }
        return false;
    }
}
