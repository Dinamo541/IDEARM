import io.github.dinamo541.idearm.language.catalog.*;

import java.util.*;
import java.util.stream.Collectors;

/** Reproducible inventory of the shipped InstructionCatalog. Read-only: prints to stdout. */
public class Inventory {
    public static void main(String[] args) {
        List<InstructionInfo> all = InstructionCatalog.getAll();
        Set<String> known = InstructionCatalog.knownMnemonics();
        Set<String> primary = all.stream().map(InstructionInfo::mnemonic).collect(Collectors.toCollection(LinkedHashSet::new));

        System.out.println("## Totals");
        System.out.println("primary_entries=" + all.size());
        System.out.println("known_mnemonics=" + known.size());
        System.out.println("variant_aliases=" + (known.size() - primary.size()));

        System.out.println();
        System.out.println("## By category");
        Map<InstructionCategory, Long> byCat = all.stream()
                .collect(Collectors.groupingBy(InstructionInfo::category, TreeMap::new, Collectors.counting()));
        byCat.forEach((k, v) -> System.out.println(k + "=" + v));

        System.out.println();
        System.out.println("## By minCpu");
        Map<CpuLevel, Long> byCpu = all.stream()
                .collect(Collectors.groupingBy(InstructionInfo::minCpu, TreeMap::new, Collectors.counting()));
        byCpu.forEach((k, v) -> System.out.println(k + "=" + v));

        System.out.println();
        System.out.println("## Syntax forms");
        int forms = all.stream().mapToInt(i -> i.syntaxVariants().size()).sum();
        System.out.println("total_syntax_variant_strings=" + forms);
        System.out.println("entries_with_zero_variants=" + all.stream().filter(i -> i.syntaxVariants().isEmpty()).count());
        System.out.println("entries_with_one_variant=" + all.stream().filter(i -> i.syntaxVariants().size() == 1).count());
        System.out.println("max_variants=" + all.stream().mapToInt(i -> i.syntaxVariants().size()).max().orElse(0));

        System.out.println();
        System.out.println("## Flag tables");
        Map<String, Long> flagShapes = all.stream()
                .collect(Collectors.groupingBy(i -> i.flags().formatTable().split("\n")[1], TreeMap::new, Collectors.counting()));
        System.out.println("distinct_flag_tables=" + flagShapes.size());
        flagShapes.forEach((k, v) -> System.out.println("  [" + k + "]=" + v));
        System.out.println("entries_with_no_flag_effect=" + all.stream().filter(i -> !i.flags().isAnyAffected()).count());

        System.out.println();
        System.out.println("## Examples");
        System.out.println("entries_with_empty_example=" + all.stream().filter(i -> i.example().isBlank()).count());
        System.out.println("entries_with_single_line_example=" + all.stream().filter(i -> !i.example().isBlank() && !i.example().contains("\n")).count());
        System.out.println("entries_with_empty_descriptionEn=" + all.stream().filter(i -> i.descriptionEn().isBlank()).count());
        System.out.println("entries_with_empty_descriptionEs=" + all.stream().filter(i -> i.descriptionEs().isBlank()).count());

        System.out.println();
        System.out.println("## Alias mnemonics (known minus primary)");
        List<String> aliases = known.stream().filter(m -> !primary.contains(m)).sorted().toList();
        System.out.println("count=" + aliases.size());
        System.out.println(String.join(" ", aliases));

        System.out.println();
        System.out.println("## Alias -> resolved primary");
        for (String a : aliases) {
            System.out.println("  " + a + " -> " + InstructionCatalog.find(a).map(InstructionInfo::mnemonic).orElse("?"));
        }

        System.out.println();
        System.out.println("## Primary mnemonics (alphabetical)");
        System.out.println(primary.stream().sorted().collect(Collectors.joining(" ")));

        System.out.println();
        System.out.println("## Spot checks requested by the audit");
        for (String m : List.of("MOV", "LEA", "IMUL", "MUL", "DIV", "IDIV", "INC", "DEC", "SHL", "SHR", "SAR", "ROL",
                "RCL", "MOVS", "MOVSB", "LODSB", "STOSB", "CMPS", "SCAS", "INT", "IRET", "CALL", "RET", "PUSH", "POP",
                "PUSHA", "PUSHAD", "TEST", "CMP", "ADC", "SBB", "NEG", "NOT", "XCHG", "CBW", "CWD", "CDQ", "CQO",
                "AAA", "AAD", "AAM", "AAS", "DAA", "DAS", "XLAT", "LOOP", "JCXZ", "JECXZ", "JRCXZ", "SETcc", "CMOVE",
                "BT", "BSF", "SHLD", "ENTER", "LEAVE", "BOUND", "ARPL", "SYSCALL", "SYSENTER", "CPUID", "RDTSC",
                "FLD", "FADD", "FSTSW", "FNSTSW", "WAIT", "FWAIT", "MOVSX", "MOVZX", "MOVABS", "CMPXCHG", "XADD",
                "PUSHF", "POPF", "LAHF", "SAHF", "STI", "CLI", "STD", "CLD", "STC", "CLC", "CMC", "HLT", "NOP",
                "IN", "OUT", "INSB", "OUTSB", "REP", "REPE", "REPNE", "LOCK", "MOVAPS", "ADDPS", "PADDB", "EMMS",
                "SAL", "JA", "JNBE", "JZ", "JE", "JNE", "JNZ", "RETF", "RETN", "IRETD", "IRETQ", "LGDT", "LIDT",
                "MOVSD", "MOVSQ", "STOSD", "LODSD", "SCASD", "CMPSD")) {
            Optional<InstructionInfo> f = InstructionCatalog.find(m);
            System.out.println(String.format("  %-9s known=%-5s -> %-9s minCpu=%-8s flags=[%s]", m, f.isPresent(),
                    f.map(InstructionInfo::mnemonic).orElse("-"),
                    f.map(i -> i.minCpu().displayName()).orElse("-"),
                    f.map(i -> i.flags().formatTable().split("\n")[1]).orElse("-")));
        }

        System.out.println();
        System.out.println("## CpuLevel filtering behaviour");
        for (String target : List.of("8086", "80286", "80386", "i386", "80486", "pentium", "x86-64", "x64", "amd64", "x86", "16", "32", "64", "unknown")) {
            System.out.println(String.format("  target=%-8s parseLevel=%d getForCpu=%d", target,
                    CpuLevel.parseLevel(target), InstructionCatalog.getForCpu(target).size()));
        }
        System.out.println("  getForCpu(null)=" + InstructionCatalog.getForCpu(null).size());

        System.out.println();
        System.out.println("## Search behaviour on punctuation / concept queries");
        for (String q : List.of("dos puntos", ":", "[", "corchetes", "acarreo", "carry", "segmento extra", "interrupcion 21h",
                "int 21h", "stack", "pila", "JNBE", "sal", "overflow", "desbordamiento", "@data", "offset", "ptr")) {
            List<InstructionInfo> hits = InstructionCatalog.filter(null, null, q);
            System.out.println(String.format("  query=%-18s hits=%-4d first=%s", "\"" + q + "\"", hits.size(),
                    hits.stream().limit(6).map(InstructionInfo::mnemonic).collect(Collectors.joining(","))));
        }

        System.out.println();
        System.out.println("## suggest() samples");
        for (String w : List.of("MVO", "MUV", "INTT", "PUSHH", "LEAA", "DATA", "SEGMENT", "PROC", "END", "OFFSET", "AX", "DB", "EQU", "mensaje", "main")) {
            System.out.println("  " + w + " -> " + InstructionCatalog.suggest(w));
        }
    }
}
