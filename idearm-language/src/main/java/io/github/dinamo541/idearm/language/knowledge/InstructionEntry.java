package io.github.dinamo541.idearm.language.knowledge;

import io.github.dinamo541.idearm.language.catalog.CpuLevel;
import io.github.dinamo541.idearm.language.catalog.FlagSummary;
import io.github.dinamo541.idearm.language.catalog.InstructionCategory;
import io.github.dinamo541.idearm.language.catalog.InstructionInfo;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Top-level canonical instruction entry in the Academic Knowledge Base.
 */
public record InstructionEntry(
        String id,
        String mnemonic,
        InstructionKind kind,
        String family,
        InstructionCategory category,
        PedagogicalLevel pedagogicalLevel,
        List<String> prerequisites,
        List<AliasDeclaration> aliases,
        List<String> homonyms,
        List<RelatedInstruction> related,
        List<InstructionForm> forms,
        List<String> pitfalls,
        List<String> counterExamples,
        List<String> sources,
        Set<Dialect> recognizedBy,
        String summaryEn,
        String summaryEs,
        String descriptionEn,
        String descriptionEs,
        String example,
        List<String> syntaxVariants,
        CpuGeneration minCpuGen,
        FlagSummary flagSummary
) {
    public InstructionEntry {
        prerequisites = prerequisites != null ? Collections.unmodifiableList(prerequisites) : List.of();
        aliases = aliases != null ? Collections.unmodifiableList(aliases) : List.of();
        homonyms = homonyms != null ? Collections.unmodifiableList(homonyms) : List.of();
        related = related != null ? Collections.unmodifiableList(related) : List.of();
        forms = forms != null ? Collections.unmodifiableList(forms) : List.of();
        pitfalls = pitfalls != null ? Collections.unmodifiableList(pitfalls) : List.of();
        counterExamples = counterExamples != null ? Collections.unmodifiableList(counterExamples) : List.of();
        sources = sources != null ? Collections.unmodifiableList(sources) : List.of();
        recognizedBy = recognizedBy != null ? Collections.unmodifiableSet(recognizedBy) : Set.of();
        syntaxVariants = syntaxVariants != null ? Collections.unmodifiableList(syntaxVariants) : List.of();
        category = category != null ? category : InstructionCategory.DATA_TRANSFER;
        minCpuGen = minCpuGen != null ? minCpuGen : CpuGeneration.I8086;
        flagSummary = flagSummary != null ? flagSummary : FlagSummary.none();
        example = example != null ? example : "";
        summaryEn = summaryEn != null ? summaryEn : "";
        summaryEs = summaryEs != null ? summaryEs : "";
        descriptionEn = descriptionEn != null ? descriptionEn : "";
        descriptionEs = descriptionEs != null ? descriptionEs : "";
    }

    /**
     * Converts to the legacy InstructionInfo record for 100% backward compatibility.
     */
    public InstructionInfo toInstructionInfo() {
        CpuLevel legacyCpu = minCpuGen.toCpuLevel();
        return new InstructionInfo(
                mnemonic,
                category,
                summaryEn,
                summaryEs,
                syntaxVariants,
                legacyCpu,
                flagSummary,
                descriptionEn,
                descriptionEs,
                example
        );
    }

    /**
     * Accent-insensitive search matching against mnemonic, summaries, descriptions, syntax,
     * aliases, examples, and synonyms.
     */
    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String cleanQuery = stripAccents(query).toLowerCase(Locale.ROOT).trim();

        if (stripAccents(mnemonic).toLowerCase(Locale.ROOT).contains(cleanQuery)) return true;
        for (AliasDeclaration alias : aliases) {
            if (stripAccents(alias.name()).toLowerCase(Locale.ROOT).contains(cleanQuery)) return true;
        }
        if (stripAccents(summaryEn).toLowerCase(Locale.ROOT).contains(cleanQuery)) return true;
        if (stripAccents(summaryEs).toLowerCase(Locale.ROOT).contains(cleanQuery)) return true;
        if (stripAccents(descriptionEn).toLowerCase(Locale.ROOT).contains(cleanQuery)) return true;
        if (stripAccents(descriptionEs).toLowerCase(Locale.ROOT).contains(cleanQuery)) return true;
        if (stripAccents(example).toLowerCase(Locale.ROOT).contains(cleanQuery)) return true;
        for (String v : syntaxVariants) {
            if (stripAccents(v).toLowerCase(Locale.ROOT).contains(cleanQuery)) return true;
        }
        for (InstructionForm form : forms) {
            for (SyntaxForm sf : form.syntax()) {
                if (stripAccents(sf.text()).toLowerCase(Locale.ROOT).contains(cleanQuery)) return true;
            }
        }
        return false;
    }

    private static String stripAccents(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
