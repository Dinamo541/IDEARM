package io.github.dinamo541.idearm.language.knowledge;

import io.github.dinamo541.idearm.language.catalog.FlagEffect;
import io.github.dinamo541.idearm.language.catalog.InstructionCategory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CorpusLoaderTest {

    @Test
    void loadsAllPrimaryInstructions() {
        Corpus corpus = Corpus.get();
        assertEquals(298, corpus.getAllInstructions().size(), "Expected 298 primary instructions");
        assertTrue(corpus.isKnownInstruction("MOV"));
        assertTrue(corpus.isKnownInstruction("IMUL"));
        assertTrue(corpus.isKnownInstruction("ADD"));
    }

    @Test
    void explicitAliasesResolveCorrectlyWithoutInstructionArtifact() {
        Corpus corpus = Corpus.get();
        // Finding A-01: INSTRUCTION must NOT resolve to LOCK
        assertFalse(corpus.findInstruction("INSTRUCTION").isPresent(), "INSTRUCTION must not be recognized as an instruction");

        // Legitimate aliases resolve
        Optional<InstructionEntry> sal = corpus.findInstruction("SAL");
        assertTrue(sal.isPresent());
        assertEquals("SHL", sal.get().mnemonic());

        Optional<InstructionEntry> jnbe = corpus.findInstruction("JNBE");
        assertTrue(jnbe.isPresent());
        assertEquals("JA", jnbe.get().mnemonic());

        Optional<InstructionEntry> fwait = corpus.findInstruction("FWAIT");
        assertTrue(fwait.isPresent());
        assertEquals("WAIT", fwait.get().mnemonic());

        Optional<InstructionEntry> xlatb = corpus.findInstruction("XLATB");
        assertTrue(xlatb.isPresent());
        assertEquals("XLAT", xlatb.get().mnemonic());
    }

    @Test
    void flagCorrectionsAreAccurateToSDM093() {
        Corpus corpus = Corpus.get();

        // Finding A-06: BT/BTS/BTR/BTC sets CF, leaves others undefined
        InstructionEntry bt = corpus.findInstruction("BT").orElseThrow();
        assertEquals(FlagEffect.MODIFIED, bt.flagSummary().c(), "BT must modify CF");
        assertEquals(FlagEffect.UNDEFINED, bt.flagSummary().o(), "BT leaves OF undefined");
        assertEquals(FlagEffect.UNDEFINED, bt.flagSummary().s(), "BT leaves SF undefined");
        assertEquals(FlagEffect.UNDEFINED, bt.flagSummary().a(), "BT leaves AF undefined");
        assertEquals(FlagEffect.UNDEFINED, bt.flagSummary().p(), "BT leaves PF undefined");
        assertEquals(FlagEffect.UNAFFECTED, bt.flagSummary().z(), "BT leaves ZF unaffected");

        // Finding A-07: IRET modifies all flags
        InstructionEntry iret = corpus.findInstruction("IRET").orElseThrow();
        assertEquals(FlagEffect.MODIFIED, iret.flagSummary().d(), "IRET restores DF");
        assertEquals(FlagEffect.MODIFIED, iret.flagSummary().i(), "IRET restores IF");
        assertEquals(FlagEffect.MODIFIED, iret.flagSummary().t(), "IRET restores TF");
        assertEquals(FlagEffect.MODIFIED, iret.flagSummary().c(), "IRET restores CF");

        // Finding A-11: Shifts leave AF undefined for non-zero count
        InstructionEntry shl = corpus.findInstruction("SHL").orElseThrow();
        assertEquals(FlagEffect.UNDEFINED, shl.flagSummary().a(), "SHL leaves AF undefined");
        assertEquals(FlagEffect.MODIFIED, shl.flagSummary().c(), "SHL modifies CF");
    }

    @Test
    void cpuLevelCorrectionsAreAccurate() {
        Corpus corpus = Corpus.get();

        // Finding A-03: CMOVcc requires P6 (Pentium Pro)
        InstructionEntry cmova = corpus.findInstruction("CMOVA").orElseThrow();
        assertEquals(CpuGeneration.P6, cmova.minCpuGen(), "CMOVA requires P6 generation");

        InstructionEntry cmovz = corpus.findInstruction("CMOVZ").orElseThrow();
        assertEquals(CpuGeneration.P6, cmovz.minCpuGen(), "CMOVZ requires P6 generation");
    }

    @Test
    void performanceOfCorpusLoadingIsWithinLimit() {
        long start = System.nanoTime();
        Corpus corpus = Corpus.get();
        assertNotNull(corpus);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("Corpus access time: " + elapsedMs + " ms");
        assertTrue(elapsedMs < 150, "Corpus access must be under 150 ms");
    }
}
