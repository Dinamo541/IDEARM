package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AA-P5-05 (SIMD extensions MMX, SSE, SSE2, AVX) and AA-P5-06 (System opcodes, Undocumented, GNU as movabs).
 */
@DisplayName("AA-P5-05 & AA-P5-06: SIMD Extensions, System Instructions, Undocumented & GAS Movabs")
class SimdAndSystemInstructionsTest {

    private static Corpus corpus;

    @BeforeAll
    static void setUp() {
        corpus = Corpus.get();
        assertNotNull(corpus);
    }

    @Test
    @DisplayName("AA-P5-05: Family F-18 SIMD - MMX instructions present with proper features and 64-bit mmx semantics")
    void verifyMmxInstructions() {
        String[] mmxMnemonics = {"MOVD", "MOVQ", "PADDB", "PADDW", "PADDD", "EMMS"};
        for (String mnemonic : mmxMnemonics) {
            Optional<InstructionEntry> instrOpt = corpus.findInstruction(mnemonic);
            assertTrue(instrOpt.isPresent(), "MMX instruction " + mnemonic + " must be present in corpus");
            InstructionEntry instr = instrOpt.get();
            assertEquals("F-18", instr.family(), mnemonic + " must belong to family F-18");
            assertNotNull(instr.descriptionEs());
            assertNotNull(instr.descriptionEn());
            assertFalse(instr.descriptionEs().isBlank());
            assertFalse(instr.descriptionEn().isBlank());
        }

        // Verify EMMS details
        InstructionEntry emms = corpus.findInstruction("EMMS").orElseThrow();
        assertTrue(emms.descriptionEs().toLowerCase().contains("x87") || emms.descriptionEs().toLowerCase().contains("fpu"),
                "EMMS description must explain restoring x87 FPU tag word state");
    }

    @Test
    @DisplayName("AA-P5-05: Family F-18 SIMD - SSE & SSE2 instructions present with 128-bit XMM semantics")
    void verifySseAndSse2Instructions() {
        String[] sseMnemonics = {"MOVAPS", "MOVUPS", "ADDPS", "SUBPS", "MULPS", "DIVPS", "SQRTPS", "MAXPS", "MINPS", "RCPPS", "RSQRTPS"};
        for (String mnemonic : sseMnemonics) {
            Optional<InstructionEntry> instrOpt = corpus.findInstruction(mnemonic);
            assertTrue(instrOpt.isPresent(), "SSE instruction " + mnemonic + " must be present in corpus");
            InstructionEntry instr = instrOpt.get();
            assertEquals("F-18", instr.family());
            assertTrue(instr.descriptionEs().contains("128") || instr.descriptionEs().contains("XMM") || instr.descriptionEs().contains("SSE"),
                    mnemonic + " description must mention 128-bit XMM registers or SSE");
        }

        String[] sse2Mnemonics = {"MOVAPD", "MOVUPD", "ADDPD", "SUBPD", "MULPD", "DIVPD", "PADDQ", "PSUBQ"};
        for (String mnemonic : sse2Mnemonics) {
            Optional<InstructionEntry> instrOpt = corpus.findInstruction(mnemonic);
            assertTrue(instrOpt.isPresent(), "SSE2 instruction " + mnemonic + " must be present in corpus");
            InstructionEntry instr = instrOpt.get();
            assertEquals("F-18", instr.family());
        }
    }

    @Test
    @DisplayName("AA-P5-05: Family F-18 SIMD - Outlines for SSE3, SSSE3, SSE4.1, SSE4.2, AVX, AVX2")
    void verifySimdOutlinesAndAvxVexNotes() {
        String[] laterSimd = {"ADDSUBPS", "HADDPS", "PSHUFB", "DPPS", "ROUNDPS", "PCMPESTRI", "CRC32", "VADDPS", "VMULPS", "VMOVUPS", "VZEROUPPER", "VPADDD"};
        for (String mnemonic : laterSimd) {
            Optional<InstructionEntry> instrOpt = corpus.findInstruction(mnemonic);
            assertTrue(instrOpt.isPresent(), "Instruction outline " + mnemonic + " must be present in corpus");
            InstructionEntry instr = instrOpt.get();
            assertEquals("F-18", instr.family());
        }

        // Verify AVX VEX notes and 3-operand syntax
        InstructionEntry vaddps = corpus.findInstruction("VADDPS").orElseThrow();
        assertTrue(vaddps.descriptionEs().contains("VEX") || vaddps.descriptionEs().contains("YMM") || vaddps.descriptionEs().contains("3 operandos"),
                "VADDPS description must mention VEX prefix, YMM registers or 3-operand non-destructive syntax");
        assertTrue(vaddps.descriptionEn().contains("VEX") || vaddps.descriptionEn().contains("YMM") || vaddps.descriptionEn().contains("3-operand"),
                "VADDPS description EN must mention VEX prefix, YMM registers or 3-operand non-destructive syntax");

        InstructionEntry vzeroupper = corpus.findInstruction("VZEROUPPER").orElseThrow();
        assertTrue(vzeroupper.descriptionEs().contains("AVX") || vzeroupper.descriptionEs().contains("SSE") || vzeroupper.descriptionEs().contains("YMM"),
                "VZEROUPPER description must mention transition between AVX and SSE or clearing upper YMM");
    }

