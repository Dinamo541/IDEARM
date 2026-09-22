package io.github.dinamo541.idearm.application.editor;

import io.github.dinamo541.idearm.language.catalog.InstructionCatalog;
import io.github.dinamo541.idearm.language.catalog.InstructionInfo;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import io.github.dinamo541.idearm.language.index.SymbolDefinition;
import io.github.dinamo541.idearm.language.index.SymbolKind;
import io.github.dinamo541.idearm.language.lexer.AssemblyLexer;

import java.util.Locale;
import java.util.Optional;

/**
 * Use case: Resolves rich educational hover content (instructions, number conversions, symbol definitions).
 *
 * <p>Instruction texts come from the bilingual instruction catalog. Numbers and symbols are described in English
 * and also carry their facts, which the user interface writes in its own language (ADR-006).
 */
public final class QueryHover {

    @FunctionalInterface
    public interface VariableEvaluator {
        Optional<Long> evaluate(String filePath, int line, int byteSize);
    }

    public Optional<HoverInfo> execute(String word, String localeLanguage, ProjectSymbolIndex index) {
        return execute(word, localeLanguage, index, null);
    }

    public Optional<HoverInfo> execute(String word, String localeLanguage, ProjectSymbolIndex index, VariableEvaluator evaluator) {
        if (word == null || word.isBlank()) {
            return Optional.empty();
        }

        String trimmed = word.trim();
        boolean isSpanish = localeLanguage != null && localeLanguage.toLowerCase(Locale.ROOT).startsWith("es");

        // 1. A numeric literal starts with a digit, as the assembler reads it: 0Ah is a number, AH a register.
        Optional<HoverInfo> numHover = tryNumericConversion(trimmed);
        if (numHover.isPresent()) {
            return numHover;
        }

        // 2. Try instruction catalog
        Optional<InstructionInfo> instOpt = InstructionCatalog.find(trimmed);
        if (instOpt.isPresent()) {
            InstructionInfo inst = instOpt.get();
            String title = inst.mnemonic() + " — " + (isSpanish ? inst.summaryEs() : inst.summaryEn());
            String syntax = String.join("\n", inst.syntaxVariants());
            String desc = isSpanish ? inst.descriptionEs() : inst.descriptionEn();
            String flags = inst.flags().formatTable();
            String example = inst.example();

            return Optional.of(new HoverInfo(title, syntax, desc, flags, example, HoverKind.INSTRUCTION));
        }

        // 3. Try project symbol index
        if (index != null) {
            Optional<SymbolDefinition> symOpt = index.findDefinition(trimmed);
            if (symOpt.isPresent()) {
                return Optional.of(symbolHover(symOpt.get(), evaluator));
            }
        }

        return Optional.empty();
    }

    private static HoverInfo symbolHover(SymbolDefinition sym, VariableEvaluator evaluator) {
        Long live = null;
        if (evaluator != null && sym.kind() == SymbolKind.VARIABLE) {
            int size = isByteData(sym.signature()) ? 1 : 2;
            live = evaluator.evaluate(sym.filePath(), sym.line(), size).orElse(null);
        }
        var description = new StringBuilder("Defined in ").append(sym.filePath()).append(':').append(sym.line());
        if (live != null) {
            description.append("\n\nLive memory value: 0x").append(Long.toHexString(live).toUpperCase(Locale.ROOT))
                    .append(" (").append(live).append(')');
        }
        String syntax = sym.signature() != null ? sym.signature() : sym.name();
        return new HoverInfo(sym.kind().name() + " " + sym.name(), syntax, description.toString(), null, null,
                HoverKind.SYMBOL_INFO, null,
                new HoverInfo.SymbolFacts(sym.name(), sym.kind().name(), sym.filePath(), sym.line(), live));
    }

    /** A variable defined with DB holds bytes; the signature reads "name DB ...". */
    private static boolean isByteData(String signature) {
        if (signature == null) {
            return false;
        }
        String[] words = signature.trim().split("\\s+");
        return words.length >= 2 && words[1].equalsIgnoreCase("DB");
    }

    private static Optional<HoverInfo> tryNumericConversion(String text) {
        Long val = parseNumber(text);
        if (val == null) {
            return Optional.empty();
        }

        long n = val;
        String hex = "0x" + Long.toHexString(n).toUpperCase(Locale.ROOT);
        String dec = Long.toString(n);
        String bin = formatBinary(n);

        StringBuilder desc = new StringBuilder();
        desc.append("Base Conversion:\n");
        desc.append("  Decimal:     ").append(dec).append("\n");
        desc.append("  Hexadecimal: ").append(hex).append("\n");
        desc.append("  Binary:      ").append(bin);

        if (n >= 32 && n <= 126) {
            desc.append("\n  ASCII:       '").append((char) n).append("'");
        }

        return Optional.of(new HoverInfo("Numeric Literal: " + text, hex + " = " + dec, desc.toString(), null, null,
                HoverKind.NUMBER_CONVERSION, n, null));
    }

    /** The value of a numeric literal as MASM and TASM read it, or {@code null} when the word is not one. */
    static Long parseNumber(String s) {
        String clean = s.trim();
        if (!AssemblyLexer.isNumberLiteral(clean)) {
            return null;
        }
        try {
            if (clean.startsWith("0x") || clean.startsWith("0X")) {
                return Long.parseLong(clean.substring(2), 16);
            }
            if (clean.endsWith("h") || clean.endsWith("H")) {
                return Long.parseLong(clean.substring(0, clean.length() - 1), 16);
            }
            if (clean.endsWith("b") || clean.endsWith("B")) {
                return Long.parseLong(clean.substring(0, clean.length() - 1), 2);
            }
            if (clean.endsWith("d") || clean.endsWith("D")) {
                return Long.parseLong(clean.substring(0, clean.length() - 1), 10);
            }
            if (clean.endsWith("o") || clean.endsWith("O") || clean.endsWith("q") || clean.endsWith("Q")) {
                return Long.parseLong(clean.substring(0, clean.length() - 1), 8);
            }
            return Long.parseLong(clean, 10);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Binary digits in groups of four, with the assembler's {@code b} suffix: {@code 100 1100b}. */
    public static String formatBinary(long n) {
        String raw = Long.toBinaryString(n);
        StringBuilder sb = new StringBuilder();
        int len = raw.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 4 == 0) {
                sb.append(" ");
            }
            sb.append(raw.charAt(i));
        }
        sb.append("b");
        return sb.toString();
    }
}
