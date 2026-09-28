package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies Acceptance Case 5: Comparative sheets and semantics for instruction pairs
 * (MOV/LEA, CMP/TEST, MUL/IMUL, DIV/IDIV) with signed differences, flags, and dividend preparation (AA-P5-02).
 */
@DisplayName("AA-P5-02: Comparative Instruction Pairs & Acceptance Case 5")
class ComparativePairsTest {

    private static Corpus corpus;

    @BeforeAll
    static void setUp() {
        corpus = Corpus.get();
        assertNotNull(corpus);
    }

    @Test
    @DisplayName("MOV vs LEA: compares memory dereference vs address computation without memory bus access")
    void movVsLeaComparativeSheet() {
        Optional<ConceptEntry> concept = corpus.getAllConcepts().stream()
                .filter(c -> "concept.pair.mov_vs_lea".equals(c.id()))
                .findFirst();
        assertTrue(concept.isPresent(), "concept.pair.mov_vs_lea must be registered");

        ConceptEntry c = concept.get();
        assertTrue(c.relatedInstructions().contains("MOV") && c.relatedInstructions().contains("LEA"));

        // Bilingual coverage
        assertTrue(c.contentEs().contains("Desreferencia memoria") && c.contentEs().contains("Load Effective Address"));
        assertTrue(c.contentEn().contains("Dereferences memory") && c.contentEn().contains("Load Effective Address"));
        assertTrue(c.contentEs().contains("sin acceder a la memoria") || c.contentEs().contains("sin tocar"));
        assertTrue(c.contentEn().contains("without performing any memory read"));
    }

    @Test
    @DisplayName("CMP vs TEST: compares arithmetic subtraction vs bitwise conjunction and flag behaviors")
    void cmpVsTestComparativeSheet() {
        Optional<ConceptEntry> concept = corpus.getAllConcepts().stream()
                .filter(c -> "concept.pair.cmp_vs_test".equals(c.id()))
                .findFirst();
        assertTrue(concept.isPresent(), "concept.pair.cmp_vs_test must be registered");

        ConceptEntry c = concept.get();
        assertTrue(c.relatedInstructions().contains("CMP") && c.relatedInstructions().contains("TEST"));

        assertTrue(c.contentEs().contains("resta implícita") && c.contentEs().contains("conjunción lógica"));
        assertTrue(c.contentEn().contains("implicit subtraction") && c.contentEn().contains("implicit bitwise logical"));
        assertTrue(c.contentEs().contains("CF = 0") && c.contentEs().contains("OF = 0"));
        assertTrue(c.contentEn().contains("CF = 0") && c.contentEn().contains("OF = 0"));
    }

    @Test
    @DisplayName("MUL vs IMUL: compares unsigned vs signed multiplication and 1/2/3 operand forms")
    void mulVsImulComparativeSheet() {
        Optional<ConceptEntry> concept = corpus.getAllConcepts().stream()
                .filter(c -> "concept.pair.mul_vs_imul".equals(c.id()))
                .findFirst();
        assertTrue(concept.isPresent(), "concept.pair.mul_vs_imul must be registered");

        ConceptEntry c = concept.get();
        assertTrue(c.relatedInstructions().contains("MUL") && c.relatedInstructions().contains("IMUL"));

        assertTrue(c.contentEs().contains("sin signo") && c.contentEs().contains("con signo"));
        assertTrue(c.contentEn().contains("Unsigned") && c.contentEn().contains("Signed"));
        assertTrue(c.contentEs().contains("1, 2 y 3 operandos"));
        assertTrue(c.contentEn().contains("1, 2, and 3-operand forms"));
    }

    @Test
    @DisplayName("DIV vs IDIV: compares unsigned vs signed division, dividend preparation (CBW/CWD), and #DE error")
    void divVsIdivComparativeSheet() {
        Optional<ConceptEntry> concept = corpus.getAllConcepts().stream()
                .filter(c -> "concept.pair.div_vs_idiv".equals(c.id()))
                .findFirst();
        assertTrue(concept.isPresent(), "concept.pair.div_vs_idiv must be registered");

        ConceptEntry c = concept.get();
        assertTrue(c.relatedInstructions().contains("DIV") && c.relatedInstructions().contains("IDIV"));
        assertTrue(c.relatedInstructions().contains("CBW") && c.relatedInstructions().contains("CWD"));

        // Setup instructions
        assertTrue(c.contentEs().contains("CBW") && c.contentEs().contains("CWD") && c.contentEs().contains("CDQ"));
        assertTrue(c.contentEn().contains("CBW") && c.contentEn().contains("CWD") && c.contentEn().contains("CDQ"));

        // Divide error exception #DE / INT 0
        assertTrue(c.contentEs().contains("#DE") && (c.contentEs().contains("divisor es cero") || c.contentEs().contains("interrupción 0")));
        assertTrue(c.contentEn().contains("#DE") && (c.contentEn().contains("divisor is zero") || c.contentEn().contains("Interrupt 0")));
    }
}
