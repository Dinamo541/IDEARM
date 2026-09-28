package io.github.dinamo541.idearm.language.knowledge;

import io.github.dinamo541.idearm.language.catalog.FlagEffect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Authoritative semantic validation for Family F-05: Shifts and Rotations.
 * Verifies Acceptance Case 6 from Annex C/F:
 * - CF and OF differentiated under the same data
 * - Shift effects conditioned by count (count = 1 vs count > 1 vs count = 0)
 * - Logical vs arithmetic shift semantics (SHR clears SF, SAR preserves SF, SAR clears OF on count 1)
 * - Rotations preserve status flags (SF, ZF, AF, PF unaffected)
 * - 80186+ CPU requirements for imm8 count > 1, 80386+ for double-precision SHLD/SHRD
 * - No assertions on undefined flags
 */
class FamilyF05SemanticsTest {

    private static Corpus corpus;

    @BeforeAll
    static void initCorpus() {
        corpus = Corpus.get();
    }

    @Test
    @DisplayName("F-05 completeness: All 10 instructions exist in knowledge base")
    void allTenInstructionsExist() {
        String[] mnemonics = {"SHL", "SAL", "SHR", "SAR", "ROL", "ROR", "RCL", "RCR", "SHLD", "SHRD"};
        for (String m : mnemonics) {
            Optional<InstructionEntry> entry = corpus.findInstruction(m);
            assertTrue(entry.isPresent(), "Mnemonic " + m + " must be present in Corpus");
            assertEquals("F-05", entry.get().family(), "Mnemonic " + m + " must belong to Family F-05");
        }
    }

    @Test
    @DisplayName("F-05 alias: SAL is a canonical dialect-common alias of SHL")
    void salIsAliasOfShl() {
        InstructionEntry shl = corpus.findInstruction("SHL").orElseThrow();
        assertTrue(shl.aliases().stream().anyMatch(a -> "SAL".equals(a.name()) && a.dialect() == Dialect.COMMON),
                "SHL must declare SAL as a COMMON dialect alias");

        InstructionEntry salResolved = corpus.findInstruction("SAL").orElseThrow();
        assertEquals("SHL", salResolved.mnemonic(), "Looking up SAL must resolve to the SHL entry");
    }

    @Test
    @DisplayName("F-05 Case 6: Shift flag conditions depending on count")
    void shiftFlagConditionsDifferentiateByCount() {
        InstructionEntry shl = corpus.findInstruction("SHL").orElseThrow();

        // Form 1: SHL reg, 1 -> count is 1, OF is MODIFIED
        InstructionForm form1 = shl.forms().stream().filter(f -> "SHL reg, 1".equals(f.operationPlain())).findFirst().orElseThrow();
        FlagEffectSpec ofForm1 = form1.flags().stream().filter(f -> "OF".equals(f.flagId())).findFirst().orElseThrow();
        assertEquals(FlagEffect.MODIFIED, ofForm1.effect());
        assertEquals("count == 1", ofForm1.condition());

        // Form 3: SHL reg, CL -> for count > 1, OF is UNDEFINED
        InstructionForm formCl = shl.forms().stream().filter(f -> "SHL reg, CL".equals(f.operationPlain())).findFirst().orElseThrow();
        FlagEffectSpec ofFormCl = formCl.flags().stream().filter(f -> "OF".equals(f.flagId())).findFirst().orElseThrow();
        assertEquals(FlagEffect.UNDEFINED, ofFormCl.effect());
        assertEquals("count > 1", ofFormCl.condition());

        // AF is explicitly marked as UNDEFINED for shifts
        FlagEffectSpec afSpec = form1.flags().stream().filter(f -> "AF".equals(f.flagId())).findFirst().orElseThrow();
        assertEquals(FlagEffect.UNDEFINED, afSpec.effect());
    }

