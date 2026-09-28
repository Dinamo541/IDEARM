package io.github.dinamo541.idearm.language.knowledge;

import io.github.dinamo541.idearm.language.lexer.AssemblyLexer;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegisterCatalogTest {

    @Test
    void allAssemblyLexerRegistersExistInCorpus() {
        Corpus corpus = Corpus.get();
        Set<String> corpusRegs = corpus.getAllRegisterNames();
        assertFalse(corpusRegs.isEmpty(), "Corpus must have loaded registers from registers.json");

        // Verify every register in AssemblyLexer.REGISTERS is recognized by the corpus
        for (String lexerReg : AssemblyLexer.REGISTERS) {
            assertTrue(corpus.findRegister(lexerReg).isPresent() || corpusRegs.contains(lexerReg),
                    "Register " + lexerReg + " from AssemblyLexer must exist in the Corpus catalog");
        }
    }

    @Test
    void registersDefineViewsAndParents() {
        Corpus corpus = Corpus.get();

        // RAX: 64-bit parent with EAX, AX, AL, AH views
        Optional<RegisterEntry> rax = corpus.findRegister("RAX");
        assertTrue(rax.isPresent());
        assertEquals(64, rax.get().sizeBits());
        assertEquals(RegisterGroup.G1_GENERAL, rax.get().group());
        assertTrue(rax.get().views().stream().anyMatch(v -> "EAX".equals(v.name())));
        assertTrue(rax.get().views().stream().anyMatch(v -> "AX".equals(v.name())));
        assertTrue(rax.get().views().stream().anyMatch(v -> "AL".equals(v.name())));
        assertTrue(rax.get().views().stream().anyMatch(v -> "AH".equals(v.name())));

        // EAX: 32-bit with parent RAX
        Optional<RegisterEntry> eax = corpus.findRegister("EAX");
        assertTrue(eax.isPresent());
        assertEquals("x86.reg.rax", eax.get().parentId());

        // Segment registers FS and GS
        Optional<RegisterEntry> fs = corpus.findRegister("FS");
        assertTrue(fs.isPresent());
        assertEquals(RegisterGroup.G3_SEGMENT, fs.get().group());
        assertEquals(CpuGeneration.I80386, fs.get().requirement().minGeneration());

        Optional<RegisterEntry> gs = corpus.findRegister("GS");
        assertTrue(gs.isPresent());
        assertEquals(RegisterGroup.G3_SEGMENT, gs.get().group());
        assertEquals(CpuGeneration.I80386, gs.get().requirement().minGeneration());
    }

    @Test
    void x87AndSimdRegistersArePresent() {
        Corpus corpus = Corpus.get();

        Optional<RegisterEntry> st0 = corpus.findRegister("ST0");
        assertTrue(st0.isPresent());
        assertEquals(RegisterGroup.G5_X87_SIMD, st0.get().group());

        Optional<RegisterEntry> xmm0 = corpus.findRegister("XMM0");
        assertTrue(xmm0.isPresent());
        assertEquals(RegisterGroup.G5_X87_SIMD, xmm0.get().group());
        assertEquals(128, xmm0.get().sizeBits());
    }

    @Test
    void systemRegistersArePresent() {
        Corpus corpus = Corpus.get();

        Optional<RegisterEntry> cr0 = corpus.findRegister("CR0");
        assertTrue(cr0.isPresent());
        assertEquals(RegisterGroup.G6_SYSTEM, cr0.get().group());
        assertEquals(Privilege.CPL0, cr0.get().requirement().privilege());
    }
}
