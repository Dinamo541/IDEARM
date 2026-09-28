package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.language.catalog.CpuLevel;
import io.github.dinamo541.idearm.language.catalog.InstructionCatalog;
import io.github.dinamo541.idearm.language.catalog.InstructionCategory;
import io.github.dinamo541.idearm.language.catalog.InstructionInfo;
import java.util.List;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * ViewModel for the Academic Assistance Mnemonics Dictionary.
 * Manages instruction filtering across categories, CPU generations, and search terms,
 * as well as sequential and direct instruction navigation.
 */
public final class MnemonicsDictionaryViewModel {

    private final ObservableList<InstructionInfo> allInstructions = FXCollections.observableArrayList();
    private final ObservableList<InstructionInfo> filteredInstructions = FXCollections.observableArrayList();
    private final ObservableList<InstructionInfo> unmodifiableFiltered = FXCollections.unmodifiableObservableList(filteredInstructions);

    private final StringProperty searchQuery = new SimpleStringProperty("");
    private final ObjectProperty<InstructionCategory> selectedCategory = new SimpleObjectProperty<>();
    private final ObjectProperty<CpuLevel> selectedCpu = new SimpleObjectProperty<>();
    private final ObjectProperty<InstructionInfo> selectedInstruction = new SimpleObjectProperty<>();

    private final IntegerProperty matchCount = new SimpleIntegerProperty(0);
    private final BooleanProperty hasMatches = new SimpleBooleanProperty(false);
    private final BooleanProperty canSelectPrevious = new SimpleBooleanProperty(false);
    private final BooleanProperty canSelectNext = new SimpleBooleanProperty(false);

    public MnemonicsDictionaryViewModel() {
        this(InstructionCatalog.getAll());
    }

    public MnemonicsDictionaryViewModel(List<InstructionInfo> initialInstructions) {
        if (initialInstructions != null) {
            allInstructions.setAll(initialInstructions);
        }

        searchQuery.addListener((obs, oldVal, newVal) -> reapplyFilter());
        selectedCategory.addListener((obs, oldVal, newVal) -> reapplyFilter());
        selectedCpu.addListener((obs, oldVal, newVal) -> reapplyFilter());
        selectedInstruction.addListener((obs, oldVal, newVal) -> updateNavigationState());

        reapplyFilter();
        if (!filteredInstructions.isEmpty()) {
            selectedInstruction.set(filteredInstructions.getFirst());
        }
    }

    public ObservableList<InstructionInfo> getFilteredInstructions() {
        return unmodifiableFiltered;
    }

    public StringProperty searchQueryProperty() {
        return searchQuery;
    }

    public ObjectProperty<InstructionCategory> selectedCategoryProperty() {
        return selectedCategory;
    }

    public ObjectProperty<CpuLevel> selectedCpuProperty() {
        return selectedCpu;
    }

    public ObjectProperty<InstructionInfo> selectedInstructionProperty() {
        return selectedInstruction;
    }

    public ReadOnlyIntegerProperty matchCountProperty() {
        return matchCount;
    }

    public ReadOnlyBooleanProperty hasMatchesProperty() {
        return hasMatches;
    }

    public ReadOnlyBooleanProperty canSelectPreviousProperty() {
        return canSelectPrevious;
    }

    public ReadOnlyBooleanProperty canSelectNextProperty() {
        return canSelectNext;
    }

    public int totalCount() {
        return allInstructions.size();
    }

    public boolean isFilterActive() {
        String query = searchQuery.get();
        return (query != null && !query.isBlank()) || selectedCategory.get() != null || selectedCpu.get() != null;
    }

    public void clearSearch() {
        searchQuery.set("");
    }

    public void resetFilters() {
        searchQuery.set("");
        selectedCategory.set(null);
        selectedCpu.set(null);
    }

    public void selectNext() {
        if (!canSelectNext.get()) {
            return;
        }
        int currentIndex = filteredInstructions.indexOf(selectedInstruction.get());
        if (currentIndex >= 0 && currentIndex < filteredInstructions.size() - 1) {
            selectedInstruction.set(filteredInstructions.get(currentIndex + 1));
        }
    }

    public void selectPrevious() {
        if (!canSelectPrevious.get()) {
            return;
        }
        int currentIndex = filteredInstructions.indexOf(selectedInstruction.get());
        if (currentIndex > 0) {
            selectedInstruction.set(filteredInstructions.get(currentIndex - 1));
        }
    }

    public void selectInstruction(InstructionInfo instruction) {
        if (instruction == null) {
            return;
        }
        if (!filteredInstructions.contains(instruction) && allInstructions.contains(instruction)) {
            resetFilters();
        }
        if (filteredInstructions.contains(instruction)) {
            selectedInstruction.set(instruction);
        }
    }

    private void reapplyFilter() {
        String query = searchQuery.get();
        InstructionCategory category = selectedCategory.get();
        CpuLevel cpu = selectedCpu.get();

        List<InstructionInfo> matches = allInstructions.stream()
                .filter(info -> category == null || info.category() == category)
                .filter(info -> cpu == null || info.minCpu().level() <= cpu.level())
                .filter(info -> info.matches(query))
                .toList();

        filteredInstructions.setAll(matches);
        matchCount.set(matches.size());
        hasMatches.set(!matches.isEmpty());

        InstructionInfo current = selectedInstruction.get();
        if (matches.isEmpty()) {
            selectedInstruction.set(null);
        } else if (current == null || !matches.contains(current)) {
            selectedInstruction.set(matches.getFirst());
        }

        updateNavigationState();
    }

    private void updateNavigationState() {
        InstructionInfo current = selectedInstruction.get();
        if (current == null || filteredInstructions.isEmpty()) {
            canSelectPrevious.set(false);
            canSelectNext.set(false);
            return;
        }
        int index = filteredInstructions.indexOf(current);
        canSelectPrevious.set(index > 0);
        canSelectNext.set(index >= 0 && index < filteredInstructions.size() - 1);
    }
}
