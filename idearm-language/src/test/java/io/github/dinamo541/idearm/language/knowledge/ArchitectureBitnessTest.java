package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureBitnessTest {

    private static Corpus corpus;

    @BeforeAll
    static void setUp() {
        corpus = Corpus.get();
        assertNotNull(corpus);
    }

    @Test
    void testUniversalInstructionMov() {
        InstructionEntry mov = corpus.findInstruction("MOV").orElseThrow();
        assertTrue(ArchitectureBitness.ALL.supportsInstruction(mov));
        assertTrue(ArchitectureBitness.BITS_16.supportsInstruction(mov));
        assertTrue(ArchitectureBitness.BITS_32.supportsInstruction(mov));
        assertTrue(ArchitectureBitness.BITS_64.supportsInstruction(mov));
    }

    @Test
    void testBcdInstructionsExcludedFrom64Bit() {
        InstructionEntry aaa = corpus.findInstruction("AAA").orElseThrow();
        assertTrue(ArchitectureBitness.BITS_16.supportsInstruction(aaa), "AAA runs in 16-bit");
        assertTrue(ArchitectureBitness.BITS_32.supportsInstruction(aaa), "AAA runs in 32-bit");
        assertFalse(ArchitectureBitness.BITS_64.supportsInstruction(aaa), "AAA is INVALID in 64-bit Long Mode");

        InstructionEntry daa = corpus.findInstruction("DAA").orElseThrow();
        assertTrue(ArchitectureBitness.BITS_16.supportsInstruction(daa));
        assertTrue(ArchitectureBitness.BITS_32.supportsInstruction(daa));
        assertFalse(ArchitectureBitness.BITS_64.supportsInstruction(daa));
    }

    @Test
    void testPushaExcludedFrom64Bit() {
        InstructionEntry pusha = corpus.findInstruction("PUSHA").orElseThrow();
        assertTrue(ArchitectureBitness.BITS_16.supportsInstruction(pusha), "PUSHA was introduced in 80186 (16-bit)");
        assertTrue(ArchitectureBitness.BITS_32.supportsInstruction(pusha), "PUSHA works in 32-bit");
        assertFalse(ArchitectureBitness.BITS_64.supportsInstruction(pusha), "PUSHA is INVALID in 64-bit Long Mode");
    }

    @Test
    void test32BitIntroducedInstructionsExcludedFrom16Bit() {
        InstructionEntry bsf = corpus.findInstruction("BSF").orElseThrow();
        assertFalse(ArchitectureBitness.BITS_16.supportsInstruction(bsf), "BSF requires 80386+");
        assertTrue(ArchitectureBitness.BITS_32.supportsInstruction(bsf), "BSF runs in 32-bit");
        assertTrue(ArchitectureBitness.BITS_64.supportsInstruction(bsf), "BSF runs in 64-bit");
    }

    @Test
    void test64BitIntroducedInstructionsExcludedFrom16And32Bit() {
        InstructionEntry cqo = corpus.findInstruction("CQO").orElseThrow();
        assertFalse(ArchitectureBitness.BITS_16.supportsInstruction(cqo), "CQO is 64-bit only");
        assertFalse(ArchitectureBitness.BITS_32.supportsInstruction(cqo), "CQO is 64-bit only");
        assertTrue(ArchitectureBitness.BITS_64.supportsInstruction(cqo), "CQO runs in 64-bit");

        InstructionEntry syscall = corpus.findInstruction("SYSCALL").orElseThrow();
        assertFalse(ArchitectureBitness.BITS_16.supportsInstruction(syscall));
        assertFalse(ArchitectureBitness.BITS_32.supportsInstruction(syscall));
        assertTrue(ArchitectureBitness.BITS_64.supportsInstruction(syscall));
    }

    @Test
    void testRegisterBitnessFiltering() {
        RegisterEntry ax = corpus.findRegister("AX").orElseThrow();
        assertTrue(ArchitectureBitness.BITS_16.supportsRegister(ax));
        assertFalse(ArchitectureBitness.BITS_32.supportsRegister(ax));
        assertFalse(ArchitectureBitness.BITS_64.supportsRegister(ax));

        RegisterEntry eax = corpus.findRegister("EAX").orElseThrow();
        assertFalse(ArchitectureBitness.BITS_16.supportsRegister(eax));
        assertTrue(ArchitectureBitness.BITS_32.supportsRegister(eax));
        assertFalse(ArchitectureBitness.BITS_64.supportsRegister(eax));

        RegisterEntry rax = corpus.findRegister("RAX").orElseThrow();
        assertFalse(ArchitectureBitness.BITS_16.supportsRegister(rax));
        assertFalse(ArchitectureBitness.BITS_32.supportsRegister(rax));
        assertTrue(ArchitectureBitness.BITS_64.supportsRegister(rax));
    }

    @Test
    void testFromBits() {
        assertEquals(ArchitectureBitness.BITS_16, ArchitectureBitness.fromBits(16));
        assertEquals(ArchitectureBitness.BITS_32, ArchitectureBitness.fromBits(32));
        assertEquals(ArchitectureBitness.BITS_64, ArchitectureBitness.fromBits(64));
        assertEquals(ArchitectureBitness.ALL, ArchitectureBitness.fromBits(0));
        assertEquals(ArchitectureBitness.ALL, ArchitectureBitness.fromBits(128));
    }
}
