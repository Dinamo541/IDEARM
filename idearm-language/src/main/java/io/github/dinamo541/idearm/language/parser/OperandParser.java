package io.github.dinamo541.idearm.language.parser;

import io.github.dinamo541.idearm.language.lexer.AssemblyLexer;
import io.github.dinamo541.idearm.language.lexer.NumericLiteral;
import io.github.dinamo541.idearm.language.lexer.Token;
import io.github.dinamo541.idearm.language.lexer.TokenType;
import io.github.dinamo541.idearm.language.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Structured parser for instruction operands (AA-P4-01).
 * Deconstructs operand tokens into typed nodes (registers, immediates, memory expressions with
 * base/index/scale/displacement, symbols, or robust unknown fallbacks) with precise column ranges.
 */
public final class OperandParser {

    private OperandParser() {}

    /**
     * Parses all comma-separated operands starting from startIndex in a tokenized line.
     */
    public static List<ParsedOperand> parseOperands(List<Token> line, int startIndex) {
        List<ParsedOperand> result = new ArrayList<>();
        if (line == null || startIndex >= line.size()) {
            return result;
        }

        List<Token> currentOperandTokens = new ArrayList<>();
        for (int i = startIndex; i < line.size(); i++) {
            Token tok = line.get(i);
            if (tok.is(TokenType.COMMENT) || tok.is(TokenType.EOL) || tok.is(TokenType.EOF)) {
                break;
            }
            if (tok.is(TokenType.COMMA)) {
                if (!currentOperandTokens.isEmpty()) {
                    result.add(parseSingleOperand(currentOperandTokens));
                    currentOperandTokens.clear();
                }
            } else {
                currentOperandTokens.add(tok);
            }
        }

        if (!currentOperandTokens.isEmpty()) {
            result.add(parseSingleOperand(currentOperandTokens));
        }

        return result;
    }

    /**
     * Convenience method to parse a raw operand string into a structured operand.
     */
    public static ParsedOperand parse(String text, int line, int baseColumn) {
        if (text == null || text.isBlank()) {
            return new UnknownOperand("", line, baseColumn, baseColumn);
        }
        List<Token> tokens = new AssemblyLexer().tokenize(text.trim());
        List<Token> adjusted = new ArrayList<>();
        for (Token t : tokens) {
            if (t.is(TokenType.EOL) || t.is(TokenType.EOF)) continue;
            adjusted.add(new Token(t.type(), t.text(), line, baseColumn + (t.column() - 1), t.length()));
        }
        if (adjusted.isEmpty()) {
            return new UnknownOperand(text, line, baseColumn, baseColumn + text.length());
        }
        return parseSingleOperand(adjusted);
    }

    /**
     * Parses a single operand represented as a contiguous slice of tokens.
     */
    public static ParsedOperand parseSingleOperand(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return new UnknownOperand("", 1, 1, 1);
        }

        int line = tokens.getFirst().line();
        int startColumn = tokens.getFirst().column();
        int endColumn = tokens.getLast().column() + tokens.getLast().length();
        String rawText = buildRawText(tokens);

