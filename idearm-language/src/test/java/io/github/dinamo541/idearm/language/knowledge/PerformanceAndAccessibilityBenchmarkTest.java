package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates AA-P7-02: Performance benchmarks (load latency, lookup speed)
 * and accessible diagram/text guarantees.
 */
@DisplayName("AA-P7-02: Performance Benchmarks and Accessibility Validation")
class PerformanceAndAccessibilityBenchmarkTest {

    @Test
    @DisplayName("Corpus Load Latency Benchmark: load time must be < 50 ms under warm/cached conditions")
    void benchmarkCorpusAccessLatency() {
        long start = System.nanoTime();
        Corpus corpus = Corpus.get();
        long elapsedNanos = System.nanoTime() - start;
        double elapsedMs = elapsedNanos / 1_000_000.0;

        assertNotNull(corpus);
        assertTrue(corpus.getAllInstructions().size() >= 200);

        System.out.printf("Corpus Access Latency: %.3f ms%n", elapsedMs);
        assertTrue(elapsedMs < 50.0, "Corpus access time must be strictly under 50 ms");
    }

    @Test
    @DisplayName("Lookup & Filter Latency Benchmark: average lookup latency must be < 0.05 ms")
    void benchmarkLookupLatency() {
        Corpus corpus = Corpus.get();

        String[] testMnemonics = {"MOV", "ADD", "XOR", "SUB", "CMP", "IMUL", "DIV", "CALL", "RET", "FSQRT", "VADDPS", "SALC"};

        // Warm up JIT
        for (int i = 0; i < 2_000; i++) {
            corpus.findInstruction(testMnemonics[i % testMnemonics.length]);
            corpus.findSyntaxItem("@DATA");
            corpus.findService("21h", "09h");
        }

        int iterations = 10_000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String m = testMnemonics[i % testMnemonics.length];
            Optional<InstructionEntry> instr = corpus.findInstruction(m);
            assertTrue(instr.isPresent());
        }
        long elapsedNanos = System.nanoTime() - start;
        double avgMicros = (elapsedNanos / (double) iterations) / 1000.0;

        System.out.printf("Average Instruction Lookup Latency: %.4f µs (microsecond)%n", avgMicros);
        assertTrue(avgMicros < 50.0, "Average lookup must be under 50 microseconds (0.05 ms)");
    }

    @Test
    @DisplayName("Accessibility Validation: Diagrams and texts must contain only printable characters and valid formatting")
    void verifyAccessibilityGuarantees() {
        // 1. Register Diagram
        String regDiag = PedagogicalDiagrams.renderRegisterHierarchy("RAX");
        assertFalse(regDiag.contains("\u0000"), "Diagram must not contain null characters");
        assertFalse(regDiag.contains("\u001B"), "Diagram must not contain ANSI escape sequences (theme independent)");

        // 2. Stack Frame Diagram
        String stackDiag = PedagogicalDiagrams.renderStackFrame(true);
        assertFalse(stackDiag.contains("\u0000"));
        assertFalse(stackDiag.contains("\u001B"));

        // 3. Segmentation Diagram
        String segDiag = PedagogicalDiagrams.renderSegmentation20Bit(0x1234, 0x0100);
        assertFalse(segDiag.contains("\u0000"));
        assertFalse(segDiag.contains("\u001B"));

        // 4. Instructions and Concepts must be pure readable text without raw control codes
        Corpus corpus = Corpus.get();
        for (InstructionEntry e : corpus.getAllInstructions()) {
            assertFalse(e.descriptionEs().contains("\u0000"));
            assertFalse(e.descriptionEn().contains("\u0000"));
        }
        for (ConceptEntry c : corpus.getAllConcepts()) {
            assertFalse(c.contentEs().contains("\u0000"));
            assertFalse(c.contentEn().contains("\u0000"));
        }
    }
}