    @Test
    @DisplayName("AA-P5-06: Undocumented instructions SALC (D6) and ICEBP (F1) marked with SDM and Undocumented PC sources")
    void verifyUndocumentedInstructions() {
        // SALC
        Optional<InstructionEntry> salcOpt = corpus.findInstruction("SALC");
        assertTrue(salcOpt.isPresent(), "SALC must be present in corpus");
        InstructionEntry salc = salcOpt.get();
        assertEquals("F-16", salc.family());
        assertEquals(PedagogicalLevel.ADVANCED, salc.pedagogicalLevel());
        assertTrue(salc.sources().stream().anyMatch(s -> s.contains("intel-sdm") || s.contains("opcode-D6")),
                "SALC sources must cite Intel SDM opcode D6");
        assertTrue(salc.sources().stream().anyMatch(s -> s.contains("undocumented")),
                "SALC sources must cite Undocumented PC / undocumented references");
        assertTrue(salc.descriptionEs().toLowerCase().contains("no documentada") || salc.descriptionEs().toLowerCase().contains("indocumentada"),
                "SALC description ES must note it is undocumented");
        assertTrue(salc.descriptionEn().toLowerCase().contains("undocumented"),
                "SALC description EN must note it is undocumented");

        // ICEBP
        Optional<InstructionEntry> icebpOpt = corpus.findInstruction("ICEBP");
        assertTrue(icebpOpt.isPresent(), "ICEBP must be present in corpus");
        InstructionEntry icebp = icebpOpt.get();
        assertEquals("F-16", icebp.family());
        assertEquals(PedagogicalLevel.ADVANCED, icebp.pedagogicalLevel());
        assertTrue(icebp.sources().stream().anyMatch(s -> s.contains("opcode-F1") || s.contains("intel-sdm")),
                "ICEBP sources must cite opcode F1");
        assertTrue(icebp.sources().stream().anyMatch(s -> s.contains("undocumented")),
                "ICEBP sources must cite Undocumented PC");
        assertTrue(icebp.descriptionEs().toLowerCase().contains("no documentada") || icebp.descriptionEs().toLowerCase().contains("indocumentada"),
                "ICEBP description ES must note it is undocumented");
    }

    @Test
    @DisplayName("AA-P5-06: System opcodes (LGDT..RDTSCP) present in Family F-16")
    void verifySystemOpcodes() {
        String[] f16SystemMnemonics = {
                "LGDT", "LIDT", "LLDT", "LTR", "SGDT", "SIDT", "SLDT", "STR",
                "INVD", "WBINVD", "INVLPG",
                "SYSENTER", "SYSEXIT", "SYSCALL", "SYSRET",
                "RDTSCP"
        };

        for (String mnemonic : f16SystemMnemonics) {
            Optional<InstructionEntry> instrOpt = corpus.findInstruction(mnemonic);
            assertTrue(instrOpt.isPresent(), "System instruction " + mnemonic + " must be present in corpus");
            InstructionEntry instr = instrOpt.get();
            assertEquals("F-16", instr.family(), mnemonic + " must belong to family F-16");
            assertNotNull(instr.descriptionEs());
            assertNotNull(instr.descriptionEn());
        }

        // CPUID and RDTSC are processor identification/timing in Family F-14
        assertTrue(corpus.findInstruction("CPUID").isPresent(), "CPUID must be present in corpus");
        assertEquals("F-14", corpus.findInstruction("CPUID").get().family());
        assertTrue(corpus.findInstruction("RDTSC").isPresent(), "RDTSC must be present in corpus");
        assertEquals("F-14", corpus.findInstruction("RDTSC").get().family());
    }

    @Test
    @DisplayName("AA-P5-06: MOVABS cataloged as GNU as / AT&T syntax instruction")
    void verifyMovabsCatalogedAsGas() {
        Optional<InstructionEntry> movabsOpt = corpus.findInstruction("MOVABS");
        assertTrue(movabsOpt.isPresent(), "MOVABS must be present in corpus");
        InstructionEntry movabs = movabsOpt.get();

        // Check description explicitly identifies it as GNU as (GAS) / AT&T pseudo-mnemonic
        String descEs = movabs.descriptionEs();
        String descEn = movabs.descriptionEn();
        assertTrue(descEs.contains("GNU as") || descEs.contains("GAS") || descEs.contains("AT&T"),
                "MOVABS description ES must identify it as GNU as / GAS / AT&T syntax");
        assertTrue(descEn.contains("GNU as") || descEn.contains("GAS") || descEn.contains("AT&T"),
                "MOVABS description EN must identify it as GNU as / GAS / AT&T syntax");
    }
}