        try {
            int ptr = 0;
            String sizePrefix = null;
            OperandComponent sizeComponent = null;
            String segmentOverride = null;
            OperandComponent segmentComponent = null;

            // 1. Detect optional size prefix or segment override at start (in either order)
            boolean scannedPrefix = true;
            while (scannedPrefix && ptr < tokens.size()) {
                scannedPrefix = false;

                // Check size prefix: e.g. "BYTE PTR", "WORD PTR", "DWORD PTR", or NASM "byte", "word"
                if (sizePrefix == null) {
                    PrefixMatch sizeMatch = matchSizePrefix(tokens, ptr);
                    if (sizeMatch != null) {
                        sizePrefix = sizeMatch.prefix;
                        sizeComponent = sizeMatch.component;
                        ptr += sizeMatch.consumedTokens;
                        scannedPrefix = true;
                        continue;
                    }
                }

                // Check segment override: e.g. "ES:", "DS:", "SS:", "CS:", "FS:", "GS:"
                if (segmentOverride == null) {
                    PrefixMatch segMatch = matchSegmentOverride(tokens, ptr);
                    if (segMatch != null) {
                        segmentOverride = segMatch.prefix;
                        segmentComponent = segMatch.component;
                        ptr += segMatch.consumedTokens;
                        scannedPrefix = true;
                        continue;
                    }
                }
            }

            List<Token> remaining = tokens.subList(ptr, tokens.size());
            if (remaining.isEmpty()) {
                return new UnknownOperand(rawText, line, startColumn, endColumn);
            }

            // 2. Check for bracketed memory expression: e.g. [bx+si+4], [bp-2], [eax*4+tabla]
            int lbracketIdx = -1;
            int rbracketIdx = -1;
            for (int i = 0; i < remaining.size(); i++) {
                if (remaining.get(i).is(TokenType.LBRACKET)) {
                    lbracketIdx = i;
                } else if (remaining.get(i).is(TokenType.RBRACKET)) {
                    rbracketIdx = i;
                }
            }

            if (lbracketIdx != -1 && rbracketIdx != -1 && lbracketIdx < rbracketIdx) {
                // There may be a symbol before the bracket, e.g. tabla[si]
                String leadingSymbol = null;
                OperandComponent leadingSymbolComp = null;
                if (lbracketIdx > 0) {
                    Token symTok = remaining.getFirst();
                    if (symTok.is(TokenType.IDENTIFIER)) {
                        leadingSymbol = symTok.text();
                        leadingSymbolComp = new OperandComponent(leadingSymbol, symTok.line(), symTok.column(),
                                symTok.column() + symTok.length(), OperandComponent.Role.SYMBOL);
                    }
                }

                List<Token> innerTokens = remaining.subList(lbracketIdx + 1, rbracketIdx);
                Token ltok = remaining.get(lbracketIdx);
                Token rtok = remaining.get(rbracketIdx);

                MemoryExpression expr = parseMemoryExpression(innerTokens, ltok.column(), rtok.column() + rtok.length(),
                        leadingSymbol, leadingSymbolComp);

                return new MemoryOperand(rawText, line, startColumn, endColumn,
                        segmentOverride, segmentComponent, sizePrefix, sizeComponent, expr);
            }

            // 3. No brackets present.
            // If segment override or size prefix was present, it's direct memory addressing (e.g. ES:variable or word ptr var)
            if (segmentOverride != null || sizePrefix != null) {
                MemoryExpression expr = parseDirectMemoryExpression(remaining);
                return new MemoryOperand(rawText, line, startColumn, endColumn,
                        segmentOverride, segmentComponent, sizePrefix, sizeComponent, expr);
            }

            // 4. Single token operand
            if (remaining.size() == 1) {
                Token single = remaining.getFirst();
                if (single.is(TokenType.REGISTER) || isKnownRegister(single.text())) {
                    int bitSize = lookupRegisterBitSize(single.text());
                    return new RegisterOperand(rawText, line, startColumn, endColumn, single.normalized(), bitSize);
                }
                if (single.is(TokenType.NUMBER)) {
                    Long num = parseNumericLiteral(single.text());
                    int hint = inferBitSize(num);
                    return new ImmediateOperand(rawText, line, startColumn, endColumn, num, hint);
                }
                if (single.is(TokenType.STRING)) {
                    return new ImmediateOperand(rawText, line, startColumn, endColumn, null, 8);
                }
                if (single.is(TokenType.IDENTIFIER)) {
                    return new SymbolOperand(rawText, line, startColumn, endColumn, single.text());
                }
            }

            // 5. Unbracketed immediate expression or symbol expression (e.g. "OFFSET var" or "var + 4")
            if (isImmediateOrSymbolExpression(remaining)) {
                Token first = remaining.getFirst();
                if (first.text().equalsIgnoreCase("OFFSET") && remaining.size() > 1) {
                    Token sym = remaining.get(1);
                    return new SymbolOperand(rawText, line, startColumn, endColumn, sym.text());
                }
                Long computed = tryEvaluateConstantExpression(remaining);
                if (computed != null) {
                    return new ImmediateOperand(rawText, line, startColumn, endColumn, computed, inferBitSize(computed));
                }
                return new SymbolOperand(rawText, line, startColumn, endColumn, remaining.getFirst().text());
            }

        } catch (Exception ignored) {
            // Defensive: unrecognised operands must produce UnknownOperand without throwing
        }

