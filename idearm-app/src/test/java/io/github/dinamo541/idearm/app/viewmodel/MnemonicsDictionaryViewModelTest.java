package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.language.catalog.CpuLevel;
import io.github.dinamo541.idearm.language.catalog.InstructionCategory;
import io.github.dinamo541.idearm.language.catalog.InstructionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MnemonicsDictionaryViewModelTest {

    private MnemonicsDictionaryViewModel viewModel;

    @BeforeEach
    void setUp() {
        viewModel = new MnemonicsDictionaryViewModel();
    }

    @Test
    void initializesWithAllInstructionsAndSelectsFirst() {
        assertTrue(viewModel.totalCount() > 80, "Catalog should have more than 80 instructions");
        assertEquals(viewModel.totalCount(), viewModel.matchCountProperty().get());
        assertTrue(viewModel.hasMatchesProperty().get());
        assertNotNull(viewModel.selectedInstructionProperty().get());
        assertEquals(viewModel.getFilteredInstructions().getFirst(), viewModel.selectedInstructionProperty().get());
        assertFalse(viewModel.canSelectPreviousProperty().get());
        assertTrue(viewModel.canSelectNextProperty().get());
    }

    @Test
    void filtersByCategory() {
        viewModel.selectedCategoryProperty().set(InstructionCategory.ARITHMETIC);

        assertFalse(viewModel.getFilteredInstructions().isEmpty());
        for (InstructionInfo info : viewModel.getFilteredInstructions()) {
            assertEquals(InstructionCategory.ARITHMETIC, info.category());
        }
        assertTrue(viewModel.isFilterActive());
    }

    @Test
    void filtersByCpuLevel() {
        viewModel.selectedCpuProperty().set(CpuLevel.CPU_8086);

        assertFalse(viewModel.getFilteredInstructions().isEmpty());
        for (InstructionInfo info : viewModel.getFilteredInstructions()) {
            assertTrue(info.minCpu().level() <= CpuLevel.CPU_8086.level());
        }
    }

    @Test
    void filtersBySearchTextAccentInsensitive() {
        viewModel.searchQueryProperty().set("división");

        assertFalse(viewModel.getFilteredInstructions().isEmpty());
        assertTrue(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equals("DIV")));

        viewModel.searchQueryProperty().set("division");
        assertFalse(viewModel.getFilteredInstructions().isEmpty());
        assertTrue(viewModel.getFilteredInstructions().stream().anyMatch(i -> i.mnemonic().equals("DIV")));
    }

    @Test
    void combinesMultiCriteriaFilters() {
        viewModel.selectedCategoryProperty().set(InstructionCategory.ARITHMETIC);
        viewModel.selectedCpuProperty().set(CpuLevel.CPU_8086);
        viewModel.searchQueryProperty().set("ADD");

        assertFalse(viewModel.getFilteredInstructions().isEmpty());
        for (InstructionInfo info : viewModel.getFilteredInstructions()) {
            assertEquals(InstructionCategory.ARITHMETIC, info.category());
            assertTrue(info.minCpu().level() <= CpuLevel.CPU_8086.level());
        }
    }

    @Test
    void navigatesNextAndPrevious() {
        InstructionInfo first = viewModel.selectedInstructionProperty().get();
        assertNotNull(first);

        viewModel.selectNext();
        InstructionInfo second = viewModel.selectedInstructionProperty().get();
        assertNotEquals(first, second);
        assertTrue(viewModel.canSelectPreviousProperty().get());

        viewModel.selectPrevious();
        assertEquals(first, viewModel.selectedInstructionProperty().get());
        assertFalse(viewModel.canSelectPreviousProperty().get());
    }

    @Test
    void clearsAndResetsFilters() {
        viewModel.selectedCategoryProperty().set(InstructionCategory.STRINGS);
        viewModel.searchQueryProperty().set("MOVS");
        assertTrue(viewModel.isFilterActive());

        viewModel.clearSearch();
        assertEquals("", viewModel.searchQueryProperty().get());
        assertEquals(InstructionCategory.STRINGS, viewModel.selectedCategoryProperty().get());

        viewModel.resetFilters();
        assertFalse(viewModel.isFilterActive());
        assertEquals("", viewModel.searchQueryProperty().get());
        assertNull(viewModel.selectedCategoryProperty().get());
        assertNull(viewModel.selectedCpuProperty().get());
        assertEquals(viewModel.totalCount(), viewModel.matchCountProperty().get());
    }

    @Test
    void handlesEmptySearchResults() {
        viewModel.searchQueryProperty().set("xyzNonExistentMnemonic999");

        assertEquals(0, viewModel.matchCountProperty().get());
        assertFalse(viewModel.hasMatchesProperty().get());
        assertNull(viewModel.selectedInstructionProperty().get());
        assertFalse(viewModel.canSelectPreviousProperty().get());
        assertFalse(viewModel.canSelectNextProperty().get());
    }

    @Test
    void selectInstructionResetsFilterIfTargetIsHidden() {
        viewModel.selectedCategoryProperty().set(InstructionCategory.STRINGS);
        InstructionInfo mov = viewModel.getFilteredInstructions().stream()
                .filter(i -> i.mnemonic().equals("MOV"))
                .findFirst()
                .orElse(null);
        assertNull(mov, "MOV should not be in STRINGS category");

        InstructionInfo movFromCatalog = io.github.dinamo541.idearm.language.catalog.InstructionCatalog.find("MOV").orElseThrow();
        viewModel.selectInstruction(movFromCatalog);

        assertEquals(movFromCatalog, viewModel.selectedInstructionProperty().get());
        assertNull(viewModel.selectedCategoryProperty().get(), "Filter should have been reset");
    }

    @Test
    void handlesNullOrForeignInstructionSelectionGracefully() {
        InstructionInfo current = viewModel.selectedInstructionProperty().get();
        assertNotNull(current);

        assertDoesNotThrow(() -> viewModel.selectInstruction(null));
        assertEquals(current, viewModel.selectedInstructionProperty().get());

        InstructionInfo foreign = new InstructionInfo("CUSTOM", "Custom", "Personalizado", java.util.List.of(), null, null, "", "", "");
        assertDoesNotThrow(() -> viewModel.selectInstruction(foreign));
        assertEquals(current, viewModel.selectedInstructionProperty().get());
    }
}
