package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies complete x87 FPU coverage, status/control word semantics, and Acceptance Case 11 (AA-P5-03).
 */
@DisplayName("AA-P5-03: Family F-15 (x87 FPU) Completeness & Acceptance Case 11")
class FamilyF15X87Test {

    private static Corpus corpus;

    @BeforeAll
    static void setUp() {
        corpus = Corpus.get();
        assertNotNull(corpus);
    }

    @Test
    @DisplayName("Verify that no x87 instruction declares minCpu as 8086 (Acceptance Criterion AA-P5-03)")
    void noX87InstructionDeclaresMinCpu8086() {
        List<InstructionEntry> f15Instructions = corpus.getAllInstructions().stream()
                .filter(i -> "F-15".equalsIgnoreCase(i.family()))
                .toList();

        assertFalse(f15Instructions.isEmpty(), "Family F-15 must contain x87 instructions");

        for (InstructionEntry instr : f15Instructions) {
            assertNotEquals("8086", instr.minCpuGen().displayName(),
                    "Instruction " + instr.mnemonic() + " must not declare 8086 as minCpu (coprocessor was 8087)");

            for (InstructionForm form : instr.forms()) {
                if (form.requirement() != null && form.requirement().minGeneration() != null) {
                    assertNotEquals(CpuGeneration.I8086, form.requirement().minGeneration(),
                            "Form " + form.id() + " of " + instr.mnemonic() + " must not declare 8086");
                }
            }
        }
    }

    @Test
    @DisplayName("Acceptance Case 11: FSQRT unavailability in emu8086 with exact reason and alternatives")
    void acceptanceCase11FsqrtUnavailabilityInEmu8086() {
        Optional<InstructionEntry> fsqrtOpt = corpus.findInstruction("FSQRT");
        assertTrue(fsqrtOpt.isPresent(), "FSQRT must be present in knowledge corpus");

        InstructionEntry fsqrt = fsqrtOpt.get();
        assertEquals("F-15", fsqrt.family());
        assertEquals("8087", fsqrt.minCpuGen().displayName());

        // Check bilingual description for exact reason and alternatives
        String descEs = fsqrt.descriptionEs().toLowerCase();
        String descEn = fsqrt.descriptionEn().toLowerCase();

        assertTrue(descEs.contains("emu8086") && descEs.contains("8087") && descEs.contains("opcode"),
                "Spanish description must explain emu8086 lack of 8087 coprocessor and opcode exception");
        assertTrue(descEs.contains("dosbox") || descEs.contains("nativos"),
                "Spanish description must provide alternatives (DOSBox, native profiles)");

        assertTrue(descEn.contains("emu8086") && descEn.contains("8087") && descEn.contains("opcode"),
                "English description must explain emu8086 lack of 8087 and opcode trap");
        assertTrue(descEn.contains("dosbox") || descEn.contains("native"),
                "English description must provide alternatives");

        // Verify form requirement and availability
        assertFalse(fsqrt.forms().isEmpty());
        InstructionForm form = fsqrt.forms().getFirst();
        assertTrue(form.requirement().features().contains(Feature.X87));

        Optional<BackendAvailability> emuAvail = form.availability().stream()
                .filter(a -> "emu8086".equalsIgnoreCase(a.backend()))
                .findFirst();
        assertTrue(emuAvail.isPresent(), "FSQRT must have explicit emu8086 availability declaration");
        assertEquals(BackendAvailability.AvailabilityStatus.UNAVAILABLE, emuAvail.get().status());
        assertNotNull(emuAvail.get().reason());
        assertTrue(emuAvail.get().reason().contains("8087") || emuAvail.get().reason().contains("coprocessor"));
    }

    @Test
    @DisplayName("FSTSW AX / SAHF bridge: verifies status word transfer and condition code mapping to CPU flags")
    void fstswAxBridgeToCpuFlags() {
        Optional<InstructionEntry> fstswOpt = corpus.findInstruction("FSTSW");
        assertTrue(fstswOpt.isPresent(), "FSTSW must be present in knowledge corpus");

        InstructionEntry fstsw = fstswOpt.get();
        assertEquals("F-15", fstsw.family());

        // Must show example connecting FSTSW AX with SAHF
        assertTrue(fstsw.example().toLowerCase().contains("fstsw ax"));
        assertTrue(fstsw.example().toLowerCase().contains("sahf"));

        // Description explains the bridge to conditional jumps
        assertTrue(fstsw.descriptionEs().contains("SAHF") && fstsw.descriptionEs().contains("salto condicional"));
        assertTrue(fstsw.descriptionEn().contains("SAHF") && fstsw.descriptionEn().contains("conditional jump"));
    }

    @Test
    @DisplayName("Verify x87 condition codes and status word effects on FLD, FADD, FCOM, FTST, FXCH")
    void x87ConditionCodesAndStatusWordEffects() {
        List<String> coreX87Mnemonics = List.of("FLD", "FST", "FSTP", "FADD", "FSUB", "FMUL", "FDIV", "FCOM", "FTST", "FXCH");
        for (String m : coreX87Mnemonics) {
            Optional<InstructionEntry> entry = corpus.findInstruction(m);
            assertTrue(entry.isPresent(), "Core x87 instruction " + m + " must be in corpus");
            assertEquals("F-15", entry.get().family());
            assertFalse(entry.get().sources().isEmpty());
        }
    }
}
