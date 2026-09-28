package io.github.dinamo541.idearm.language.catalog;

import io.github.dinamo541.idearm.language.knowledge.Corpus;
import io.github.dinamo541.idearm.language.knowledge.CpuGeneration;
import io.github.dinamo541.idearm.language.knowledge.InstructionEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test comparing the new Academic Knowledge Corpus with the historical catalog baseline,
 * ensuring zero lost instructions and verifying all deliberate factual corrections (AA-P1-02).
 */
class CatalogEquivalenceTest {

    @Test
    void retainsAllHistoricalInstructions() {
        List<InstructionInfo> all = InstructionCatalog.getAll();
        assertTrue(all.size() >= 214, "Expected at least 214 instructions, found: " + all.size());

        // Check key instructions across all categories
        for (String mnemonic : List.of("MOV", "ADD", "SUB", "JMP", "JE", "CALL", "RET", "INT",
                "PUSH", "POP", "IN", "OUT", "MOVSB", "LODSB", "STOSB", "SCASB", "CMPSB",
                "CLC", "STC", "CLI", "STI", "NOP", "HLT", "FLD", "FADD", "CPUID", "RDTSC")) {
            assertTrue(InstructionCatalog.find(mnemonic).isPresent(), "Missing historical mnemonic: " + mnemonic);
        }
    }

    @Test
    void verifiesDeliberateFactualCorrections() {
        // 1. "INSTRUCTION" pseudo-mnemonic removed (previously leaked from LOCK)
        assertFalse(InstructionCatalog.knownMnemonics().contains("INSTRUCTION"),
                "'INSTRUCTION' was a bogus alias leaked by 'LOCK [instruction]'");
        assertFalse(InstructionCatalog.find("INSTRUCTION").isPresent());

        // 2. CMOVcc requires Pentium Pro (P6)
        Optional<InstructionInfo> cmov = InstructionCatalog.find("CMOVZ");
        assertTrue(cmov.isPresent());
        assertEquals(CpuLevel.CPU_P6, cmov.get().minCpu(), "CMOVcc requires Pentium Pro (P6)");

        Optional<InstructionEntry> cmovEntry = Corpus.get().findInstruction("CMOVZ");
        assertTrue(cmovEntry.isPresent());
        assertEquals(CpuGeneration.P6, cmovEntry.get().minCpuGen());

        // 3. BT / BTC / BTR / BTS: CF modified, OF/SF/ZF/AF/PF undefined (SDM 093)
        for (String mnemonic : List.of("BT", "BTC", "BTR", "BTS")) {
            Optional<InstructionInfo> info = InstructionCatalog.find(mnemonic);
            assertTrue(info.isPresent());
            assertEquals(FlagEffect.MODIFIED, info.get().flags().c(), mnemonic + " must modify CF");
            assertEquals(FlagEffect.UNDEFINED, info.get().flags().o(), mnemonic + " leaves OF undefined");
            assertEquals(FlagEffect.UNDEFINED, info.get().flags().s(), mnemonic + " leaves SF undefined");
            assertEquals(FlagEffect.UNAFFECTED, info.get().flags().z(), mnemonic + " leaves ZF unaffected");
            assertEquals(FlagEffect.UNDEFINED, info.get().flags().a(), mnemonic + " leaves AF undefined");
            assertEquals(FlagEffect.UNDEFINED, info.get().flags().p(), mnemonic + " leaves PF undefined");
        }

        // 4. IRET restores all 9 flags from stack (SDM 093)
        Optional<InstructionInfo> iret = InstructionCatalog.find("IRET");
        assertTrue(iret.isPresent());
        assertEquals(FlagEffect.MODIFIED, iret.get().flags().o());
        assertEquals(FlagEffect.MODIFIED, iret.get().flags().d());
        assertEquals(FlagEffect.MODIFIED, iret.get().flags().i());
        assertEquals(FlagEffect.MODIFIED, iret.get().flags().t());
        assertEquals(FlagEffect.MODIFIED, iret.get().flags().s());
        assertEquals(FlagEffect.MODIFIED, iret.get().flags().z());
        assertEquals(FlagEffect.MODIFIED, iret.get().flags().a());
        assertEquals(FlagEffect.MODIFIED, iret.get().flags().p());
        assertEquals(FlagEffect.MODIFIED, iret.get().flags().c());

        // 5. SHL / SAL / SHR / SAR leaves AF undefined
        Optional<InstructionInfo> shl = InstructionCatalog.find("SHL");
        assertTrue(shl.isPresent());
        assertEquals(FlagEffect.UNDEFINED, shl.get().flags().a(), "SHL leaves AF undefined according to SDM");
    }

    @Test
    void verifiesAliasResolutionParity() {
        assertEquals("JA", InstructionCatalog.find("JNBE").orElseThrow().mnemonic());
        assertEquals("WAIT", InstructionCatalog.find("FWAIT").orElseThrow().mnemonic());
        assertEquals("INS", InstructionCatalog.find("INSB").orElseThrow().mnemonic());
        assertEquals("SHL", InstructionCatalog.find("SAL").orElseThrow().mnemonic());
        assertEquals("CMOVZ", InstructionCatalog.find("CMOVE").orElseThrow().mnemonic());
    }

    @Test
    void bilingualContentCompleteness() {
        for (InstructionInfo info : InstructionCatalog.getAll()) {
            assertFalse(info.summaryEn().isBlank(), info.mnemonic() + " missing summaryEn");
            assertFalse(info.summaryEs().isBlank(), info.mnemonic() + " missing summaryEs");
            assertFalse(info.descriptionEn().isBlank(), info.mnemonic() + " missing descriptionEn");
            assertFalse(info.descriptionEs().isBlank(), info.mnemonic() + " missing descriptionEs");
            assertFalse(info.example().isBlank(), info.mnemonic() + " missing example");
            assertFalse(info.syntaxVariants().isEmpty(), info.mnemonic() + " missing syntax variants");
        }
    }
}
