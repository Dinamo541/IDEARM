package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test verifying AA-P5-01: Coverage report generation and inventory integrity.
 */
@DisplayName("AA-P5-01: Coverage Report Generation & Inventory Integrity")
class CoverageReportTest {

    @Test
    @DisplayName("Generate docs/academic-assistant/coverage.md and verify all 18 families and zero omissions")
    void testGenerateCoverageReport() throws IOException {
        Corpus corpus = Corpus.get();
        assertNotNull(corpus, "Corpus must not be null");

        CoverageReportGenerator.CoverageSummary summary = CoverageReportGenerator.calculateSummary(corpus);
        assertTrue(summary.totalInstructions() >= 200, "Corpus must contain at least 200 instructions");
        assertTrue(summary.completeCount() > 0, "Complete count must be greater than 0");
        assertTrue(summary.minimalCount() > 0, "Minimal count must be greater than 0");
        assertTrue(summary.outlineCount() > 0, "Outline count must be greater than 0");

        // Verify all 18 families F-01 to F-18
        assertEquals(18, summary.familyStats().size(), "All 18 families F-01 to F-18 must be present");
        for (int i = 1; i <= 18; i++) {
            String famId = String.format("F-%02d", i);
            assertTrue(summary.familyStats().containsKey(famId), "Family " + famId + " must exist in stats");
            CoverageReportGenerator.FamilyStats stats = summary.familyStats().get(famId);
            assertTrue(stats.total() > 0, "Family " + famId + " must contain at least one instruction");
        }

        String markdown = CoverageReportGenerator.generateMarkdown(corpus);
        assertNotNull(markdown);
        assertFalse(markdown.isBlank());

        // Acceptance criterion: No "etc." or ellipses in inventory
        assertFalse(markdown.contains("etc."), "Markdown report must not contain 'etc.' abbreviations");
        assertFalse(markdown.contains("…"), "Markdown report must not contain ellipsis '…'");
        assertFalse(markdown.contains("..."), "Markdown report must not contain ellipsis '...'");

        // Write to docs/academic-assistant/coverage.md
        Path docsDir = Paths.get("..", "docs", "academic-assistant");
        if (!Files.exists(docsDir)) {
            docsDir = Paths.get("docs", "academic-assistant");
        }
        if (Files.exists(docsDir)) {
            Path coverageFile = docsDir.resolve("coverage.md");
            Files.writeString(coverageFile, markdown);
            assertTrue(Files.exists(coverageFile), "coverage.md file must exist after generation");
            assertTrue(Files.size(coverageFile) > 1000, "coverage.md must be substantial");
        }
    }
}
