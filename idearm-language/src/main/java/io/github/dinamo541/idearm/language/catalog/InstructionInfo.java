package io.github.dinamo541.idearm.language.catalog;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Educational metadata for an x86 Assembly instruction.
 */
public record InstructionInfo(
        String mnemonic,
        InstructionCategory category,
        String summaryEn,
        String summaryEs,
        List<String> syntaxVariants,
        CpuLevel minCpu,
        FlagSummary flags,
        String descriptionEn,
        String descriptionEs,
        String example
) {
    public InstructionInfo {
        Objects.requireNonNull(mnemonic, "mnemonic cannot be null");
        category = category != null ? category : InstructionCategory.DATA_TRANSFER;
        Objects.requireNonNull(summaryEn, "summaryEn cannot be null");
        Objects.requireNonNull(summaryEs, "summaryEs cannot be null");
        syntaxVariants = syntaxVariants != null ? List.copyOf(syntaxVariants) : List.of();
        flags = flags != null ? flags : FlagSummary.none();
        minCpu = minCpu != null ? minCpu : CpuLevel.CPU_8086;
        descriptionEn = descriptionEn != null ? descriptionEn : "";
        descriptionEs = descriptionEs != null ? descriptionEs : "";
        example = example != null ? example : "";
    }

    /**
     * Backwards-compatible convenience constructor defaulting category to DATA_TRANSFER.
     */
    public InstructionInfo(
            String mnemonic,
            String summaryEn,
            String summaryEs,
            List<String> syntaxVariants,
            CpuLevel minCpu,
            FlagSummary flags,
            String descriptionEn,
            String descriptionEs,
            String example
    ) {
        this(mnemonic, InstructionCategory.DATA_TRANSFER, summaryEn, summaryEs, syntaxVariants, minCpu, flags, descriptionEn, descriptionEs, example);
    }

    /**
     * Localized short summary according to language code (e.g. "es", "en").
     */
    public String summary(String languageCode) {
        if (languageCode != null && languageCode.toLowerCase(Locale.ROOT).startsWith("es")) {
            return summaryEs;
        }
        return summaryEn;
    }

    /**
     * Localized detailed explanation according to language code (e.g. "es", "en").
     */
    public String description(String languageCode) {
        if (languageCode != null && languageCode.toLowerCase(Locale.ROOT).startsWith("es")) {
            return descriptionEs;
        }
        return descriptionEn;
    }

    /**
     * Checks if this instruction matches a search query against its mnemonic, summaries,
     * description, or syntax forms, ignoring accents and case.
     */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String clean = stripAccents(query.toLowerCase(Locale.ROOT).trim());
        if (stripAccents(mnemonic.toLowerCase(Locale.ROOT)).contains(clean)) return true;
        if (stripAccents(summaryEn.toLowerCase(Locale.ROOT)).contains(clean)) return true;
        if (stripAccents(summaryEs.toLowerCase(Locale.ROOT)).contains(clean)) return true;
        if (stripAccents(descriptionEn.toLowerCase(Locale.ROOT)).contains(clean)) return true;
        if (stripAccents(descriptionEs.toLowerCase(Locale.ROOT)).contains(clean)) return true;
        if (stripAccents(example.toLowerCase(Locale.ROOT)).contains(clean)) return true;
        for (String v : syntaxVariants) {
            if (stripAccents(v.toLowerCase(Locale.ROOT)).contains(clean)) return true;
        }
        return false;
    }

    private static final java.util.regex.Pattern COMBINING_MARKS = java.util.regex.Pattern.compile("\\p{M}");

    private static String stripAccents(String text) {
        if (text == null) return "";
        return COMBINING_MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
    }
}
