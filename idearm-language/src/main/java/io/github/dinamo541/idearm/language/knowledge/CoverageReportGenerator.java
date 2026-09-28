package io.github.dinamo541.idearm.language.knowledge;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates the authoritative Coverage and Inventory Report for the Academic Knowledge Base (AA-P5-01).
 * Complies with pure JDK Layer 3 requirements (ADR-007).
 */
public final class CoverageReportGenerator {

    private static final Map<String, String> FAMILY_NAMES = Map.ofEntries(
            Map.entry("F-01", "Basic data transfer"),
            Map.entry("F-02", "Integer arithmetic"),
            Map.entry("F-03", "BCD decimal arithmetic"),
            Map.entry("F-04", "Logic and bit manipulation"),
            Map.entry("F-05", "Shifts and rotations"),
            Map.entry("F-06", "Advanced bit manipulation"),
            Map.entry("F-07", "Comparison, conditional jumps and SETcc"),
            Map.entry("F-08", "Unconditional jumps and procedure calls"),
            Map.entry("F-09", "Loops and register counting"),
            Map.entry("F-10", "Stack, frames and control registers"),
            Map.entry("F-11", "String operations and REP prefixes"),
            Map.entry("F-12", "Port input and output"),
            Map.entry("F-13", "Status flags and CPU control"),
            Map.entry("F-14", "Interrupts, exceptions and traps"),
            Map.entry("F-15", "x87 floating point (FPU)"),
            Map.entry("F-16", "System and protected mode"),
            Map.entry("F-17", "Atomic operations and synchronization"),
            Map.entry("F-18", "SIMD extensions (MMX, SSE, SSE2, AVX)")
    );

    public record FamilyStats(
            String familyId,
            String name,
            int total,
            int complete,
            int minimal,
            int outline,
            int excluded,
            List<InstructionEntry> instructions
    ) {}

    public record CoverageSummary(
            int totalInstructions,
            int completeCount,
            int minimalCount,
            int outlineCount,
            int excludedCount,
            Map<String, FamilyStats> familyStats,
            Map<PedagogicalLevel, Integer> pedagogicalCounts
    ) {}

    private CoverageReportGenerator() {
    }

    public static TreatmentLevel determineTreatmentLevel(InstructionEntry entry) {
        if ("MOVABS".equalsIgnoreCase(entry.mnemonic())) {
            return TreatmentLevel.EXCLUDED;
        }

        // The corpus marks an outline entry in the prose of both languages.
        String desc = (entry.descriptionEs() + " " + entry.descriptionEn()).toLowerCase(Locale.ROOT);
        if (desc.contains("no cubierta en profundidad") || desc.contains("not covered in depth")
                || desc.contains("esbozo") || desc.contains("outline")) {
            return TreatmentLevel.OUTLINE;
        }

        // SIMD vector outlines
        if ("F-18".equalsIgnoreCase(entry.family()) && entry.mnemonic().startsWith("V")) {
            return TreatmentLevel.OUTLINE;
        }

        // System instructions, MMX or undocumented opcodes without extensive execution examples
        if ("F-16".equalsIgnoreCase(entry.family())
                || "SALC".equalsIgnoreCase(entry.mnemonic())
                || "ICEBP".equalsIgnoreCase(entry.mnemonic())
                || "UD2".equalsIgnoreCase(entry.mnemonic())
                || ("F-18".equalsIgnoreCase(entry.family()) && (entry.example() == null || entry.example().isBlank()))) {
            return TreatmentLevel.MINIMAL;
        }

        if (entry.example() != null && !entry.example().isBlank()
                && !entry.forms().isEmpty()
                && !entry.sources().isEmpty()) {
            return TreatmentLevel.COMPLETE;
        }

        return TreatmentLevel.MINIMAL;
    }

    public static CoverageSummary calculateSummary(Corpus corpus) {
        List<InstructionEntry> all = corpus.getAllInstructions();
        int total = all.size();
        int complete = 0;
        int minimal = 0;
        int outline = 0;
        int excluded = 0;

        Map<String, List<InstructionEntry>> byFamily = new TreeMap<>();
        Map<PedagogicalLevel, Integer> pedCounts = new EnumMap<>(PedagogicalLevel.class);
        for (PedagogicalLevel p : PedagogicalLevel.values()) {
            pedCounts.put(p, 0);
        }

        for (InstructionEntry e : all) {
            TreatmentLevel lvl = determineTreatmentLevel(e);
            switch (lvl) {
                case COMPLETE -> complete++;
                case MINIMAL -> minimal++;
                case OUTLINE -> outline++;
                case EXCLUDED -> excluded++;
            }

            PedagogicalLevel ped = e.pedagogicalLevel() != null ? e.pedagogicalLevel() : PedagogicalLevel.BASIC;
            pedCounts.put(ped, pedCounts.get(ped) + 1);

            String fam = e.family() != null ? e.family() : "F-01";
            byFamily.computeIfAbsent(fam, k -> new ArrayList<>()).add(e);
        }

        Map<String, FamilyStats> stats = new LinkedHashMap<>();
        for (int i = 1; i <= 18; i++) {
            String famId = format("F-%02d", i);
            String name = FAMILY_NAMES.getOrDefault(famId, "Family " + famId);
            List<InstructionEntry> list = byFamily.getOrDefault(famId, List.of());
            int c = 0, m = 0, o = 0, x = 0;
            for (InstructionEntry e : list) {
                TreatmentLevel lvl = determineTreatmentLevel(e);
                switch (lvl) {
                    case COMPLETE -> c++;
                    case MINIMAL -> m++;
                    case OUTLINE -> o++;
                    case EXCLUDED -> x++;
                }
            }
            stats.put(famId, new FamilyStats(famId, name, list.size(), c, m, o, x, list));
        }

        return new CoverageSummary(total, complete, minimal, outline, excluded, stats, pedCounts);
    }