    @Test
    @DisplayName("F-05 Case 6: Arithmetic vs Logical shift differences (SHR vs SAR)")
    void arithmeticVsLogicalShiftDifferentiated() {
        InstructionEntry shr = corpus.findInstruction("SHR").orElseThrow();
        InstructionEntry sar = corpus.findInstruction("SAR").orElseThrow();

        // In SHR count=1, SF is CLEARED (always 0) because MSB is 0
        InstructionForm shrForm1 = shr.forms().stream().filter(f -> "SHR reg, 1".equals(f.operationPlain())).findFirst().orElseThrow();
        FlagEffectSpec shrSf = shrForm1.flags().stream().filter(f -> "SF".equals(f.flagId())).findFirst().orElseThrow();
        assertEquals(FlagEffect.CLEARED, shrSf.effect(), "SHR always clears SF to 0");

        // In SAR count=1, SF is MODIFIED (sign preserved), OF is CLEARED (always 0, sign cannot change)
        InstructionForm sarForm1 = sar.forms().stream().filter(f -> "SAR reg, 1".equals(f.operationPlain())).findFirst().orElseThrow();
        FlagEffectSpec sarSf = sarForm1.flags().stream().filter(f -> "SF".equals(f.flagId())).findFirst().orElseThrow();
        assertEquals(FlagEffect.MODIFIED, sarSf.effect(), "SAR preserves the sign in SF");

        FlagEffectSpec sarOf = sarForm1.flags().stream().filter(f -> "OF".equals(f.flagId())).findFirst().orElseThrow();
        assertEquals(FlagEffect.CLEARED, sarOf.effect(), "SAR clears OF to 0 for count == 1");
    }

    @Test
    @DisplayName("F-05 Case 6: Rotations do not affect arithmetic status flags")
    void rotationsPreserveStatusFlags() {
        String[] rotates = {"ROL", "ROR", "RCL", "RCR"};
        for (String rotMnemonic : rotates) {
            InstructionEntry rot = corpus.findInstruction(rotMnemonic).orElseThrow();
            // Flag summary overall
            assertEquals(FlagEffect.UNAFFECTED, rot.flagSummary().s(), rotMnemonic + " must not affect SF");
            assertEquals(FlagEffect.UNAFFECTED, rot.flagSummary().z(), rotMnemonic + " must not affect ZF");
            assertEquals(FlagEffect.UNAFFECTED, rot.flagSummary().a(), rotMnemonic + " must not affect AF");
            assertEquals(FlagEffect.UNAFFECTED, rot.flagSummary().p(), rotMnemonic + " must not affect PF");
            // Only CF and OF are affected
            assertEquals(FlagEffect.MODIFIED, rot.flagSummary().c(), rotMnemonic + " affects CF");
            assertEquals(FlagEffect.MODIFIED, rot.flagSummary().o(), rotMnemonic + " affects OF");
        }
    }

    @Test
    @DisplayName("F-05 CPU requirements: immediate counts > 1 require 80186, SHLD/SHRD require 80386")
    void cpuRequirementsEnforced() {
        InstructionEntry shl = corpus.findInstruction("SHL").orElseThrow();
        InstructionForm immForm = shl.forms().stream().filter(f -> "SHL reg, imm8".equals(f.operationPlain())).findFirst().orElseThrow();
        assertEquals(CpuGeneration.I80186, immForm.requirement().minGeneration(), "Immediate shift count requires 80186+");

        InstructionEntry shld = corpus.findInstruction("SHLD").orElseThrow();
        assertEquals(CpuGeneration.I80386, shld.minCpuGen(), "SHLD baseline requires 80386+");

        InstructionEntry shrd = corpus.findInstruction("SHRD").orElseThrow();
        assertEquals(CpuGeneration.I80386, shrd.minCpuGen(), "SHRD baseline requires 80386+");
    }

    @Test
    @DisplayName("F-05 Quality: Every instruction has pitfalls, counterexamples, and verified ASSEMBLED examples")
    void qualityAndVerifiedExamples() {
        String[] mnemonics = {"SHL", "SHR", "SAR", "ROL", "ROR", "RCL", "RCR", "SHLD", "SHRD"};
        for (String m : mnemonics) {
            InstructionEntry entry = corpus.findInstruction(m).orElseThrow();
            assertFalse(entry.pitfalls().isEmpty(), m + " must have pedagogical pitfalls");
            assertNotNull(entry.example(), m + " must have a runnable example");
            assertTrue(entry.example().contains("ASSEMBLED"), m + " example must be verified with ASSEMBLED marker");
            assertFalse(entry.sources().isEmpty(), m + " must cite authoritative sources");
        }
    }
}
