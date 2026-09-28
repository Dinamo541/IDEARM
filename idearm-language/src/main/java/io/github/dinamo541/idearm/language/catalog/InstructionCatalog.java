package io.github.dinamo541.idearm.language.catalog;

import io.github.dinamo541.idearm.language.knowledge.Corpus;
import io.github.dinamo541.idearm.language.knowledge.InstructionEntry;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Built-in educational knowledge base of x86 Assembly instructions.
 * Provides syntax forms, affected flags, minimum CPU requirements, categories, and bilingual descriptions (English & Spanish).
 *
 * <p>Delegates directly to the modern Academic Knowledge Corpus (ADR-013), eliminating static bytecode overhead
 * while maintaining 100% backward compatibility for all existing callers.
 */
public final class InstructionCatalog {

    private InstructionCatalog() {}

    /**
     * Looks up an instruction by its primary mnemonic or known variant alias (e.g. JE or JZ, WAIT or FWAIT).
     * Automatically trims whitespace and is case-insensitive.
     */
    public static Optional<InstructionInfo> find(String mnemonic) {
        return Corpus.get().findInstruction(mnemonic).map(InstructionEntry::toInstructionInfo);
    }

    public static boolean isKnownInstruction(String mnemonic) {
        return Corpus.get().isKnownInstruction(mnemonic);
    }

    public static List<InstructionInfo> getAll() {
        return Corpus.get().getAllInstructions().stream()
                .map(InstructionEntry::toInstructionInfo)
                .toList();
    }

    public static List<InstructionInfo> getForCpu(String targetCpu) {
        return Corpus.get().getForCpu(targetCpu).stream()
                .map(InstructionEntry::toInstructionInfo)
                .toList();
    }

    public static List<InstructionInfo> getByCategory(InstructionCategory category) {
        return Corpus.get().getByCategory(category).stream()
                .map(InstructionEntry::toInstructionInfo)
                .toList();
    }

    public static List<InstructionInfo> searchStartingWith(String prefix) {
        return Corpus.get().searchStartingWith(prefix).stream()
                .map(InstructionEntry::toInstructionInfo)
                .toList();
    }

    /**
     * Multi-criteria filter supporting category, CPU compatibility, and accent-insensitive text search.
     */
    public static List<InstructionInfo> filter(InstructionCategory category, String targetCpu, String searchPrefix) {
        return Corpus.get().filter(category, targetCpu, searchPrefix).stream()
                .map(InstructionEntry::toInstructionInfo)
                .toList();
    }

    /**
     * Every mnemonic an assembler accepts: the primary names plus the variant aliases (JNBE for JA, SAL for SHL).
     *
     * <p>This is the single source of truth for "is this word an instruction" (ADR-011). The lexer, the editor
     * highlighter and the unknown-instruction lint rule all key on it, so a typo is never coloured as valid and a
     * valid mnemonic is never underlined as a typo. Iteration order is alphabetical and stable.
     */
    public static Set<String> knownMnemonics() {
        return Corpus.get().knownMnemonics();
    }

    /**
     * The mnemonics closest to a misspelling, for a "did you mean" hint. Empty when the word is already known or
     * nothing is close enough: a wrong suggestion teaches worse than none.
     */
    public static List<String> suggest(String mnemonic) {
        return Corpus.get().suggest(mnemonic);
    }
}
