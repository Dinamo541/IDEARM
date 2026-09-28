package io.github.dinamo541.idearm.application.editor;

import io.github.dinamo541.idearm.language.knowledge.CompatibilityContext;
import io.github.dinamo541.idearm.language.knowledge.CpuGeneration;
import io.github.dinamo541.idearm.language.knowledge.Dialect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AA-P3-04: Completion from Corpus in Interface Language")
class CorpusCompletionTest {

    private static QueryCompletion queryCompletion;

    @BeforeAll
    static void setUp() {
        queryCompletion = new QueryCompletion();
    }

    @Test
    @DisplayName("Acceptance Criteria 1: With Spanish locale, completion details are in Spanish")
    void testSpanishCompletionDescriptions() {
        CompatibilityContext ctx = CompatibilityContext.forCpu(CpuGeneration.I8086);
        List<CompletionItem> itemsEs = queryCompletion.execute("MOV", ctx, Locale.of("es"), null);
        assertFalse(itemsEs.isEmpty(), "Completions for 'MOV' must not be empty");

        CompletionItem movItemEs = itemsEs.stream()
                .filter(i -> i.label().equalsIgnoreCase("MOV"))
                .findFirst()
                .orElse(null);
        assertNotNull(movItemEs, "Must find 'MOV' completion item");
        assertNotNull(movItemEs.detail(), "Detail must not be null");
        // Spanish summary for MOV contains Spanish keywords like "transfiere" or "copia" or "mueve"
        assertFalse(movItemEs.detail().isBlank(), "Spanish detail must not be blank");

        List<CompletionItem> itemsEn = queryCompletion.execute("MOV", ctx, Locale.ENGLISH, null);
        CompletionItem movItemEn = itemsEn.stream()
                .filter(i -> i.label().equalsIgnoreCase("MOV"))
                .findFirst()
                .orElse(null);
        assertNotNull(movItemEn);
        assertNotEquals(movItemEs.detail(), movItemEn.detail(), "Spanish and English details must differ");
    }

    @Test
    @DisplayName("Acceptance Criteria 2: Contextual availability filters out UNAVAILABLE candidates")
    void testUnavailableItemsFilteredOut() {
        // In 64-bit Long Mode: AAA, AAS, DAA, DAS are UNAVAILABLE
        CompatibilityContext ctx64 = CompatibilityContext.win64(Dialect.NASM, null);
        List<CompletionItem> items64 = queryCompletion.execute("AA", ctx64, Locale.ENGLISH, null);
        boolean hasAaaIn64 = items64.stream().anyMatch(i -> i.label().equalsIgnoreCase("AAA"));
        assertFalse(hasAaaIn64, "AAA must not be offered in 64-bit mode (UNAVAILABLE)");

        // In 8086 Real Mode: AAA IS available
        CompatibilityContext ctx8086 = CompatibilityContext.forCpu(CpuGeneration.I8086);
        List<CompletionItem> items8086 = queryCompletion.execute("AA", ctx8086, Locale.ENGLISH, null);
        boolean hasAaaIn8086 = items8086.stream().anyMatch(i -> i.label().equalsIgnoreCase("AAA"));
        assertTrue(hasAaaIn8086, "AAA must be offered in 8086 mode");
    }

    @Test
    @DisplayName("Acceptance Criteria 3: Aliases and 64-bit registers offered when appropriate")
    void testAliasesAndRegistersByContext() {
        // In 64-bit context: RAX should be offered
        CompatibilityContext ctx64 = CompatibilityContext.win64(Dialect.NASM, null);
        List<CompletionItem> items64 = queryCompletion.execute("RA", ctx64, Locale.ENGLISH, null);
        boolean hasRaxIn64 = items64.stream().anyMatch(i -> i.label().equalsIgnoreCase("RAX"));
        assertTrue(hasRaxIn64, "RAX must be offered in 64-bit context");

        // In 8086 context: RAX should NOT be offered
        CompatibilityContext ctx8086 = CompatibilityContext.forCpu(CpuGeneration.I8086);
        List<CompletionItem> items8086 = queryCompletion.execute("RA", ctx8086, Locale.ENGLISH, null);
        boolean hasRaxIn8086 = items8086.stream().anyMatch(i -> i.label().equalsIgnoreCase("RAX"));
        assertFalse(hasRaxIn8086, "RAX must NOT be offered in 8086 context");

        // Alias SAL should be offered when prefix is "SA"
        List<CompletionItem> saItems = queryCompletion.execute("SA", ctx8086, Locale.ENGLISH, null);
        boolean hasSal = saItems.stream().anyMatch(i -> i.label().equalsIgnoreCase("SAL"));
        assertTrue(hasSal, "Alias 'SAL' must be offered in completion");
    }

    /** AL is its own register and a view of AX, EAX and RAX; it was offered four times. */
    @Test
    void eachRegisterIsOfferedOnce() {
        CompatibilityContext ctx64 = CompatibilityContext.win64(Dialect.NASM, null);
        List<String> labels = queryCompletion.execute("AL", ctx64, Locale.ENGLISH, null).stream()
                .map(CompletionItem::label).toList();
        assertEquals(labels.stream().distinct().count(), labels.size(), "duplicates in " + labels);
        assertTrue(labels.stream().anyMatch("AL"::equalsIgnoreCase), labels.toString());
    }

    /** The CPU badge was capped at 80486, so CPUID showed "80486" instead of "Pentium". */
    @Test
    void instructionsNewerThanThe486KeepTheirCpu() {
        CompatibilityContext ctx64 = CompatibilityContext.win64(Dialect.NASM, null);
        var cpuid = queryCompletion.execute("CPUI", ctx64, Locale.ENGLISH, null).stream()
                .filter(i -> i.label().equalsIgnoreCase("CPUID")).findFirst().orElseThrow();
        assertEquals(io.github.dinamo541.idearm.language.catalog.CpuLevel.CPU_PENTIUM, cpuid.minCpu());
    }
}
