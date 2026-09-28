package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architectural and data integrity test for the Academic Knowledge Corpus (AA-P1-08).
 * Ensures stable identifiers, source citations, linguistic parity, and alias consistency.
 */
class CorpusIntegrityTest {

    private static Corpus corpus;

    @BeforeAll
    static void setUp() {
        corpus = Corpus.get();
        assertNotNull(corpus, "Corpus should load cleanly");
    }

    @Test
    void verifiesIdentifierUniquenessAcrossAllEntities() {
        Set<String> seenIds = new HashSet<>();
        List<String> duplicates = new ArrayList<>();

        for (InstructionEntry entry : corpus.getAllInstructions()) {
            if (!seenIds.add(entry.id())) {
                duplicates.add(entry.id());
            }
            for (InstructionForm form : entry.forms()) {
                if (!seenIds.add(form.id())) {
                    duplicates.add(form.id());
                }
            }
        }

        for (RegisterEntry reg : corpus.getAllRegisters()) {
            if (!seenIds.add(reg.id())) {
                duplicates.add(reg.id());
            }
        }

        for (SyntaxItem item : corpus.getAllSyntaxItems()) {
            if (!seenIds.add(item.id())) {
                duplicates.add(item.id());
            }
        }

        for (ServiceEntry service : corpus.getAllServices()) {
            if (!seenIds.add(service.id())) {
                duplicates.add(service.id());
            }
        }

        for (ConceptEntry concept : corpus.getAllConcepts()) {
            if (!seenIds.add(concept.id())) {
                duplicates.add(concept.id());
            }
        }

        assertTrue(duplicates.isEmpty(), "Duplicate entity identifiers detected: " + duplicates);
    }

    @Test
    void verifiesMandatorySourceCitations() {
        List<String> missingSources = new ArrayList<>();

        for (InstructionEntry entry : corpus.getAllInstructions()) {
            if (entry.sources().isEmpty()) {
                missingSources.add(entry.mnemonic() + " (" + entry.id() + ")");
            } else {
                for (String src : entry.sources()) {
                    assertFalse(src.isBlank(), entry.mnemonic() + " has blank source citation");
                }
            }
        }

        assertTrue(missingSources.isEmpty(),
                "Instructions lacking authoritative source citations: " + missingSources);
    }

    @Test
    void verifiesBilingualTextParity() {
        List<String> lackingParity = new ArrayList<>();

        for (InstructionEntry entry : corpus.getAllInstructions()) {
            if (entry.summaryEn().isBlank() || entry.summaryEs().isBlank()
                    || entry.descriptionEn().isBlank() || entry.descriptionEs().isBlank()) {
                lackingParity.add(entry.mnemonic() + " [en=" + !entry.summaryEn().isBlank() + ", es=" + !entry.summaryEs().isBlank() + "]");
            }
        }

        assertTrue(lackingParity.isEmpty(),
                "Instructions with missing localized summaries or descriptions: " + lackingParity);
    }

    @Test
    void verifiesAliasConsistency() {
        for (InstructionEntry entry : corpus.getAllInstructions()) {
            for (AliasDeclaration alias : entry.aliases()) {
                String aliasName = alias.name().toUpperCase(Locale.ROOT);
                // An alias should not be another primary mnemonic
                Optional<InstructionEntry> resolved = corpus.findInstruction(aliasName);
                assertTrue(resolved.isPresent(), "Alias " + aliasName + " should resolve");
                assertEquals(entry.mnemonic(), resolved.get().mnemonic(),
                        "Alias " + aliasName + " should resolve to parent mnemonic " + entry.mnemonic());
            }
        }
    }

    @Test
    void verifiesSyntaxFormsAndCategories() {
        for (InstructionEntry entry : corpus.getAllInstructions()) {
            assertNotNull(entry.category(), entry.mnemonic() + " has null category");
            assertNotNull(entry.minCpuGen(), entry.mnemonic() + " has null minCpuGen");
            assertFalse(entry.syntaxVariants().isEmpty(), entry.mnemonic() + " has empty syntax variants");
        }
    }
}