        return new UnknownOperand(rawText, line, startColumn, endColumn);
    }

    private static MemoryExpression parseMemoryExpression(List<Token> inner, int bracketStartCol, int bracketEndCol,
                                                           String leadingSymbol, OperandComponent leadingSymbolComp) {
        StringBuilder rawInner = new StringBuilder();
        for (Token t : inner) {
            if (!rawInner.isEmpty()) rawInner.append(" ");
            rawInner.append(t.text());
        }

        List<OperandComponent> components = new ArrayList<>();
        OperandComponent base = null;
        OperandComponent index = null;
        int scale = 1;
        OperandComponent scaleComponent = null;
        OperandComponent displacement = leadingSymbolComp;
        Long displacementValue = null;
        String displacementSymbol = leadingSymbol;

        if (leadingSymbolComp != null) {
            components.add(leadingSymbolComp);
        }

        // Split inner tokens by + / - while tracking signs
        List<Term> terms = extractTerms(inner);
        for (Term term : terms) {
            if (term.isScaleMultiplication()) {
                // E.g. eax * 4 or 4 * eax
                index = term.registerComponent();
                scale = term.scaleValue();
                scaleComponent = term.scaleComponent();
                components.add(index);
                if (scaleComponent != null) components.add(scaleComponent);
            } else if (term.isRegister()) {
                OperandComponent regComp = term.registerComponent();
                components.add(regComp);
                String reg = regComp.text().toUpperCase(Locale.ROOT);

                // 16-bit register roles
                if (reg.equals("BX") || reg.equals("BP")) {
                    if (base == null) base = regComp;
                    else if (index == null) index = regComp; // two bases! Handled in validation
                } else if (reg.equals("SI") || reg.equals("DI")) {
                    if (index == null) index = regComp;
                    else if (base == null) base = regComp; // two indices! Handled in validation
                } else {
                    // 32/64-bit or other registers. The stack pointer cannot be a SIB index, so the assembler
                    // always encodes it as the base: [esp+4] and [eax+esp] are both valid.
                    if (base == null) {
                        base = regComp;
                    } else if (index == null && isStackPointer(reg) && !isStackPointer(base.text())) {
                        index = base;
                        base = regComp;
                    } else if (index == null) {
                        index = regComp;
                    }
                }
            } else if (term.isNumeric()) {
                long val = term.numericValue();
                if (displacementValue == null) displacementValue = val;
                else displacementValue += val;

                OperandComponent dispComp = term.toComponent(OperandComponent.Role.DISPLACEMENT);
                displacement = dispComp;
                components.add(dispComp);
            } else if (term.isIdentifier()) {
                displacementSymbol = term.rawText();
                OperandComponent symComp = term.toComponent(OperandComponent.Role.SYMBOL);
                displacement = symComp;
                components.add(symComp);
            } else {
                components.add(term.toComponent(OperandComponent.Role.UNKNOWN));
            }
        }

        return new MemoryExpression(
                rawInner.toString(),
                bracketStartCol,
                bracketEndCol,
                base,
                index,
                scale,
                scaleComponent,
                displacement,
                displacementValue,
                displacementSymbol,
                components
        );
    }

    private static MemoryExpression parseDirectMemoryExpression(List<Token> tokens) {
        StringBuilder raw = new StringBuilder();
        List<OperandComponent> components = new ArrayList<>();
        String symbol = null;
        OperandComponent disp = null;

        for (Token t : tokens) {
            if (!raw.isEmpty()) raw.append(" ");
            raw.append(t.text());
            if (t.is(TokenType.IDENTIFIER)) {
                symbol = t.text();
                disp = new OperandComponent(symbol, t.line(), t.column(), t.column() + t.length(), OperandComponent.Role.SYMBOL);
                components.add(disp);
            }
        }

        return new MemoryExpression(raw.toString(), -1, -1, null, null, 1, null, disp, null, symbol, components);
    }

    // --- Term extraction for inner memory expressions ---

    private record Term(List<Token> tokens, boolean isNegative) {
        boolean isScaleMultiplication() {
            return tokens.size() >= 3 && containsToken(TokenType.STAR);
        }

        boolean isRegister() {
            if (tokens.size() == 1) {
                Token t = tokens.getFirst();
                return t.is(TokenType.REGISTER) || isKnownRegister(t.text());
            }
            return false;
        }

        boolean isNumeric() {
            if (tokens.size() == 1) {
                return tokens.getFirst().is(TokenType.NUMBER) || parseNumericLiteral(tokens.getFirst().text()) != null;
            }
            return false;
        }

        boolean isIdentifier() {
            if (tokens.size() == 1) {
                return tokens.getFirst().is(TokenType.IDENTIFIER);
            }
            return false;
        }

        long numericValue() {
            Token t = tokens.getFirst();
            Long n = parseNumericLiteral(t.text());
            long val = n != null ? n : 0;
            return isNegative ? -val : val;
        }

        String rawText() {
            StringBuilder sb = new StringBuilder();
            if (isNegative) sb.append("-");
            for (Token t : tokens) sb.append(t.text());
            return sb.toString();
        }

        OperandComponent toComponent(OperandComponent.Role role) {
            Token first = tokens.getFirst();
            Token last = tokens.getLast();
            return new OperandComponent(rawText(), first.line(), first.column(), last.column() + last.length(), role);
        }

        OperandComponent registerComponent() {
            for (Token t : tokens) {
                if (t.is(TokenType.REGISTER) || isKnownRegister(t.text())) {
                    return new OperandComponent(t.normalized(), t.line(), t.column(), t.column() + t.length(),
                            OperandComponent.Role.REGISTER);
                }
            }
            return toComponent(OperandComponent.Role.REGISTER);
        }

        int scaleValue() {
            for (Token t : tokens) {
                if (t.is(TokenType.NUMBER)) {
                    Long n = parseNumericLiteral(t.text());
                    if (n != null) return n.intValue();
                }
            }
            return 1;
        }

        OperandComponent scaleComponent() {
            for (Token t : tokens) {
                if (t.is(TokenType.NUMBER)) {
                    return new OperandComponent(t.text(), t.line(), t.column(), t.column() + t.length(),
                            OperandComponent.Role.SCALE);
                }
            }
            return null;
        }

        private boolean containsToken(TokenType type) {
            for (Token t : tokens) {
                if (t.is(type)) return true;
            }
            return false;
        }
    }

    private static List<Term> extractTerms(List<Token> inner) {
        List<Term> terms = new ArrayList<>();
        List<Token> current = new ArrayList<>();
        boolean nextNegative = false;

        for (int i = 0; i < inner.size(); i++) {
            Token t = inner.get(i);
            if (t.is(TokenType.PLUS)) {
                if (!current.isEmpty()) {
                    terms.add(new Term(new ArrayList<>(current), nextNegative));
                    current.clear();
                }
                nextNegative = false;
            } else if (t.is(TokenType.MINUS)) {
                if (!current.isEmpty()) {
                    terms.add(new Term(new ArrayList<>(current), nextNegative));
                    current.clear();
                }
                nextNegative = true;
            } else {
                current.add(t);
            }
        }

        if (!current.isEmpty()) {
            terms.add(new Term(current, nextNegative));
        }

        return terms;
    }

    // --- Prefix Matching ---

    private record PrefixMatch(String prefix, OperandComponent component, int consumedTokens) {}

    private static PrefixMatch matchSizePrefix(List<Token> tokens, int start) {
        if (start >= tokens.size()) return null;
        Token first = tokens.get(start);
        String u1 = first.text().toUpperCase(Locale.ROOT);

        // Check two-token prefixes: "BYTE PTR", "WORD PTR", "DWORD PTR", "QWORD PTR", "TBYTE PTR", "FAR PTR", "NEAR PTR"
        if (start + 1 < tokens.size()) {
            Token second = tokens.get(start + 1);
            String u2 = second.text().toUpperCase(Locale.ROOT);
            if (u2.equals("PTR")) {
                if (u1.matches("^(BYTE|WORD|DWORD|QWORD|TBYTE|NEAR|FAR)$")) {
                    String text = u1 + " " + u2;
                    OperandComponent comp = new OperandComponent(text, first.line(), first.column(),
                            second.column() + second.length(), OperandComponent.Role.SIZE_PREFIX);
                    return new PrefixMatch(text, comp, 2);
                }
            }
        }

        // Check single-token NASM size keywords: byte, word, dword, qword, tword
        if (u1.matches("^(BYTE|WORD|DWORD|QWORD|TWORD|OWORD|YWORD|ZWORD|NEAR|FAR|SHORT)$")) {
            OperandComponent comp = new OperandComponent(u1, first.line(), first.column(),
                    first.column() + first.length(), OperandComponent.Role.SIZE_PREFIX);
            return new PrefixMatch(u1, comp, 1);
        }

        return null;
    }

    private static PrefixMatch matchSegmentOverride(List<Token> tokens, int start) {
        if (start + 1 >= tokens.size()) return null;
        Token first = tokens.get(start);
        Token second = tokens.get(start + 1);

        if (second.is(TokenType.COLON)) {
            String u = first.text().toUpperCase(Locale.ROOT);
            if (u.matches("^(CS|DS|SS|ES|FS|GS)$")) {
                String text = u + ":";
                OperandComponent comp = new OperandComponent(text, first.line(), first.column(),
                        second.column() + second.length(), OperandComponent.Role.SEGMENT_OVERRIDE);
                return new PrefixMatch(u, comp, 2);
            }
        }
        return null;
    }

    // --- Helpers ---

    private static String buildRawText(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (i > 0) {
                Token prev = tokens.get(i - 1);
                boolean noSpace = prev.is(TokenType.LBRACKET) || prev.is(TokenType.LPAREN) || prev.is(TokenType.COLON)
                        || t.is(TokenType.RBRACKET) || t.is(TokenType.RPAREN) || t.is(TokenType.COLON)
                        || t.is(TokenType.PLUS) || t.is(TokenType.MINUS) || t.is(TokenType.STAR)
                        || prev.is(TokenType.PLUS) || prev.is(TokenType.MINUS) || prev.is(TokenType.STAR);
                if (!noSpace) {
                    sb.append(" ");
                }
            }
            sb.append(t.text());
        }
        return sb.toString();
    }

    private static boolean isKnownRegister(String text) {
        if (text == null) return false;
        String u = text.toUpperCase(Locale.ROOT);
        return u.matches("^(A[HLX]|B[HLX]|C[HLX]|D[HLX]|S[IP]|D[IP]|BP|SP|CS|DS|SS|ES|FS|GS|IP|FLAGS|" +
                "E[ABCD]X|E[SD]I|E[BS]P|EIP|EFLAGS|R[ABCD]X|R[SD]I|R[BS]P|R[89]|R1[0-5]|RIP|RFLAGS|" +
                "R[89][BWD]|R1[0-5][BWD]|SIL|DIL|BPL|SPL|" +
                "ST\\([0-7]\\)|ST|MM[0-7]|XMM(?:[0-9]|[12][0-9]|3[01])|YMM(?:[0-9]|[12][0-9]|3[01])|ZMM(?:[0-9]|[12][0-9]|3[01]))$");
    }

    public static int lookupRegisterBitSize(String text) {
        if (text == null) return 0;
        String u = text.toUpperCase(Locale.ROOT);
        if (u.matches("^(A[HL]|B[HL]|C[HL]|D[HL]|SIL|DIL|BPL|SPL|R[89]B|R1[0-5]B)$")) return 8;
        if (u.matches("^(AX|BX|CX|DX|SI|DI|BP|SP|IP|FLAGS|CS|DS|SS|ES|FS|GS|R[89]W|R1[0-5]W)$")) return 16;
        if (u.matches("^(E[ABCD]X|E[SD]I|E[BS]P|EIP|EFLAGS|R[89]D|R1[0-5]D)$")) return 32;
        if (u.matches("^(R[ABCD]X|R[SD]I|R[BS]P|R[89]|R1[0-5]|RIP|RFLAGS|MM[0-7])$")) return 64;
        if (u.startsWith("ST")) return 80;
        if (u.startsWith("XMM")) return 128;
        if (u.startsWith("YMM")) return 256;
        if (u.startsWith("ZMM")) return 512;
        return 0;
    }

    public static Long parseNumericLiteral(String text) {
        if (text == null || text.isBlank()) return null;
        String s = text.trim();
        boolean neg = false;
        if (s.startsWith("+")) s = s.substring(1).trim();
        else if (s.startsWith("-")) {
            neg = true;
            s = s.substring(1).trim();
        }

        if ((s.startsWith("'") && s.endsWith("'") && s.length() == 3)
                || (s.startsWith("\"") && s.endsWith("\"") && s.length() == 3)) {
            long val = s.charAt(1);
            return neg ? -val : val;
        }
        Long val = NumericLiteral.parse(s);
        if (val == null) return null;
        return neg ? -val : val;
    }

    private static boolean isStackPointer(String register) {
        String upper = register.toUpperCase(Locale.ROOT);
        return upper.equals("ESP") || upper.equals("RSP");
    }

    private static int inferBitSize(Long num) {
        if (num == null) return 0;
        if (num >= -128 && num <= 255) return 8;
        if (num >= -32768 && num <= 65535) return 16;
        if (num >= Integer.MIN_VALUE && num <= 0xFFFFFFFFL) return 32;
        return 64;
    }

    private static boolean isImmediateOrSymbolExpression(List<Token> tokens) {
        for (Token t : tokens) {
            if (t.is(TokenType.NUMBER) || t.is(TokenType.IDENTIFIER) || t.is(TokenType.PLUS) || t.is(TokenType.MINUS)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static Long tryEvaluateConstantExpression(List<Token> tokens) {
        try {
            long sum = 0;
            boolean neg = false;
            for (Token t : tokens) {
                if (t.is(TokenType.PLUS)) {
                    neg = false;
                } else if (t.is(TokenType.MINUS)) {
                    neg = true;
                } else if (t.is(TokenType.NUMBER)) {
                    Long n = parseNumericLiteral(t.text());
                    if (n == null) return null;
                    sum += neg ? -n : n;
                } else {
                    return null; // Contains identifier or unknown symbol
                }
            }
            return sum;
        } catch (Exception e) {
            return null;
        }
    }
}
