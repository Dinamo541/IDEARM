package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityMatrixTest {

    @Test
    void cpu8086ExcludesAdvancedGenerations() {
        Corpus corpus = Corpus.get();
        CompatibilityContext ctx8086 = CompatibilityContext.of(
                CpuGeneration.I8086,
                ProcessorMode.REAL,
                Set.of(),
                Dialect.MASM,
                "emu8086"
        );

        InstructionEntry mov = corpus.findInstruction("MOV").orElseThrow();
        assertEquals(Availability.AVAILABLE, CompatibilityResolver.resolve(mov, ctx8086).status());

        InstructionEntry pusha = corpus.findInstruction("PUSHA").orElseThrow();
        assertEquals(Availability.UNAVAILABLE, CompatibilityResolver.resolve(pusha, ctx8086).status());
    }

    @Test
    void longModeExcludesAllFifteenInvalidInstructions() {
        Corpus corpus = Corpus.get();
        CompatibilityContext ctxLong = CompatibilityContext.of(
                CpuGeneration.X86_64,
                ProcessorMode.LONG,
                Set.of(),
                Dialect.NASM,
                "gdb"
        );

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
            CompatibilityResult result = CompatibilityResolver.resolve(entry, ctxLong);
            assertEquals(Availability.UNAVAILABLE, result.status(),
                    mnemonic + " must be UNAVAILABLE in 64-bit Long Mode");
            assertTrue(result.reason() != null && result.reason().toLowerCase().contains("modo"),
                    mnemonic + " unavailable reason should mention mode");
        }
    }

    @Test
    void unknownCpuYieldsUnknownAvailabilityWithoutFalseDiagnostics() {
        Corpus corpus = Corpus.get();
        CompatibilityContext ctxUnknown = CompatibilityContext.of(
                CpuGeneration.UNKNOWN,
                ProcessorMode.UNKNOWN,
                null, // unspecified features
                Dialect.COMMON,
                "none"
        );

        InstructionEntry bswap = corpus.findInstruction("BSWAP").orElseThrow();
        CompatibilityResult result = CompatibilityResolver.resolve(bswap, ctxUnknown);
        assertEquals(Availability.UNKNOWN, result.status(),
                "BSWAP on unknown CPU generation must resolve to UNKNOWN, not UNAVAILABLE");

        // getForCpu with UNKNOWN or "x86" returns all instructions instead of degrading to 8086
        List<InstructionEntry> list = corpus.getForCpu("x86");
        assertEquals(corpus.getAllInstructions().size(), list.size(),
                "Querying cpu='x86' must return all instructions, not 127");
    }

    @Test
    void unspecifiedFeaturesYieldUnknownState() {
        InstructionForm formWithSse2 = new InstructionForm(
                "test.form",
                List.of(SyntaxForm.of(Dialect.COMMON, "test")),
                List.of(),
                List.of(),
                new Requirement(CpuGeneration.P6, Set.of(Feature.SSE2), Set.of(), Set.of(), Privilege.ANY, List.of(), List.of()),
                List.of(),
                List.of(),
                "test",
                "test",
                List.of(),
                List.of(),
                List.of()
        );

        // Features specified as empty -> UNAVAILABLE
        CompatibilityContext ctxKnownFeatures = CompatibilityContext.of(
                CpuGeneration.P6,
                ProcessorMode.PROTECTED_32,
                Set.of(),
                Dialect.NASM,
                "gdb"
        );
        assertEquals(Availability.UNAVAILABLE, CompatibilityResolver.resolve(formWithSse2, ctxKnownFeatures).status());

        // Features unspecified (null) -> UNKNOWN
        CompatibilityContext ctxUnspecifiedFeatures = CompatibilityContext.of(
                CpuGeneration.P6,
                ProcessorMode.PROTECTED_32,
                null,
                Dialect.NASM,
                "gdb"
        );
        assertEquals(Availability.UNKNOWN, CompatibilityResolver.resolve(formWithSse2, ctxUnspecifiedFeatures).status());
    }
}
