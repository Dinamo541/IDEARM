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
        assertEquals(CpuLevel.CPU_8086, mov.get().minCpu());
        assertFalse(mov.get().syntaxVariants().isEmpty());
        assertNotNull(mov.get().summaryEn());
        assertNotNull(mov.get().summaryEs());

        Optional<InstructionInfo> add = InstructionCatalog.find("ADD");
        assertTrue(add.isPresent());
        assertEquals(FlagEffect.MODIFIED, add.get().flags().z());
        assertEquals(FlagEffect.MODIFIED, add.get().flags().c());

        Optional<InstructionInfo> pusha = InstructionCatalog.find("PUSHA");
        assertTrue(pusha.isPresent());
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
}
