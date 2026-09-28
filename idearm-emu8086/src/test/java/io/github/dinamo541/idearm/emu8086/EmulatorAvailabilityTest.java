package io.github.dinamo541.idearm.emu8086;

import io.github.dinamo541.idearm.emu8086.cpu.Emu8086Opcodes;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmulatorAvailabilityTest {

    @Test
    void testCore8086MnemonicsImplemented() {
        Set<String> core = Set.of(
                "MOV", "XCHG", "LEA", "LDS", "LES", "XLAT",
                "ADD", "ADC", "SUB", "SBB", "INC", "DEC", "NEG", "CMP",
                "MUL", "IMUL", "DIV", "IDIV",
                "AND", "OR", "XOR", "NOT", "TEST",
                "SHL", "SHR", "ROL", "ROR",
                "JMP", "CALL", "RET", "JE", "JNE", "LOOP",
                "PUSH", "POP", "INT", "IRET", "NOP", "HLT"
        );
        for (String m : core) {
            assertTrue(Emu8086Opcodes.isImplemented(m), "Expected " + m + " to be implemented");
            assertTrue(Emu8086Opcodes.isImplemented(m.toLowerCase()), "Expected case-insensitivity for " + m);
        }
    }

    @Test
    void testUnimplemented186Mnemonics() {
        Set<String> unimpl = Set.of(
                "PUSHA", "POPA", "PUSHAD", "POPAD",
                "ENTER", "LEAVE", "BOUND", "INS", "OUTS"
        );
        for (String m : unimpl) {
            assertTrue(Emu8086Opcodes.isUnimplemented186(m), "Expected " + m + " to be identified as unimplemented 186");
            assertFalse(Emu8086Opcodes.isImplemented(m), "Expected " + m + " to NOT be implemented in 8086 emu");
        }
    }

    @Test
    void testImplementedAndUnimplementedAreDisjoint() {
        for (String m : Emu8086Opcodes.implementedMnemonics()) {
            assertFalse(Emu8086Opcodes.isUnimplemented186(m), "Mnemonic " + m + " should not be both implemented and unimplemented186");
        }
    }

    @Test
    void testNullOrBlankHandling() {
        assertFalse(Emu8086Opcodes.isImplemented(null));
        assertFalse(Emu8086Opcodes.isImplemented("   "));
        assertFalse(Emu8086Opcodes.isUnimplemented186(null));
        assertFalse(Emu8086Opcodes.isUnimplemented186(""));
    }
}