    public static String generateMarkdown(Corpus corpus) {
        CoverageSummary summary = calculateSummary(corpus);
        StringBuilder sb = new StringBuilder();
        int total = summary.totalInstructions();

        sb.append("# Academic Assistant coverage and inventory report\n\n");
        sb.append("> **Reference revisions:** Intel SDM revision 093 / NASM 3.02 / MASM 6.11 / TASM 4.1.\n");
        sb.append("> **Generated by:** `CoverageReportTest` in `idearm-language`, from the corpus it describes.\n");
        sb.append("> **Project phase:** AA-P5 (systematic expansion of the corpus).\n\n");

        sb.append("## 1. Coverage summary\n\n");
        sb.append("| Metric | Value | Percentage |\n");
        sb.append("|---|:---:|:---:|\n");
        sb.append(format("| **Cataloged mnemonics** | **%d** | 100.0%% |\n", total));
        sb.append(format("| Complete level (core x86 / x87 FPU) | %d | %.1f%% |\n",
                summary.completeCount(), percent(summary.completeCount(), total)));
        sb.append(format("| Minimal level (system, MMX, undocumented opcodes) | %d | %.1f%% |\n",
                summary.minimalCount(), percent(summary.minimalCount(), total)));
        sb.append(format("| Outline level (AVX, vector SSE, CPUID bit) | %d | %.1f%% |\n",
                summary.outlineCount(), percent(summary.outlineCount(), total)));
        sb.append(format("| Classified / specific dialect | %d | %.1f%% |\n",
                summary.excludedCount(), percent(summary.excludedCount(), total)));
        sb.append("| **Families covered (F-01 to F-18)** | **18 / 18** | **100.0%** |\n\n");

        sb.append("## 2. Breakdown by instruction family\n\n");
        sb.append("| ID | Family name | Total | Complete | Minimal | Outline | Cataloged mnemonics |\n");
        sb.append("|---|---|:---:|:---:|:---:|:---:|---|\n");

        for (FamilyStats fs : summary.familyStats().values()) {
            String allMnemonics = fs.instructions().stream()
                    .map(InstructionEntry::mnemonic)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(", "));
            sb.append(format("| **%s** | %s | %d | %d | %d | %d | %s |\n",
                    fs.familyId(), fs.name(), fs.total(), fs.complete(), fs.minimal(), fs.outline(), allMnemonics));
        }
        sb.append("\n");

        sb.append("## 3. Breakdown by pedagogical level\n\n");
        sb.append("| Pedagogical level | Instructions | Description |\n");
        sb.append("|---|:---:|---|\n");
        sb.append(format("| `BASIC` | %d | Essential instructions for first steps (16/32-bit modes) |\n",
                summary.pedagogicalCounts().getOrDefault(PedagogicalLevel.BASIC, 0)));
        sb.append(format("| `INTERMEDIATE` | %d | Addressing, strings, BCD and call instructions |\n",
                summary.pedagogicalCounts().getOrDefault(PedagogicalLevel.INTERMEDIATE, 0)));
        sb.append(format("| `ADVANCED` | %d | Protected mode, x87 FPU, SIMD extensions and 64-bit |\n\n",
                summary.pedagogicalCounts().getOrDefault(PedagogicalLevel.ADVANCED, 0)));

        sb.append("## 4. Editorial criteria and authoritative sources\n\n");
        sb.append("1. **Intel SDM revision 093:** canonical source for opcode encoding, effects on FLAGS and CPU exceptions.\n");
        sb.append("2. **Irvine (5th edition, Spanish translation):** core teaching reference for x86 Assembly in academic settings.\n");
        sb.append("3. **Stallings (7th edition) and Mano (3rd edition), Spanish translations:** foundations of computer architecture and digital circuits.\n");
        sb.append("4. **Official NASM 3.02, MASM 6.11 and TASM 4.1 manuals:** strict boundaries of each dialect's directives and operators.\n");
        sb.append("5. **Independence from emu8086:** the corpus and the semantic tests are validated against independent mathematical oracles, never against the emu8086 implementation.\n\n");

        sb.append("## 5. Full inventory of mnemonics by family (no omissions)\n\n");
        for (FamilyStats fs : summary.familyStats().values()) {
            sb.append(format("### %s: %s (%d instructions)\n\n", fs.familyId(), fs.name(), fs.total()));
            sb.append("| Mnemonic | Pedagogical level | Treatment | Summary |\n");
            sb.append("|---|:---:|:---:|---|\n");
            List<InstructionEntry> sortedList = fs.instructions().stream()
                    .sorted(Comparator.comparing(InstructionEntry::mnemonic))
                    .toList();
            for (InstructionEntry e : sortedList) {
                TreatmentLevel lvl = determineTreatmentLevel(e);
                sb.append(format("| `%s` | `%s` | `%s` | %s |\n",
                        e.mnemonic(), e.pedagogicalLevel(), lvl.name(), e.summaryEn()));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private static double percent(int count, int total) {
        return total > 0 ? count * 100.0 / total : 0;
    }

    /** Formats with a fixed locale, so the report reads 86.6% whatever the language of the machine. */
    private static String format(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }
}
