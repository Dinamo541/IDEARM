package io.github.dinamo541.idearm.language.knowledge;

import io.github.dinamo541.idearm.language.catalog.InstructionCatalog;
import io.github.dinamo541.idearm.language.catalog.InstructionInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests transversal corpus search indexing examples, aliases, and synonyms (AA-P1-07).
 */
class CorpusSearchTest {

    @Test
    void searchFindsInstructionByExamplePattern() {
        // Search "@data" which appears in the example of MOV
        List<InstructionInfo> dataMatches = InstructionCatalog.filter(null, null, "@data");
        assertFalse(dataMatches.isEmpty(), "Searching '@data' should return instructions using it in examples");
        assertTrue(dataMatches.stream().anyMatch(i -> i.mnemonic().equals("MOV")),
                "MOV should match '@data' via its example");

        // Search "offset" which appears in examples/descriptions of XLAT and LEA
        List<InstructionInfo> offsetMatches = InstructionCatalog.filter(null, null, "offset");
        assertFalse(offsetMatches.isEmpty(), "Searching 'offset' should return instructions referencing it");
        assertTrue(offsetMatches.stream().anyMatch(i -> i.mnemonic().equals("XLAT") || i.mnemonic().equals("LEA")),
                "XLAT or LEA should match 'offset'");
    }

    @Test
    void searchFindsInstructionByAlias() {
        // Search "jnbe" which is an alias of JA
        List<InstructionInfo> jnbeMatches = InstructionCatalog.filter(null, null, "jnbe");
        assertFalse(jnbeMatches.isEmpty(), "Searching 'jnbe' should return JA");
        assertTrue(jnbeMatches.stream().anyMatch(i -> i.mnemonic().equals("JA")),
                "JA should be found when searching alias 'jnbe'");

        // Search "sal" which is an alias of SHL
        List<InstructionInfo> salMatches = InstructionCatalog.filter(null, null, "sal");
        assertFalse(salMatches.isEmpty(), "Searching 'sal' should return SHL");
        assertTrue(salMatches.stream().anyMatch(i -> i.mnemonic().equals("SHL")),
                "SHL should be found when searching alias 'sal'");
    }

    @Test
    void searchThroughCorpusEntryMatchesDirectly() {
        Corpus corpus = Corpus.get();

        List<InstructionEntry> dataEntries = corpus.filter(null, null, "@data");
        assertTrue(dataEntries.stream().anyMatch(e -> e.mnemonic().equals("MOV")));

        List<InstructionEntry> salEntries = corpus.filter(null, null, "sal");
        assertTrue(salEntries.stream().anyMatch(e -> e.mnemonic().equals("SHL")));
    }
}
