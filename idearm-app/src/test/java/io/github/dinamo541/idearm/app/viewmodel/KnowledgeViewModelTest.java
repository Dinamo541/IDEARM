package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.language.catalog.InstructionCategory;
import io.github.dinamo541.idearm.language.catalog.InstructionInfo;
import io.github.dinamo541.idearm.language.knowledge.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeViewModelTest {

    private KnowledgeViewModel viewModel;

    @BeforeEach
    void setUp() {
        viewModel = new KnowledgeViewModel();
    }

    @Test
    void filtersInstructionsByBitness() {
        // MOV is universal in all modes
        assertTrue(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("MOV")));

        // 16-bit mode: 8086-286 instructions valid; 386+ and 64-bit-only absent
        viewModel.selectedBitnessProperty().set(ArchitectureBitness.BITS_16);
        assertTrue(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("MOV")));
        assertTrue(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("AAA")));
        assertTrue(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("PUSHA")));
        assertFalse(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("BSF")));
        assertFalse(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("CQO")));
        assertFalse(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("SYSCALL")));

        // 32-bit mode: IA-32 instructions valid, BCD instructions still valid, 64-bit absent
        viewModel.selectedBitnessProperty().set(ArchitectureBitness.BITS_32);
        assertTrue(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("MOV")));
        assertTrue(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("AAA")));
        assertTrue(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("PUSHA")));
        assertTrue(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("BSF")));
        assertFalse(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("CQO")));
        assertFalse(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("SYSCALL")));

        // 64-bit mode: BCD / PUSHA / POPA are invalid in Long Mode; CQO, SYSCALL, BSF valid
        viewModel.selectedBitnessProperty().set(ArchitectureBitness.BITS_64);
        assertTrue(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("MOV")));
        assertFalse(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("AAA")));
        assertFalse(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("PUSHA")));
        assertTrue(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("BSF")));
        assertTrue(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("CQO")));
        assertTrue(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equalsIgnoreCase("SYSCALL")));
    }

    @Test
    void filtersRegistersByBitness() {
        // All modes
        assertTrue(viewModel.getFilteredRegisters().stream().anyMatch(r -> r.name().equalsIgnoreCase("AX")));
        assertTrue(viewModel.getFilteredRegisters().stream().anyMatch(r -> r.name().equalsIgnoreCase("EAX")));
        assertTrue(viewModel.getFilteredRegisters().stream().anyMatch(r -> r.name().equalsIgnoreCase("RAX")));

        // 16-bit mode: AX present, 32/64-bit registers excluded
        viewModel.selectedBitnessProperty().set(ArchitectureBitness.BITS_16);
        assertTrue(viewModel.getFilteredRegisters().stream().anyMatch(r -> r.name().equalsIgnoreCase("AX")));
        assertFalse(viewModel.getFilteredRegisters().stream().anyMatch(r -> r.name().equalsIgnoreCase("EAX")));
        assertFalse(viewModel.getFilteredRegisters().stream().anyMatch(r -> r.name().equalsIgnoreCase("RAX")));
        assertFalse(viewModel.getFilteredRegisters().stream().anyMatch(r -> r.name().equalsIgnoreCase("R8")));

        // 32-bit mode: EAX present, 16/64-bit excluded
        viewModel.selectedBitnessProperty().set(ArchitectureBitness.BITS_32);
        assertTrue(viewModel.getFilteredRegisters().stream().anyMatch(r -> r.name().equalsIgnoreCase("EAX")));
        assertFalse(viewModel.getFilteredRegisters().stream().anyMatch(r -> r.name().equalsIgnoreCase("AX")));
        assertFalse(viewModel.getFilteredRegisters().stream().anyMatch(r -> r.name().equalsIgnoreCase("RAX")));
        assertFalse(viewModel.getFilteredRegisters().stream().anyMatch(r -> r.name().equalsIgnoreCase("R8")));

        // 64-bit mode: RAX and R8..R15 present, narrower excluded
        viewModel.selectedBitnessProperty().set(ArchitectureBitness.BITS_64);
        assertTrue(viewModel.getFilteredRegisters().stream().anyMatch(r -> r.name().equalsIgnoreCase("RAX")));
        assertTrue(viewModel.getFilteredRegisters().stream().anyMatch(r -> r.name().equalsIgnoreCase("R8")));
        assertFalse(viewModel.getFilteredRegisters().stream().anyMatch(r -> r.name().equalsIgnoreCase("EAX")));
        assertFalse(viewModel.getFilteredRegisters().stream().anyMatch(r -> r.name().equalsIgnoreCase("AX")));
    }

    @Test
    void filtersRegistersByGroup() {
        viewModel.selectedRegisterGroupProperty().set(RegisterGroup.G3_SEGMENT);
        assertFalse(viewModel.getFilteredRegisters().isEmpty());
        for (RegisterEntry reg : viewModel.getFilteredRegisters()) {
            assertEquals(RegisterGroup.G3_SEGMENT, reg.group());
        }
    }

    @Test
    void filtersSyntaxItemsBySyntaxClass() {
        viewModel.selectedSyntaxClassProperty().set(SyntaxClass.DIRECTIVE);
        assertFalse(viewModel.getFilteredSyntaxItems().isEmpty());
        for (SyntaxItem item : viewModel.getFilteredSyntaxItems()) {
            assertEquals(SyntaxClass.DIRECTIVE, item.syntaxClass());
        }
    }

    @Test
    void filtersSyntaxItemsByDialect() {
        viewModel.selectedDialectProperty().set(Dialect.MASM);
        assertFalse(viewModel.getFilteredSyntaxItems().isEmpty());
        for (SyntaxItem item : viewModel.getFilteredSyntaxItems()) {
            assertTrue(item.supportsDialect(Dialect.MASM));
        }
    }

    @Test
    void resetFiltersRestoresAllFilters() {
        viewModel.selectedCategoryProperty().set(InstructionCategory.STRINGS);
        viewModel.selectedBitnessProperty().set(ArchitectureBitness.BITS_64);
        viewModel.selectedRegisterGroupProperty().set(RegisterGroup.G1_GENERAL);
        viewModel.selectedSyntaxClassProperty().set(SyntaxClass.DIRECTIVE);
        viewModel.selectedDialectProperty().set(Dialect.NASM);
        viewModel.searchQueryProperty().set("test");

        assertTrue(viewModel.isFilterActive());

        viewModel.resetFilters();

        assertFalse(viewModel.isFilterActive());
        assertEquals("", viewModel.searchQueryProperty().get());
        assertNull(viewModel.selectedCategoryProperty().get());
        assertEquals(ArchitectureBitness.ALL, viewModel.selectedBitnessProperty().get());
        assertNull(viewModel.selectedRegisterGroupProperty().get());
        assertNull(viewModel.selectedSyntaxClassProperty().get());
        assertEquals(Dialect.COMMON, viewModel.selectedDialectProperty().get());
    }
}
