package io.github.dinamo541.idearm.language.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InstructionCatalogTest {

    @Test
    void findsEssential8086Instructions() {
        Optional<InstructionInfo> mov = InstructionCatalog.find("MOV");
        assertTrue(mov.isPresent());
        assertEquals("MOV", mov.get().mnemonic());
        assertEquals(InstructionCategory.DATA_TRANSFER, mov.get().category());
        assertEquals(CpuLevel.CPU_8086, mov.get().minCpu());
        assertFalse(mov.get().syntaxVariants().isEmpty());
        assertNotNull(mov.get().summaryEn());
        assertNotNull(mov.get().summaryEs());

        Optional<InstructionInfo> add = InstructionCatalog.find("ADD");
        assertTrue(add.isPresent());
        assertEquals(InstructionCategory.ARITHMETIC, add.get().category());
        assertEquals(FlagEffect.MODIFIED, add.get().flags().z());
        assertEquals(FlagEffect.MODIFIED, add.get().flags().c());

        Optional<InstructionInfo> pusha = InstructionCatalog.find("PUSHA");
        assertTrue(pusha.isPresent());
        assertEquals(InstructionCategory.STACK_PROCEDURES, pusha.get().category());
        assertEquals(CpuLevel.CPU_80186, pusha.get().minCpu());
    }

    @Test
    void filtersInstructionsByCpuBaseline() {
        List<InstructionInfo> for8086 = InstructionCatalog.getForCpu("8086");
        assertTrue(for8086.stream().anyMatch(i -> i.mnemonic().equals("MOV")));
        assertTrue(for8086.stream().anyMatch(i -> i.mnemonic().equals("INT")));
        assertFalse(for8086.stream().anyMatch(i -> i.mnemonic().equals("PUSHA")));

        List<InstructionInfo> for186 = InstructionCatalog.getForCpu("80186");
        assertTrue(for186.stream().anyMatch(i -> i.mnemonic().equals("PUSHA")));
    }

    @Test
    void searchesByPrefixCaseInsensitive() {
        List<InstructionInfo> jmps = InstructionCatalog.searchStartingWith("j");
        assertFalse(jmps.isEmpty());
        assertTrue(jmps.stream().anyMatch(i -> i.mnemonic().equals("JMP")));
        assertTrue(jmps.stream().anyMatch(i -> i.mnemonic().equals("JE")));
        assertTrue(jmps.stream().anyMatch(i -> i.mnemonic().equals("JNE")));
    }

    @Test
    void filtersByCategory() {
        List<InstructionInfo> arithmetic = InstructionCatalog.getByCategory(InstructionCategory.ARITHMETIC);
        assertFalse(arithmetic.isEmpty());
        assertTrue(arithmetic.stream().allMatch(i -> i.category() == InstructionCategory.ARITHMETIC));
        assertTrue(arithmetic.stream().anyMatch(i -> i.mnemonic().equals("ADD")));
        assertTrue(arithmetic.stream().anyMatch(i -> i.mnemonic().equals("SUB")));
        assertFalse(arithmetic.stream().anyMatch(i -> i.mnemonic().equals("MOV")));

        List<InstructionInfo> jumps = InstructionCatalog.getByCategory(InstructionCategory.CONTROL_FLOW);
        assertFalse(jumps.isEmpty());
        assertTrue(jumps.stream().anyMatch(i -> i.mnemonic().equals("JMP")));
        assertTrue(jumps.stream().anyMatch(i -> i.mnemonic().equals("CALL")));
    }

    @Test
    void filtersByMultipleCriteria() {
        // Filter by category + CPU + keyword
        List<InstructionInfo> res = InstructionCatalog.filter(InstructionCategory.ARITHMETIC, "8086", "add");
        assertFalse(res.isEmpty());
        assertTrue(res.stream().anyMatch(i -> i.mnemonic().equals("ADD")));
        assertTrue(res.stream().anyMatch(i -> i.mnemonic().equals("ADC")));
        assertFalse(res.stream().anyMatch(i -> i.mnemonic().equals("CWDE"))); // CWDE is 386

        // Null category returns all categories matching CPU and search query
        List<InstructionInfo> allData = InstructionCatalog.filter(null, "8086", "mov");
        assertTrue(allData.stream().anyMatch(i -> i.mnemonic().equals("MOV")));
        assertTrue(allData.stream().anyMatch(i -> i.mnemonic().equals("MOVSB")));
    }

    @Test
    void findsBcdAndAsciiAdjustInstructions() {
        for (String mnemonic : List.of("AAA", "AAS", "AAM", "AAD", "DAA", "DAS")) {
            Optional<InstructionInfo> info = InstructionCatalog.find(mnemonic);
            assertTrue(info.isPresent(), "Expected " + mnemonic + " to be present");
            assertEquals(InstructionCategory.ARITHMETIC, info.get().category());
            assertEquals(CpuLevel.CPU_8086, info.get().minCpu());
        }
    }

    @Test
    void findsExtendedConditionalJumps() {
        for (String mnemonic : List.of("JZ", "JNZ", "JC", "JNC", "JAE", "JBE", "JGE", "JLE", "JS", "JNS", "JO", "JNO", "JP", "JNP", "JECXZ", "LOOPE", "LOOPNE")) {
            Optional<InstructionInfo> info = InstructionCatalog.find(mnemonic);
            assertTrue(info.isPresent(), "Expected " + mnemonic + " to be present");
            assertEquals(InstructionCategory.CONTROL_FLOW, info.get().category());
        }
    }

    @Test
    void findsRotatesAndFlagInstructions() {
        for (String mnemonic : List.of("RCL", "RCR", "SHLD", "SHRD")) {
            Optional<InstructionInfo> info = InstructionCatalog.find(mnemonic);
            assertTrue(info.isPresent(), "Expected " + mnemonic + " to be present");
            assertEquals(InstructionCategory.LOGIC, info.get().category());
        }

        for (String mnemonic : List.of("LAHF", "SAHF", "CMC", "WAIT", "LOCK")) {
            Optional<InstructionInfo> info = InstructionCatalog.find(mnemonic);
            assertTrue(info.isPresent(), "Expected " + mnemonic + " to be present");
            assertEquals(InstructionCategory.FLAGS_CONTROL, info.get().category());
        }
    }

    @Test
    void findsAdvancedDataTransferAndSystemInstructions() {
        for (String mnemonic : List.of("LDS", "LES", "LFS", "LGS", "LSS", "BSWAP", "CMPXCHG", "XADD", "CMOVZ", "CMOVNZ")) {
            Optional<InstructionInfo> info = InstructionCatalog.find(mnemonic);
            assertTrue(info.isPresent(), "Expected " + mnemonic + " to be present");
            assertEquals(InstructionCategory.DATA_TRANSFER, info.get().category());
        }

        for (String mnemonic : List.of("CPUID", "RDTSC", "UD2", "INTO")) {
            Optional<InstructionInfo> info = InstructionCatalog.find(mnemonic);
            assertTrue(info.isPresent(), "Expected " + mnemonic + " to be present");
            assertEquals(InstructionCategory.SYSTEM_INTERRUPTS, info.get().category());
        }
    }

    @Test
    void findsTheSetccFamilyWithEachConditionKeptApart() {
        for (String mnemonic : List.of("SETZ", "SETNZ", "SETE", "SETNE", "SETA", "SETAE", "SETB", "SETBE",
                "SETG", "SETGE", "SETL", "SETLE", "SETC", "SETNC", "SETS", "SETNS", "SETO", "SETNO", "SETP", "SETNP")) {
            Optional<InstructionInfo> info = InstructionCatalog.find(mnemonic);
            assertTrue(info.isPresent(), "Expected " + mnemonic + " to be present");
            assertEquals(InstructionCategory.CONTROL_FLOW, info.get().category());
            assertEquals(CpuLevel.CPU_80386, info.get().minCpu());
        }

        // A condition must never resolve to a different one, or the hover would teach the wrong test.
        assertEquals("SETA", InstructionCatalog.find("SETA").orElseThrow().mnemonic());
        assertEquals("SETZ", InstructionCatalog.find("SETZ").orElseThrow().mnemonic());
        assertEquals("SETG", InstructionCatalog.find("SETG").orElseThrow().mnemonic());

        // Genuine synonyms of one condition may share an entry: SETNBE is SETA written the other way round.
        assertEquals("SETA", InstructionCatalog.find("SETNBE").orElseThrow().mnemonic());
        assertEquals("SETG", InstructionCatalog.find("SETNLE").orElseThrow().mnemonic());
    }

    @Test
    void findsTheCmovccFamilyWithEachConditionKeptApart() {
        for (String mnemonic : List.of("CMOVA", "CMOVAE", "CMOVB", "CMOVBE", "CMOVC", "CMOVNC", "CMOVG", "CMOVGE",
                "CMOVL", "CMOVLE", "CMOVS", "CMOVNS", "CMOVO", "CMOVNO", "CMOVP", "CMOVNP")) {
            Optional<InstructionInfo> info = InstructionCatalog.find(mnemonic);
            assertTrue(info.isPresent(), "Expected " + mnemonic + " to be present");
            assertEquals(InstructionCategory.DATA_TRANSFER, info.get().category());
            assertEquals(CpuLevel.CPU_P6, info.get().minCpu());
        }

        assertEquals("CMOVA", InstructionCatalog.find("CMOVA").orElseThrow().mnemonic());
        assertEquals("CMOVG", InstructionCatalog.find("CMOVG").orElseThrow().mnemonic());
        assertEquals("CMOVA", InstructionCatalog.find("CMOVNBE").orElseThrow().mnemonic());
    }

    @Test
    void findsTheX87FloatingPointFamily() {
        for (String mnemonic : List.of("FLD", "FST", "FSTP", "FADD", "FSUB", "FMUL", "FDIV", "FCOM", "FCOMP",
                "FINIT", "FNINIT", "FILD", "FIST", "FISTP", "FCHS", "FABS", "FSQRT", "FLDZ", "FLD1", "FLDPI",
                "FSTSW", "FXCH", "FTST")) {
            Optional<InstructionInfo> info = InstructionCatalog.find(mnemonic);
            assertTrue(info.isPresent(), "Expected " + mnemonic + " to be present");
            assertEquals(InstructionCategory.FLOATING_POINT, info.get().category());
            assertFalse(info.get().flags().isAnyAffected(),
                    mnemonic + " reports through the x87 status word, not through the CPU flags");
        }

        // FWAIT stays the alias of WAIT it already was; describing x87 must not have stolen it.
        assertEquals("WAIT", InstructionCatalog.find("FWAIT").orElseThrow().mnemonic());
    }

    @Test
    void findsInstructionsWithWhitespaceAndCase() {
        assertTrue(InstructionCatalog.find("  mov  ").isPresent());
        assertTrue(InstructionCatalog.find("\tADD\n").isPresent());
        assertFalse(InstructionCatalog.find(null).isPresent());
        assertFalse(InstructionCatalog.find("").isPresent());
        assertFalse(InstructionCatalog.find("   ").isPresent());
        assertTrue(InstructionCatalog.isKnownInstruction("cmp"));
        assertFalse(InstructionCatalog.isKnownInstruction("unknown_cmd"));
    }

    @Test
    void resolvesAliasesAutomatically() {
        // FWAIT resolves to WAIT
        Optional<InstructionInfo> fwait = InstructionCatalog.find("FWAIT");
        assertTrue(fwait.isPresent());
        assertEquals("WAIT", fwait.get().mnemonic());

        // JNBE resolves to JA
        Optional<InstructionInfo> jnbe = InstructionCatalog.find("JNBE");
        assertTrue(jnbe.isPresent());
        assertEquals("JA", jnbe.get().mnemonic());

        // INSB resolves to INS
        Optional<InstructionInfo> insb = InstructionCatalog.find("INSB");
        assertTrue(insb.isPresent());
        assertEquals("INS", insb.get().mnemonic());

        // CMOVE resolves to CMOVZ
        Optional<InstructionInfo> cmove = InstructionCatalog.find("CMOVE");
        assertTrue(cmove.isPresent());
        assertEquals("CMOVZ", cmove.get().mnemonic());
    }

    @Test
    void filtersWithAccentInsensitivity() {
        // "aritmética" with accent vs without accent
        List<InstructionInfo> withAccent = InstructionCatalog.filter(null, null, "aritmética");
        List<InstructionInfo> withoutAccent = InstructionCatalog.filter(null, null, "aritmetica");
        assertFalse(withAccent.isEmpty());
        assertEquals(withAccent.size(), withoutAccent.size());

        // "rotación" vs "rotacion"
        List<InstructionInfo> rotWith = InstructionCatalog.filter(null, null, "rotación");
        List<InstructionInfo> rotWithout = InstructionCatalog.filter(null, null, "rotacion");
        assertFalse(rotWith.isEmpty());
        assertEquals(rotWith.size(), rotWithout.size());
    }

    @Test
    void instructionCategoryRobustness() {
        assertEquals("Aritmética", InstructionCategory.ARITHMETIC.displayName("es"));
        assertEquals("Aritmética", InstructionCategory.ARITHMETIC.displayName("es_MX"));
        assertEquals("Arithmetic", InstructionCategory.ARITHMETIC.displayName("en"));
        assertEquals("Arithmetic", InstructionCategory.ARITHMETIC.displayName(null));

        Optional<InstructionCategory> parsed = InstructionCategory.parse("Transferencia de Datos");
        assertTrue(parsed.isPresent());
        assertEquals(InstructionCategory.DATA_TRANSFER, parsed.get());

        Optional<InstructionCategory> parsedEn = InstructionCategory.parse("Arithmetic");
        assertTrue(parsedEn.isPresent());
        assertEquals(InstructionCategory.ARITHMETIC, parsedEn.get());

        assertFalse(InstructionCategory.DATA_TRANSFER.instructions().isEmpty());
    }

    @Test
    void flagSummaryRobustness() {
        FlagSummary none = FlagSummary.none();
        assertFalse(none.isAnyModified());
        assertFalse(none.isAnyAffected());

        FlagSummary arith = FlagSummary.standardArithmetic();
        assertTrue(arith.isAnyModified());
        assertTrue(arith.isAnyAffected());

        FlagSummary logic = FlagSummary.standardLogic();
        assertTrue(logic.isAnyModified());
        assertTrue(logic.isAnyAffected());
    }

    @Test
    void instructionInfoHelpers() {
        InstructionInfo mov = InstructionCatalog.find("MOV").orElseThrow();
        assertEquals(mov.summaryEs(), mov.summary("es"));
        assertEquals(mov.summaryEn(), mov.summary("en"));
        assertEquals(mov.descriptionEs(), mov.description("es"));
        assertEquals(mov.descriptionEn(), mov.description("en"));

        assertTrue(mov.matches("mov"));
        assertTrue(mov.matches("copiar"));
        assertFalse(mov.matches("nonexistent_pattern_xyz"));
    }

    @Test
    void allInstructionsHaveCategoriesAndDescriptions() {
        List<InstructionInfo> all = InstructionCatalog.getAll();
        assertTrue(all.size() >= 80, "Catalog should have at least 80 instructions, had: " + all.size());
        for (InstructionInfo info : all) {
            assertNotNull(info.category(), "Category missing for " + info.mnemonic());
            assertNotNull(info.summaryEn(), "summaryEn missing for " + info.mnemonic());
            assertNotNull(info.summaryEs(), "summaryEs missing for " + info.mnemonic());
            assertNotNull(info.descriptionEn(), "descriptionEn missing for " + info.mnemonic());
            assertNotNull(info.descriptionEs(), "descriptionEs missing for " + info.mnemonic());
            assertNotNull(info.example(), "example missing for " + info.mnemonic());
            assertNotNull(info.flags(), "flags missing for " + info.mnemonic());
            assertNotNull(info.minCpu(), "minCpu missing for " + info.mnemonic());
            assertFalse(info.syntaxVariants().isEmpty(), "syntaxVariants should not be empty for " + info.mnemonic());
        }
    }

    @Test
    void knownMnemonicsCoversPrimaryNamesAndVariantAliases() {
        var known = InstructionCatalog.knownMnemonics();

        assertTrue(known.contains("MOV"));
        assertTrue(known.contains("JNBE"), "an alias of JA is still a mnemonic a student may type");
        assertTrue(known.contains("SAL"), "SAL is SHL under another name");
        assertTrue(known.contains("REP"), "the repeat prefix is written where an instruction goes");
        assertFalse(known.contains("MUV"));
    }

    @Test
    void suggestsTheClosestMnemonicForATypo() {
        assertTrue(InstructionCatalog.suggest("MUV").contains("MOV"));
        assertTrue(InstructionCatalog.suggest("MVO").contains("MOV"), "a swap is one mistake, like a wrong letter");
        assertTrue(InstructionCatalog.suggest("ADDD").contains("ADD"));
    }

    @Test
    void suggestsNothingWhenThereIsNothingToSuggest() {
        assertTrue(InstructionCatalog.suggest("MOV").isEmpty(), "a real mnemonic needs no correction");
        assertTrue(InstructionCatalog.suggest("PRINTSTRING").isEmpty(), "a wrong guess teaches worse than none");
        assertTrue(InstructionCatalog.suggest(null).isEmpty());
        assertTrue(InstructionCatalog.suggest("  ").isEmpty());
    }
}
