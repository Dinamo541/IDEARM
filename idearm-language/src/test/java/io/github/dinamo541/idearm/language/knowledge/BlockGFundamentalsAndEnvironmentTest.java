package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AA-P6-02 (Block G fundamentals), AA-P6-03 (Windows/Linux API and Acceptance Case 12),
 * and AA-P6-04 (Pedagogical Diagrams).
 */
@DisplayName("AA-P6-02, AA-P6-03 & AA-P6-04: Fundamentals, Environment APIs, Irvine32 (Case 12) & Diagrams")
class BlockGFundamentalsAndEnvironmentTest {

    private static Corpus corpus;

    @BeforeAll
    static void setUp() {
        corpus = Corpus.get();
        assertNotNull(corpus, "Corpus must not be null");
    }

    @Test
    @DisplayName("AA-P6-02: Block G fundamental concepts linked to instructions and functional examples")
    void verifyBlockGFundamentals() {
        String[] fundIds = {
                "concept.fund.binary_numbers",
                "concept.fund.twos_complement",
                "concept.fund.carry_vs_overflow",
                "concept.fund.ascii_encoding",
                "concept.fund.boolean_logic_masks"
        };

        for (String id : fundIds) {
            Optional<ConceptEntry> conceptOpt = corpus.findConcept(id);
            assertTrue(conceptOpt.isPresent(), "Concept " + id + " must be present in corpus");
            ConceptEntry c = conceptOpt.get();

            assertEquals("FUNDAMENTALS", c.category(), id + " must belong to FUNDAMENTALS category");
            assertNotNull(c.titleEs());
            assertNotNull(c.titleEn());
            assertFalse(c.titleEs().isBlank());
            assertFalse(c.titleEn().isBlank());

            assertNotNull(c.contentEs());
            assertNotNull(c.contentEn());
            assertFalse(c.contentEs().isBlank());
            assertFalse(c.contentEn().isBlank());

            assertFalse(c.relatedInstructions().isEmpty(), id + " must link to related instructions");
            assertFalse(c.sources().isEmpty(), id + " must cite sources");
        }

        // Verify specific semantic insights in carry vs overflow
        ConceptEntry carryVsOverflow = corpus.findConcept("concept.fund.carry_vs_overflow").orElseThrow();
        assertTrue(carryVsOverflow.contentEs().contains("CF") && carryVsOverflow.contentEs().contains("OF"),
                "carry_vs_overflow content must discuss CF and OF");
        assertTrue(carryVsOverflow.relatedInstructions().contains("ADD") && carryVsOverflow.relatedInstructions().contains("SUB"),
                "carry_vs_overflow must link to ADD and SUB");
    }

    @Test
    @DisplayName("AA-P6-03: Windows API (kernel32) and Linux 64-bit direct syscalls")
    void verifyEnvironmentApis() {
        // Windows API
        Optional<ConceptEntry> winOpt = corpus.findConcept("concept.env.win32_api");
        assertTrue(winOpt.isPresent(), "concept.env.win32_api must exist");
        ConceptEntry win = winOpt.get();
        assertEquals("ENVIRONMENT", win.category());
        assertTrue(win.contentEs().contains("kernel32") || win.contentEs().contains("ExitProcess"),
                "Win32 API concept must mention kernel32 and ExitProcess");
        assertTrue(win.contentEs().contains("stdcall"), "Win32 API must explain stdcall");

        // Linux Syscalls
        Optional<ConceptEntry> linOpt = corpus.findConcept("concept.env.linux_syscalls");
        assertTrue(linOpt.isPresent(), "concept.env.linux_syscalls must exist");
        ConceptEntry lin = linOpt.get();
        assertEquals("ENVIRONMENT", lin.category());
        assertTrue(lin.contentEs().contains("SYSCALL") || lin.contentEs().contains("RAX"),
                "Linux syscalls must mention SYSCALL instruction and RAX register");
        assertTrue(lin.contentEs().contains("R10"), "Linux syscalls must mention R10 argument convention");
        assertTrue(lin.relatedInstructions().contains("SYSCALL"));
    }

