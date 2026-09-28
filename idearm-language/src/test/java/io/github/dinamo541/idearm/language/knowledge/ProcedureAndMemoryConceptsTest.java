package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AA-P4-04 & AA-P4-05: Procedures, Stack Frames, ABI, and Memory Concepts")
class ProcedureAndMemoryConceptsTest {

    private static Corpus corpus;

    @BeforeAll
    static void setUp() {
        corpus = CorpusLoader.load();
    }

    @Test
    @DisplayName("AA-P4-04 / Acceptance Case 7: Stack frame mechanics and convention distinction")
    void testStackFrameConcept() {
        Optional<ConceptEntry> opt = corpus.findConcept("concept.proc.stack_frame");
        assertTrue(opt.isPresent(), "concept.proc.stack_frame must be loaded in corpus");

        ConceptEntry concept = opt.get();
        assertEquals("PROCEDURES", concept.category());
        assertFalse(concept.sources().isEmpty());

        // Check BP anchor explanation
        assertTrue(concept.contentEs().contains("MOV BP, SP"));
        assertTrue(concept.contentEs().contains("[bp+0]"));
        assertTrue(concept.contentEs().contains("[bp+2]"));
        assertTrue(concept.contentEs().contains("[bp+4]"));
        assertTrue(concept.contentEs().contains("[bp+6]"));

        // Check distinction between instruction and convention
        assertTrue(concept.contentEs().contains("RET"));
        assertTrue(concept.contentEs().toLowerCase().contains("convención, no la instrucción")
                || concept.contentEs().toLowerCase().contains("convención"));

        // Check recursion and ENTER caveat
        assertTrue(concept.contentEs().toLowerCase().contains("recursión"));
        assertTrue(concept.contentEs().contains("ENTER"));
        assertTrue(concept.contentEs().contains("emu8086"));
    }

    @Test
    @DisplayName("AA-P4-04 / Acceptance Case 7: Calling conventions and ABI across 4 profiles")
    void testCallingConventionsConcept() {
        Optional<ConceptEntry> opt = corpus.findConcept("concept.proc.calling_conventions");
        assertTrue(opt.isPresent(), "concept.proc.calling_conventions must be loaded");

        ConceptEntry concept = opt.get();
        // 1. 16-bit DOS conventions
        assertTrue(concept.contentEs().contains("cdecl"));
        assertTrue(concept.contentEs().contains("Pascal"));
        assertTrue(concept.contentEs().contains("DS:DX"));

        // 2. 32-bit conventions
        assertTrue(concept.contentEs().contains("stdcall"));

        // 3. Win64 Microsoft x64 ABI
        assertTrue(concept.contentEs().contains("RCX"));
        assertTrue(concept.contentEs().contains("RDX"));
        assertTrue(concept.contentEs().contains("R8"));
        assertTrue(concept.contentEs().contains("R9"));
        assertTrue(concept.contentEs().toLowerCase().contains("sombra") || concept.contentEs().contains("Shadow Space"));
        assertTrue(concept.contentEs().contains("32 bytes"));
        assertTrue(concept.contentEs().contains("16 bytes"));

        // 4. Linux64 System V AMD64 psABI
        assertTrue(concept.contentEs().contains("RDI"));
        assertTrue(concept.contentEs().contains("RSI"));
        assertTrue(concept.contentEs().toLowerCase().contains("roja") || concept.contentEs().contains("Red Zone"));
        assertTrue(concept.contentEs().contains("128 bytes"));
        assertTrue(concept.contentEs().contains("SYSCALL"));
    }

    @Test
    @DisplayName("AA-P4-05: Real-mode segmentation explicitly refutes the 4-segment myth")
    void testSegmentationConcept() {
        Optional<ConceptEntry> opt = corpus.findConcept("concept.mem.segmentation");
        assertTrue(opt.isPresent(), "concept.mem.segmentation must be loaded");

        ConceptEntry concept = opt.get();
        assertEquals("MEMORY", concept.category());

        // Criteria 1: Explicitly states a program is not limited to 4 segments
        assertTrue(concept.contentEs().toLowerCase().contains("no está limitado a cuatro segmentos")
                || concept.contentEs().toLowerCase().contains("no esta limitado a cuatro segmentos"));
        assertTrue(concept.contentEn().toLowerCase().contains("not limited to four segments"));

        // Formula check
        assertTrue(concept.contentEs().contains("16"));
        assertTrue(concept.contentEs().toLowerCase().contains("desplazamiento") || concept.contentEs().contains("Offset"));
    }

    @Test
    @DisplayName("AA-P4-05: Segments vs Directives vs Sections and COM non-profile note")
    void testSegmentsVsSectionsConcept() {
        Optional<ConceptEntry> opt = corpus.findConcept("concept.mem.segments_vs_sections");
        assertTrue(opt.isPresent(), "concept.mem.segments_vs_sections must be loaded");

        ConceptEntry concept = opt.get();

        // Criteria 2: Compares CPU segmentation, directives, and object sections without conflating them
        assertTrue(concept.contentEs().toLowerCase().contains("segmentos hardware"));
        assertTrue(concept.contentEs().toLowerCase().contains("directivas de ensamblador"));
        assertTrue(concept.contentEs().toLowerCase().contains("secciones de objeto"));

        // Build cycle
        assertTrue(concept.contentEs().contains(".asm"));
        assertTrue(concept.contentEs().contains(".obj"));
        assertTrue(concept.contentEs().contains("PE"));
        assertTrue(concept.contentEs().contains("ELF"));

        // Criteria 3: Explicit clarification on COM
        assertTrue(concept.contentEs().toLowerCase().contains("com no es un ejecutable"));
    }
}
