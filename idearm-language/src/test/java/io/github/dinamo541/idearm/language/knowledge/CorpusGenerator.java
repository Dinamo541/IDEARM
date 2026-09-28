package io.github.dinamo541.idearm.language.knowledge;

import io.github.dinamo541.idearm.language.catalog.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpusGenerator {

    @Test
    void generateCorpusResources() throws IOException {
        Path baseDir = Path.of("src", "main", "resources", "io", "github", "dinamo541", "idearm", "language", "knowledge");
        Files.createDirectories(baseDir.resolve("semantic/instructions"));
        Files.createDirectories(baseDir.resolve("semantic/registers"));
        Files.createDirectories(baseDir.resolve("semantic/syntax"));
        Files.createDirectories(baseDir.resolve("semantic/services"));
        Files.createDirectories(baseDir.resolve("semantic/concepts"));
        Files.createDirectories(baseDir.resolve("semantic/learning-paths"));
        Files.createDirectories(baseDir.resolve("text/en"));
        Files.createDirectories(baseDir.resolve("text/es"));

        // 1. Explicit legitimate aliases (51 aliases, eliminating INSTRUCTION)
        Map<String, List<Map<String, String>>> aliasesByPrimary = new LinkedHashMap<>();
        addAlias(aliasesByPrimary, "CMOVZ", "CMOVE", "COMMON");
        addAlias(aliasesByPrimary, "CMOVBE", "CMOVNA", "COMMON");
        addAlias(aliasesByPrimary, "CMOVB", "CMOVNAE", "COMMON");
        addAlias(aliasesByPrimary, "CMOVAE", "CMOVNB", "COMMON");
        addAlias(aliasesByPrimary, "CMOVA", "CMOVNBE", "COMMON");
        addAlias(aliasesByPrimary, "CMOVNZ", "CMOVNE", "COMMON");
        addAlias(aliasesByPrimary, "CMOVLE", "CMOVNG", "COMMON");
        addAlias(aliasesByPrimary, "CMOVL", "CMOVNGE", "COMMON");
        addAlias(aliasesByPrimary, "CMOVGE", "CMOVNL", "COMMON");
        addAlias(aliasesByPrimary, "CMOVG", "CMOVNLE", "COMMON");
        addAlias(aliasesByPrimary, "CMOVP", "CMOVPE", "COMMON");
        addAlias(aliasesByPrimary, "CMOVNP", "CMOVPO", "COMMON");
        addAlias(aliasesByPrimary, "FSTSW", "FNSTSW", "COMMON");
        addAlias(aliasesByPrimary, "WAIT", "FWAIT", "COMMON");
        addAlias(aliasesByPrimary, "INS", "INSB", "COMMON");
        addAlias(aliasesByPrimary, "INS", "INSD", "COMMON");
        addAlias(aliasesByPrimary, "INS", "INSW", "COMMON");
        addAlias(aliasesByPrimary, "IRET", "IRETD", "COMMON");
        addAlias(aliasesByPrimary, "IRET", "IRETQ", "COMMON");
        addAlias(aliasesByPrimary, "JBE", "JNA", "COMMON");
        addAlias(aliasesByPrimary, "JB", "JNAE", "COMMON");
        addAlias(aliasesByPrimary, "JAE", "JNB", "COMMON");
        addAlias(aliasesByPrimary, "JA", "JNBE", "COMMON");
        addAlias(aliasesByPrimary, "JLE", "JNG", "COMMON");
        addAlias(aliasesByPrimary, "JL", "JNGE", "COMMON");
        addAlias(aliasesByPrimary, "JGE", "JNL", "COMMON");
        addAlias(aliasesByPrimary, "JG", "JNLE", "COMMON");
        addAlias(aliasesByPrimary, "JP", "JPE", "COMMON");
        addAlias(aliasesByPrimary, "JNP", "JPO", "COMMON");
        addAlias(aliasesByPrimary, "LOOPNE", "LOOPNZ", "COMMON");
        addAlias(aliasesByPrimary, "LOOPE", "LOOPZ", "COMMON");
        addAlias(aliasesByPrimary, "OUTS", "OUTSB", "COMMON");
        addAlias(aliasesByPrimary, "OUTS", "OUTSD", "COMMON");
        addAlias(aliasesByPrimary, "OUTS", "OUTSW", "COMMON");
        addAlias(aliasesByPrimary, "REP", "REPE", "COMMON");
        addAlias(aliasesByPrimary, "REP", "REPNE", "COMMON");
        addAlias(aliasesByPrimary, "REP", "REPNZ", "COMMON");
        addAlias(aliasesByPrimary, "REP", "REPZ", "COMMON");
        addAlias(aliasesByPrimary, "RET", "RETN", "COMMON");
        addAlias(aliasesByPrimary, "SHL", "SAL", "COMMON");
        addAlias(aliasesByPrimary, "SETBE", "SETNA", "COMMON");
        addAlias(aliasesByPrimary, "SETB", "SETNAE", "COMMON");
        addAlias(aliasesByPrimary, "SETAE", "SETNB", "COMMON");
        addAlias(aliasesByPrimary, "SETA", "SETNBE", "COMMON");
        addAlias(aliasesByPrimary, "SETLE", "SETNG", "COMMON");
        addAlias(aliasesByPrimary, "SETL", "SETNGE", "COMMON");
        addAlias(aliasesByPrimary, "SETGE", "SETNL", "COMMON");
        addAlias(aliasesByPrimary, "SETG", "SETNLE", "COMMON");
        addAlias(aliasesByPrimary, "SETP", "SETPE", "COMMON");
        addAlias(aliasesByPrimary, "SETNP", "SETPO", "COMMON");
        addAlias(aliasesByPrimary, "XLAT", "XLATB", "COMMON");

        Map<String, String> textEn = new LinkedHashMap<>();
        Map<String, String> textEs = new LinkedHashMap<>();

        Map<String, List<Map<String, Object>>> familyGroups = new LinkedHashMap<>();

        for (InstructionInfo info : InstructionCatalog.getAll()) {
            String m = info.mnemonic();
            String id = "x86.instr." + m.toLowerCase(Locale.ROOT);
            String family = determineFamily(info);

            // Flag and CPU corrections:
            CpuLevel minCpu = info.minCpu();
            FlagSummary flags = info.flags();

            if (m.startsWith("CMOV")) {
                minCpu = CpuLevel.CPU_P6; // Finding A-03
            }

            if (List.of("BT", "BTS", "BTR", "BTC").contains(m)) {
                // Finding A-06: CF is modified, OF, SF, AF, PF undefined, ZF unaffected
                flags = new FlagSummary(
                        FlagEffect.UNDEFINED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                        FlagEffect.UNDEFINED, FlagEffect.UNAFFECTED, FlagEffect.UNDEFINED, FlagEffect.UNDEFINED, FlagEffect.MODIFIED
                );
            } else if (m.equals("IRET")) {
                // Finding A-07: pops all flags
                flags = new FlagSummary(
                        FlagEffect.MODIFIED, FlagEffect.MODIFIED, FlagEffect.MODIFIED, FlagEffect.MODIFIED,
                        FlagEffect.MODIFIED, FlagEffect.MODIFIED, FlagEffect.MODIFIED, FlagEffect.MODIFIED, FlagEffect.MODIFIED
                );
            } else if (List.of("SHL", "SHR", "SAR", "SHLD", "SHRD").contains(m)) {
                // Finding A-11: AF is undefined for non-zero shift count
                flags = new FlagSummary(
                        FlagEffect.MODIFIED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED, FlagEffect.UNAFFECTED,
                        FlagEffect.MODIFIED, FlagEffect.MODIFIED, FlagEffect.UNDEFINED, FlagEffect.MODIFIED, FlagEffect.MODIFIED
                );
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("mnemonic", m);
            entry.put("kind", "INSTRUCTION");
            entry.put("family", family);
            entry.put("category", info.category().name());
            entry.put("pedagogicalLevel", determineLevel(info));
            entry.put("minCpu", minCpu.displayName());
            entry.put("syntaxVariants", info.syntaxVariants());
            entry.put("example", info.example());

            // Flags serialized
            entry.put("flags", Map.of(
                    "O", flags.o().name(),
                    "D", flags.d().name(),
                    "I", flags.i().name(),
                    "T", flags.t().name(),
                    "S", flags.s().name(),
                    "Z", flags.z().name(),
                    "A", flags.a().name(),
                    "P", flags.p().name(),
                    "C", flags.c().name()
            ));

            // Aliases
            List<Map<String, String>> declaredAliases = aliasesByPrimary.getOrDefault(m, List.of());
            entry.put("aliases", declaredAliases);

            // Sources
            entry.put("sources", List.of("src.intel-sdm.093#vol2:" + m, "src.irvine.5e-es"));

            // RecognizedBy
            if (m.equals("MOVABS")) {
                entry.put("recognizedBy", List.of("GAS")); // Finding A-08
            } else {
                entry.put("recognizedBy", List.of("MASM", "TASM", "NASM"));
            }

            // Forms
            List<Map<String, Object>> forms = new ArrayList<>();
            for (int fi = 0; fi < info.syntaxVariants().size(); fi++) {
                String variant = info.syntaxVariants().get(fi);
                Map<String, Object> formObj = new LinkedHashMap<>();
                formObj.put("id", id + "#form." + (fi + 1));
                formObj.put("text", variant);
                formObj.put("minCpu", minCpu.displayName());
                forms.add(formObj);
            }
            entry.put("forms", forms);

            // Localized text
            textEn.put(id + ".summary", info.summaryEn());
            textEn.put(id + ".description", info.descriptionEn());
            textEs.put(id + ".summary", info.summaryEs());
            textEs.put(id + ".description", info.descriptionEs());

            familyGroups.computeIfAbsent(family, k -> new ArrayList<>()).add(entry);
        }

        // Write family files
        for (Map.Entry<String, List<Map<String, Object>>> e : familyGroups.entrySet()) {
            String filename = "f" + e.getKey().replace("-", "") + ".json";
            Files.writeString(baseDir.resolve("semantic/instructions/" + filename), Json.stringifyPretty(e.getValue()), StandardCharsets.UTF_8);
        }

        // Write text bundles
        Files.writeString(baseDir.resolve("text/en/instructions.json"), Json.stringifyPretty(textEn), StandardCharsets.UTF_8);
        Files.writeString(baseDir.resolve("text/es/instructions.json"), Json.stringifyPretty(textEs), StandardCharsets.UTF_8);

        // Write manifest corpus.json
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", "1.0.0");
        manifest.put("referenceRevision", "SDM 093 / NASM 3.02");
        manifest.put("totalInstructions", InstructionCatalog.getAll().size());
        manifest.put("files", familyGroups.keySet().stream().map(f -> "semantic/instructions/f" + f.replace("-", "") + ".json").toList());

        // Recognized mnemonics per assembler (including the 110 real mnemonics from FalsePositives)
        List<String> asmReal = List.of(
                "MOVAPS", "MOVUPS", "ADDPS", "MULPS", "DIVPS", "SQRTPS", "XORPS", "ANDPS",
                "MOVSS", "MOVSD", "CVTSI2SD", "CVTSD2SI", "ADDSD", "MULSD", "COMISD", "UCOMISD",
                "MOVDQA", "MOVDQU", "PADDB", "PADDW", "PADDD", "PXOR", "PCMPEQB", "PSHUFB",
                "EMMS", "MOVD", "MOVQ", "LDMXCSR", "STMXCSR", "FXSAVE", "FXRSTOR",
                "VADDPS", "VMOVAPS", "VZEROUPPER", "VPXOR",
                "LGDT", "LIDT", "LMSW", "SMSW", "SLDT", "STR", "VERR", "VERW", "CLTS", "INVLPG",
                "RDMSR", "WRMSR", "RSM", "SYSENTER", "SYSEXIT", "SWAPGS", "RDTSCP",
                "MOVSQ", "STOSQ", "LODSQ", "SCASQ", "CMPSQ",
                "SETNE", "CMOVNZ", "BSWAP", "POPCNT", "LZCNT", "TZCNT", "ANDN", "BEXTR",
                "PREFETCHT0", "SFENCE", "LFENCE", "MFENCE", "PAUSE", "CLFLUSH", "MONITOR", "MWAIT",
                "CMPXCHG8B", "CMPXCHG16B", "RDRAND", "RDSEED", "ENDBR64", "AESENC", "SHA1RNDS4",
                "REPNZ", "SALC", "ICEBP", "INT1", "INT3", "FCOMI", "FCMOVB", "FUCOM", "FUCOMI",
                "FSIN", "FCOS", "FPTAN", "FPATAN", "F2XM1", "FYL2X", "FSCALE", "FRNDINT", "FPREM",
                "FSTCW", "FLDCW", "FSAVE", "FRSTOR", "FLDL2E", "FLDLG2", "FLDLN2", "FLD2T", "FLDL2T",
                "AAM", "ARPL", "XLATB", "LAHF", "CBW", "CWDE", "CDQE"
        );
        manifest.put("recognizedByAssembler", Map.of(
                "NASM", asmReal,
                "TASM", asmReal,
                "MASM", asmReal
        ));

        Files.writeString(baseDir.resolve("corpus.json"), Json.stringifyPretty(manifest), StandardCharsets.UTF_8);

        assertTrue(Files.exists(baseDir.resolve("corpus.json")));
    }

    private static void addAlias(Map<String, List<Map<String, String>>> map, String primary, String alias, String dialect) {
        map.computeIfAbsent(primary, k -> new ArrayList<>()).add(Map.of(
                "name", alias,
                "dialect", dialect,
                "targetMnemonic", primary
        ));
    }

    private static String determineFamily(InstructionInfo info) {
        String m = info.mnemonic();
        if (List.of("AAA", "AAD", "AAM", "AAS", "DAA", "DAS").contains(m)) return "F-03";
        if (List.of("SHL", "SHR", "SAR", "ROL", "ROR", "RCL", "RCR", "SHLD", "SHRD").contains(m)) return "F-05";
        if (List.of("BT", "BTS", "BTR", "BTC", "BSF", "BSR").contains(m)) return "F-06";
        if (List.of("MOVS", "MOVSB", "MOVSW", "MOVSD", "CMPS", "CMPSB", "CMPSW", "CMPSD", "SCAS", "SCASB", "SCASW", "SCASD", "LODS", "LODSB", "LODSW", "LODSD", "STOS", "STOSB", "STOSW", "STOSD", "REP", "REPE", "REPNE").contains(m)) return "F-11";
        if (List.of("IN", "OUT", "INS", "OUTS").contains(m)) return "F-12";
        if (List.of("INT", "INTO").contains(m)) return "F-14";
        if (m.startsWith("F") && info.category() == InstructionCategory.FLOATING_POINT) return "F-15";
        if (List.of("SYSCALL", "SYSRET", "ARPL", "BOUND", "LAR", "LSL").contains(m)) return "F-16";
        if (List.of("LOCK", "CMPXCHG", "XADD").contains(m)) return "F-17";
        if (List.of("PUSH", "POP", "PUSHA", "PUSHAD", "POPA", "POPAD", "PUSHF", "PUSHFD", "PUSHFQ", "POPF", "POPFD", "POPFQ", "ENTER", "LEAVE").contains(m)) return "F-10";
        if (List.of("LOOP", "LOOPE", "LOOPNE", "JCXZ", "JECXZ", "JRCXZ").contains(m)) return "F-09";
        if (List.of("JMP", "CALL", "RET", "RETF", "IRET").contains(m)) return "F-08";

        return switch (info.category()) {
            case DATA_TRANSFER -> "F-01";
            case ARITHMETIC -> "F-02";
            case LOGIC -> "F-04";
            case CONTROL_FLOW -> "F-07";
            case FLAGS_CONTROL -> "F-13";
            case STACK_PROCEDURES -> "F-10";
            case SYSTEM_INTERRUPTS -> "F-14";
            case IO_PORTS -> "F-12";
            case BIT_MANIPULATION -> "F-06";
            case FLOATING_POINT -> "F-15";
            case STRINGS -> "F-11";
        };
    }

    private static String determineLevel(InstructionInfo info) {
        if (info.minCpu() == CpuLevel.CPU_8086) return "BASIC";
        if (info.minCpu().level() <= 3) return "INTERMEDIATE";
        return "ADVANCED";
    }
}