    @Test
    @DisplayName("AA-P6-03 / Acceptance Case 12: External dependency Irvine32 marked as third-party and not distributed by IDEARM")
    void verifyAcceptanceCase12ThirdPartyIrvine32() {
        Optional<ConceptEntry> irvineOpt = corpus.findConcept("concept.env.third_party_libraries");
        assertTrue(irvineOpt.isPresent(), "concept.env.third_party_libraries must exist for Acceptance Case 12");
        ConceptEntry irvine = irvineOpt.get();

        String contentEs = irvine.contentEs().toLowerCase();
        String contentEn = irvine.contentEn().toLowerCase();

        // 1. Must explicitly mention Irvine32 / Kip Irvine
        assertTrue(contentEs.contains("irvine32") && contentEs.contains("kip irvine"),
                "Must mention Irvine32 and Kip Irvine");

        // 2. Must explicitly state that IDEARM does NOT distribute it
        assertTrue(contentEs.contains("no distribuye") || contentEs.contains("no incluye"),
                "Spanish content must state that IDEARM does not distribute or bundle Irvine32");
        assertTrue(contentEn.contains("does not distribute") || contentEn.contains("not distributed"),
                "English content must state that IDEARM does not distribute or bundle Irvine32");

        // 3. Must explain that user/project must configure include and lib paths
        assertTrue(contentEs.contains("rutas de inclusión") || contentEs.contains("include"),
                "Must guide how to configure include/lib paths");
    }

    @Test
    @DisplayName("AA-P6-04: Pedagogical Diagrams for Register Slicing, Stack Frame, and 20-bit Segmentation")
    void verifyPedagogicalDiagrams() {
        // 1. Register Hierarchy Diagram
        String raxDiagram = PedagogicalDiagrams.renderRegisterHierarchy("RAX");
        assertNotNull(raxDiagram);
        assertTrue(raxDiagram.contains("RAX (64 bits"), "Diagram must show 64-bit RAX");
        assertTrue(raxDiagram.contains("EAX (32 bits"), "Diagram must show 32-bit EAX");
        assertTrue(raxDiagram.contains("AX (16 bits)"), "Diagram must show 16-bit AX");
        assertTrue(raxDiagram.contains("AH") && raxDiagram.contains("AL"), "Diagram must show AH and AL");
        assertTrue(raxDiagram.contains("CERO") || raxDiagram.contains("cero"),
                "Rules must explain zero-extension when writing 32-bit EAX in 64-bit mode");

        // 2. Stack Frame Diagram (32-bit and 16-bit)
        String stack32 = PedagogicalDiagrams.renderStackFrame(true);
        assertNotNull(stack32);
        assertTrue(stack32.contains("[EBP + 8]") && stack32.contains("[EBP + 12]"),
                "32-bit stack frame must show parameters at [EBP+8] and [EBP+12]");
        assertTrue(stack32.contains("EIP") || stack32.contains("Retorno"),
                "Stack frame must show return address");
        assertTrue(stack32.contains("[EBP - 4]") || stack32.contains("Local"),
                "Stack frame must show local variables below EBP");

        String stack16 = PedagogicalDiagrams.renderStackFrame(false);
        assertNotNull(stack16);
        assertTrue(stack16.contains("[BP + 4]") && stack16.contains("[BP + 6]"),
                "16-bit stack frame must show parameters at [BP+4] and [BP+6]");

        // 3. 20-bit Segmentation Diagram
        String segDiagram = PedagogicalDiagrams.renderSegmentation20Bit(0x1234, 0x0100);
        assertNotNull(segDiagram);
        assertTrue(segDiagram.contains("1234h:0100h"), "Must display logical address");
        assertTrue(segDiagram.contains("12340h"), "Must display shifted segment (12340h)");
        assertTrue(segDiagram.contains("12440h"), "Must display calculated physical address (12440h)");
        assertTrue(segDiagram.contains("1 MB") || segDiagram.contains("00000h a FFFFFh"),
                "Must note 1 MB physical address space");
    }
}
