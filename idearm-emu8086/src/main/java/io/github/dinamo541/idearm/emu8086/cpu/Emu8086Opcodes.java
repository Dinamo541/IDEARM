package io.github.dinamo541.idearm.emu8086.cpu;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Authoritative opcode inventory of the built-in 8086 CPU emulator (Finding A-25).
 * Declares the exact set of mnemonics executed by {@link Cpu8086}.
 */
public final class Emu8086Opcodes {

    private static final Set<String> IMPLEMENTED_MNEMONICS = Set.of(
            // Data Transfer
            "MOV", "XCHG", "LEA", "LDS", "LES", "XLAT", "XLATB",
            // Arithmetic
            "ADD", "ADC", "SUB", "SBB", "INC", "DEC", "NEG", "CMP",
            "MUL", "IMUL", "DIV", "IDIV", "CBW", "CWD",
            "DAA", "DAS", "AAA", "AAS", "AAM", "AAD",
            // Logic & Shifts
            "AND", "OR", "XOR", "NOT", "TEST",
            "SHL", "SAL", "SHR", "SAR", "ROL", "ROR", "RCL", "RCR",
            // Control & Jumps
            "JMP", "CALL", "RET", "RETF", "RETN",
            "JA", "JAE", "JB", "JBE", "JC", "JCXZ", "JE", "JG", "JGE", "JL", "JLE",
            "JNA", "JNAE", "JNB", "JNBE", "JNC", "JNE", "JNG", "JNGE", "JNL", "JNLE",
            "JNO", "JNP", "JNS", "JNZ", "JO", "JP", "JPE", "JPO", "JS", "JZ",
            "LOOP", "LOOPE", "LOOPZ", "LOOPNE", "LOOPNZ",
            // Strings
            "MOVSB", "MOVSW", "CMPSB", "CMPSW", "SCASB", "SCASW", "LODSB", "LODSW", "STOSB", "STOSW",
            // Stack & Procedures
            "PUSH", "POP", "PUSHF", "POPF",
            // System & Interrupts
            "INT", "INTO", "IRET",
            // Flags & CPU control
            "CLC", "STC", "CMC", "CLD", "STD", "CLI", "STI", "LAHF", "SAHF", "NOP", "HLT"
    );

    private static final Set<String> UNIMPLEMENTED_MNEMONICS_186 = Set.of(
            "PUSHA", "POPA", "PUSHAD", "POPAD",
            "ENTER", "LEAVE",
            "BOUND",
            "INS", "INSB", "INSW", "OUTS", "OUTSB", "OUTSW"
    );

    private Emu8086Opcodes() {
    }

    /**
     * Returns the unmodifiable set of canonical uppercase mnemonics implemented by {@link Cpu8086}.
     */
    public static Set<String> implementedMnemonics() {
        return Collections.unmodifiableSet(IMPLEMENTED_MNEMONICS);
    }

    /**
     * Checks if a mnemonic is supported by the built-in emulator.
     */
    public static boolean isImplemented(String mnemonic) {
        if (mnemonic == null || mnemonic.isBlank()) return false;
        return IMPLEMENTED_MNEMONICS.contains(mnemonic.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Checks if a mnemonic is explicitly an 80186+ instruction missing from {@link Cpu8086}.
     */
    public static boolean isUnimplemented186(String mnemonic) {
        if (mnemonic == null || mnemonic.isBlank()) return false;
        return UNIMPLEMENTED_MNEMONICS_186.contains(mnemonic.trim().toUpperCase(Locale.ROOT));
    }
}
