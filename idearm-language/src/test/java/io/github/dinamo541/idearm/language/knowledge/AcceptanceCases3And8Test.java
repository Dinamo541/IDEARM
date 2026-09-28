package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates Acceptance Case 3 (@data in MASM/TASM vs NASM, why 2 instructions)
 * and Acceptance Case 8 (Dynamic compatibility resolution across DOS 8086, Win32, Win64, Linux64).
 */
@DisplayName("Acceptance Cases 3 & 8: @data Two-Step Loading and Dynamic Project Target Switching")
class AcceptanceCases3And8Test {

    private static Corpus corpus;

    @BeforeAll
    static void setUp() {
        corpus = Corpus.get();
        assertNotNull(corpus, "Corpus must not be null");
    }

    @Test
    @DisplayName("Acceptance Case 3: @data predefined symbol, why 2 instructions, offered in MASM/TASM but NOT in NASM")
    void testAcceptanceCase3AtData() {
        Optional<SyntaxItem> atDataOpt = corpus.findSyntaxItem("@DATA");
        assertTrue(atDataOpt.isPresent(), "Syntax item @DATA must be present in knowledge base");

        SyntaxItem atData = atDataOpt.get();
        assertEquals(SyntaxClass.PREDEFINED_SYMBOL, atData.syntaxClass(),
                "@data must be classified as PREDEFINED_SYMBOL");

        // Dialects: Supported in MASM and TASM, but NOT in NASM
        assertTrue(atData.supportsDialect(Dialect.MASM), "@data must support MASM");
        assertTrue(atData.supportsDialect(Dialect.TASM), "@data must support TASM");
        assertFalse(atData.supportsDialect(Dialect.NASM), "@data must NOT be offered in NASM (NASM uses raw section labels)");

        // Explanations: Must explain why 2 instructions are required (no immediate to segment register in x86)
        String descEs = atData.descriptionEs().toLowerCase();
        String descEn = atData.descriptionEn().toLowerCase();

        assertTrue(descEs.contains("segmento") && descEs.contains("inmediato"),
                "Spanish description must explain that x86 cannot load an immediate directly into a segment register");
        assertTrue(descEn.contains("segment") && descEn.contains("immediate"),
                "English description must explain that x86 cannot load an immediate directly into a segment register");

        assertTrue(descEs.contains("mov ax, @data") || descEs.contains("ax"),
                "Spanish description must show intermediate general register step");
        assertTrue(descEn.contains("mov ax, @data") || descEn.contains("ax"),
                "English description must show intermediate general register step");
    }

    @Test
    @DisplayName("Acceptance Case 8: Dynamic project profile switching (DOS 8086, Win32, Win64, Linux64)")
    void testAcceptanceCase8ProjectTargetSwitching() {
        // Target 1: dos-exe-16 (8086 Real Mode, MASM)
        CompatibilityContext dos8086 = CompatibilityContext.of(
                CpuGeneration.I8086,
                ProcessorMode.REAL,
                Set.of(),
                Dialect.MASM,
                "emu8086"
        );

        // Target 2: win-pe32-console (Pentium / P6, Protected Mode 32-bit, MASM)
        CompatibilityContext win32 = CompatibilityContext.of(
                CpuGeneration.P6,
                ProcessorMode.PROTECTED_32,
                Set.of(),
                Dialect.MASM,
                "external"
        );

        // Target 3: win-pe64-console / linux-elf64-console (x86-64, Long Mode 64-bit, NASM)
        CompatibilityContext long64 = CompatibilityContext.of(
                CpuGeneration.X86_64,
                ProcessorMode.LONG,
                Set.of(),
                Dialect.NASM,
                "gdb"
        );

        InstructionEntry mov = corpus.findInstruction("MOV").orElseThrow();
        InstructionEntry pusha = corpus.findInstruction("PUSHA").orElseThrow();
        InstructionEntry bswap = corpus.findInstruction("BSWAP").orElseThrow();
        InstructionEntry syscall = corpus.findInstruction("SYSCALL").orElseThrow();

        // Under DOS 8086:
        assertEquals(Availability.AVAILABLE, CompatibilityResolver.resolve(mov, dos8086).status());
        assertEquals(Availability.UNAVAILABLE, CompatibilityResolver.resolve(pusha, dos8086).status(),
                "PUSHA must be unavailable on 8086 (requires 80186)");
        assertEquals(Availability.UNAVAILABLE, CompatibilityResolver.resolve(bswap, dos8086).status(),
                "BSWAP must be unavailable on 8086 (requires 80486)");
        assertEquals(Availability.UNAVAILABLE, CompatibilityResolver.resolve(syscall, dos8086).status(),
                "SYSCALL must be unavailable on 8086");

        // Switch to Win32 (32-bit protected):
        assertEquals(Availability.AVAILABLE, CompatibilityResolver.resolve(mov, win32).status());
        assertEquals(Availability.AVAILABLE, CompatibilityResolver.resolve(pusha, win32).status(),
                "PUSHA is valid in 32-bit protected mode");
        assertEquals(Availability.AVAILABLE, CompatibilityResolver.resolve(bswap, win32).status(),
                "BSWAP is valid in 32-bit protected mode");
        assertEquals(Availability.UNAVAILABLE, CompatibilityResolver.resolve(syscall, win32).status(),
                "SYSCALL is unavailable on 32-bit protected mode");

        // Switch to Long Mode 64-bit:
        assertEquals(Availability.AVAILABLE, CompatibilityResolver.resolve(mov, long64).status());
        assertEquals(Availability.AVAILABLE, CompatibilityResolver.resolve(bswap, long64).status());
        assertEquals(Availability.AVAILABLE, CompatibilityResolver.resolve(syscall, long64).status(),
                "SYSCALL is available in 64-bit long mode");
        assertEquals(Availability.UNAVAILABLE, CompatibilityResolver.resolve(pusha, long64).status(),
                "PUSHA is invalid and excluded in 64-bit long mode");

        // Verify all 15 invalid instructions in 64-bit long mode
        List<String> invalidInLongMode = List.of(
                "AAA", "AAD", "AAM", "AAS",
                "DAA", "DAS",
                "PUSHA", "POPA",
                "INTO", "BOUND",
                "LDS", "LES",
                "ARPL"
        );
        for (String mnemonic : invalidInLongMode) {
            InstructionEntry entry = corpus.findInstruction(mnemonic).orElseThrow();
            CompatibilityResult res = CompatibilityResolver.resolve(entry, long64);
            assertEquals(Availability.UNAVAILABLE, res.status(),
                    mnemonic + " must be UNAVAILABLE in 64-bit long mode");
        }
    }
}
