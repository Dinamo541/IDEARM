package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.application.knowledge.KnowledgeEntityKind;
import io.github.dinamo541.idearm.application.knowledge.KnowledgeSearchResult;
import io.github.dinamo541.idearm.application.knowledge.QueryKnowledge;
import io.github.dinamo541.idearm.application.knowledge.SearchResultGroup;
import io.github.dinamo541.idearm.language.catalog.CpuLevel;
import io.github.dinamo541.idearm.language.catalog.InstructionCatalog;
import io.github.dinamo541.idearm.language.catalog.InstructionCategory;
import io.github.dinamo541.idearm.language.catalog.InstructionInfo;
import io.github.dinamo541.idearm.language.knowledge.*;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.*;
import java.util.Locale;

/**
 * ViewModel for the Academic Center (Centro Académico).
 * Coordinates transversal search, topic trees, faceted filters, entity detail display,
 * and browser-style navigation history (back, forward, home, deep-links).
 */
public final class KnowledgeViewModel {

    private final Corpus corpus;
    private final QueryKnowledge queryEngine;

    // --- Search & Facets ---
    private final StringProperty searchQuery = new SimpleStringProperty("");
    private final StringProperty language = new SimpleStringProperty("es");
    private final ObjectProperty<Dialect> selectedDialect = new SimpleObjectProperty<>(Dialect.COMMON);
    private final ObjectProperty<CpuLevel> selectedCpu = new SimpleObjectProperty<>();
    private final ObjectProperty<InstructionCategory> selectedCategory = new SimpleObjectProperty<>();

    // --- Active Selection ---
    private final ObjectProperty<Object> selectedEntity = new SimpleObjectProperty<>();
    private final ObjectProperty<InstructionInfo> selectedInstruction = new SimpleObjectProperty<>();
    private final ObjectProperty<KnowledgeSearchResult> selectedSearchResult = new SimpleObjectProperty<>();

    // --- Observable Collections ---
    private final ObservableList<InstructionInfo> allInstructions = FXCollections.observableArrayList();
    private final ObservableList<InstructionInfo> filteredInstructions = FXCollections.observableArrayList();
    private final ObservableList<InstructionInfo> unmodifiableFiltered = FXCollections.unmodifiableObservableList(filteredInstructions);

    private final ObservableList<RegisterEntry> filteredRegisters = FXCollections.observableArrayList();
    private final ObservableList<RegisterEntry> unmodifiableRegisters = FXCollections.unmodifiableObservableList(filteredRegisters);

    private final ObservableList<SyntaxItem> filteredSyntaxItems = FXCollections.observableArrayList();
    private final ObservableList<SyntaxItem> unmodifiableSyntaxItems = FXCollections.unmodifiableObservableList(filteredSyntaxItems);

    private final ObservableList<SearchResultGroup> searchResultGroups = FXCollections.observableArrayList();
    private final ObservableList<SearchResultGroup> unmodifiableSearchGroups = FXCollections.unmodifiableObservableList(searchResultGroups);

    // --- Navigation & State Properties ---
    private final IntegerProperty matchCount = new SimpleIntegerProperty(0);
    private final BooleanProperty hasMatches = new SimpleBooleanProperty(false);
    private final BooleanProperty isSearching = new SimpleBooleanProperty(false);
    private final BooleanProperty canNavigateBack = new SimpleBooleanProperty(false);
    private final BooleanProperty canNavigateForward = new SimpleBooleanProperty(false);
    private final BooleanProperty canSelectPrevious = new SimpleBooleanProperty(false);
    private final BooleanProperty canSelectNext = new SimpleBooleanProperty(false);

    // --- Navigation History ---
    private final List<Object> history = new ArrayList<>();
    private int historyIndex = -1;
    private boolean isNavigatingHistory = false;

    public KnowledgeViewModel() {
        this(Corpus.get(), InstructionCatalog.getAll());
    }

    public KnowledgeViewModel(Corpus corpus, List<InstructionInfo> initialInstructions) {
        this.corpus = Objects.requireNonNull(corpus, "Corpus must not be null");
        this.queryEngine = new QueryKnowledge(corpus);

        if (initialInstructions != null) {
            allInstructions.setAll(initialInstructions);
        } else {
            allInstructions.setAll(InstructionCatalog.getAll());
        }

        searchQuery.addListener((obs, oldVal, newVal) -> onSearchOrFilterChanged());
        language.addListener((obs, oldVal, newVal) -> onSearchOrFilterChanged());
        selectedCategory.addListener((obs, oldVal, newVal) -> onSearchOrFilterChanged());
        selectedCpu.addListener((obs, oldVal, newVal) -> onSearchOrFilterChanged());
        selectedDialect.addListener((obs, oldVal, newVal) -> onSearchOrFilterChanged());

        selectedInstruction.addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isNavigatingHistory) {
                recordHistory(newVal);
            }
            updateInstructionNavigationState();
        });

        selectedEntity.addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !isNavigatingHistory) {
                recordHistory(newVal);
            }
            if (newVal instanceof InstructionInfo info) {
                selectedInstruction.set(info);
            } else if (newVal instanceof InstructionEntry entry) {
                InstructionCatalog.find(entry.mnemonic()).ifPresent(selectedInstruction::set);
            }
        });

        // Initialize state
        onSearchOrFilterChanged();
        if (!filteredInstructions.isEmpty()) {
            selectEntity(filteredInstructions.getFirst());
        }
    }

    // --- Properties ---

    public StringProperty searchQueryProperty() {
        return searchQuery;
    }

    public StringProperty languageProperty() {
        return language;
    }

    public ObjectProperty<Dialect> selectedDialectProperty() {
        return selectedDialect;
    }

    public ObjectProperty<CpuLevel> selectedCpuProperty() {
        return selectedCpu;
    }

    public ObjectProperty<InstructionCategory> selectedCategoryProperty() {
        return selectedCategory;
    }

    public ObjectProperty<Object> selectedEntityProperty() {
        return selectedEntity;
    }

    public ObjectProperty<InstructionInfo> selectedInstructionProperty() {
        return selectedInstruction;
    }

    public ObjectProperty<KnowledgeSearchResult> selectedSearchResultProperty() {
        return selectedSearchResult;
    }

    public ObservableList<InstructionInfo> getFilteredInstructions() {
        return unmodifiableFiltered;
    }

    /** Every register of the corpus that matches the current search text, widest members first. */
    public ObservableList<RegisterEntry> getFilteredRegisters() {
        return unmodifiableRegisters;
    }

    /** The special operands and signs (`:`, `[ ]`, OFFSET, PTR, @data...) that match the current search text. */
    public ObservableList<SyntaxItem> getFilteredSyntaxItems() {
        return unmodifiableSyntaxItems;
    }

    /**
     * The widths of one register, widest first, so a view can show that AX, EAX and RAX are the same storage.
     * Empty when the register is unknown.
     */
    /** A single-language corpus text in the given UI language; see {@link Corpus#localize}. */
    public String localize(String text, Locale locale) {
        return corpus.localize(text, locale);
    }

    public List<RegisterView> registerFamily(RegisterEntry register) {
        return corpus.registerFamily(register);
    }

    public ObservableList<SearchResultGroup> getSearchResultGroups() {
        return unmodifiableSearchGroups;
    }

    public ReadOnlyIntegerProperty matchCountProperty() {
        return matchCount;
    }

    public ReadOnlyBooleanProperty hasMatchesProperty() {
        return hasMatches;
    }

    public ReadOnlyBooleanProperty isSearchingProperty() {
        return isSearching;
    }

    public ReadOnlyBooleanProperty canNavigateBackProperty() {
        return canNavigateBack;
    }

    public ReadOnlyBooleanProperty canNavigateForwardProperty() {
        return canNavigateForward;
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

    // --- Navigation Actions ---

    public void clearSearch() {
        searchQuery.set("");
    }

    public void resetFilters() {
        searchQuery.set("");
        selectedCategory.set(null);
        selectedCpu.set(null);
        selectedDialect.set(Dialect.COMMON);
    }

    public void selectEntity(Object entity) {
        if (entity == null) return;
        selectedEntity.set(entity);
    }

    public void selectInstruction(InstructionInfo instruction) {
        if (instruction == null) return;
        if (!filteredInstructions.contains(instruction) && allInstructions.contains(instruction)) {
            resetFilters();
        }
        if (filteredInstructions.contains(instruction)) {
            selectedInstruction.set(instruction);
            selectedEntity.set(instruction);
        }
    }

    public void selectNext() {
        if (!canSelectNext.get()) return;
        int currentIndex = filteredInstructions.indexOf(selectedInstruction.get());
        if (currentIndex >= 0 && currentIndex < filteredInstructions.size() - 1) {
            selectInstruction(filteredInstructions.get(currentIndex + 1));
        }
    }

    public void selectPrevious() {
        if (!canSelectPrevious.get()) return;
        int currentIndex = filteredInstructions.indexOf(selectedInstruction.get());
        if (currentIndex > 0) {
            selectInstruction(filteredInstructions.get(currentIndex - 1));
        }
    }

    public void navigateBack() {
        if (historyIndex > 0) {
            isNavigatingHistory = true;
            try {
                historyIndex--;
                Object entity = history.get(historyIndex);
                selectedEntity.set(entity);
                if (entity instanceof InstructionInfo info) {
                    selectedInstruction.set(info);
                }
            } finally {
                isNavigatingHistory = false;
                updateHistoryNavigationState();
            }
        }
    }

    public void navigateForward() {
        if (historyIndex >= 0 && historyIndex < history.size() - 1) {
            isNavigatingHistory = true;
            try {
                historyIndex++;
                Object entity = history.get(historyIndex);
                selectedEntity.set(entity);
                if (entity instanceof InstructionInfo info) {
                    selectedInstruction.set(info);
                }
            } finally {
                isNavigatingHistory = false;
                updateHistoryNavigationState();
            }
        }
    }

    public void navigateHome() {
        resetFilters();
        if (!filteredInstructions.isEmpty()) {
            selectInstruction(filteredInstructions.getFirst());
        }
    }

    public boolean navigateTo(String idOrUri) {
        if (idOrUri == null || idOrUri.isBlank()) return false;
        String target = idOrUri.trim();
        if (target.startsWith("idearm://kb/")) {
            target = target.substring("idearm://kb/".length());
        }
        // Handle form hashes
        int hashIdx = target.indexOf('#');
        if (hashIdx >= 0) {
            target = target.substring(0, hashIdx);
        }

        // Try instruction
        var instrOpt = corpus.findInstruction(target);
        if (instrOpt.isPresent()) {
            selectEntity(instrOpt.get());
            return true;
        }

        // Try register
        var regOpt = corpus.findRegister(target);
        if (regOpt.isPresent()) {
            selectEntity(regOpt.get());
            return true;
        }

        // Try syntax
        var synOpt = corpus.findSyntaxItem(target);
        if (synOpt.isPresent()) {
            selectEntity(synOpt.get());
            return true;
        }

        // Try service
        var srvOpt = corpus.findService(target);
        if (srvOpt.isPresent()) {
            selectEntity(srvOpt.get());
            return true;
        }

        // Try concept
        var concOpt = corpus.findConcept(target);
        if (concOpt.isPresent()) {
            selectEntity(concOpt.get());
            return true;
        }

        return false;
    }

    // --- Internal State Management ---

    private void onSearchOrFilterChanged() {
        String query = searchQuery.get();
        boolean hasQuery = query != null && !query.trim().isEmpty();
        isSearching.set(hasQuery);

        if (hasQuery) {
            // Transversal multi-entity search
            List<SearchResultGroup> groups = queryEngine.search(query.trim(), language.get());
            searchResultGroups.setAll(groups);

            int totalMatches = groups.stream().mapToInt(g -> g.items().size()).sum();
            matchCount.set(totalMatches);
            hasMatches.set(totalMatches > 0);

            if (!groups.isEmpty() && !groups.getFirst().items().isEmpty()) {
                KnowledgeSearchResult top = groups.getFirst().items().getFirst();
                selectedSearchResult.set(top);
                selectEntity(top.rawEntity());
            } else {
                selectedSearchResult.set(null);
            }
        } else {
            searchResultGroups.clear();
        }

        // Filter instruction list for classical catalog view
        InstructionCategory category = selectedCategory.get();
        CpuLevel cpu = selectedCpu.get();

        List<InstructionInfo> matches = allInstructions.stream()
                .filter(info -> category == null || info.category() == category)
                .filter(info -> cpu == null || info.minCpu().level() <= cpu.level())
                .filter(info -> info.matches(query))
                .toList();

        filteredInstructions.setAll(matches);
        if (!hasQuery) {
            matchCount.set(matches.size());
            hasMatches.set(!matches.isEmpty());
        }

        InstructionInfo current = selectedInstruction.get();
        if (matches.isEmpty()) {
            if (!hasQuery) selectedInstruction.set(null);
        } else if (current == null || !matches.contains(current)) {
            if (!hasQuery) selectedInstruction.set(matches.getFirst());
        }

        filteredRegisters.setAll(matchingRegisters(query));
        filteredSyntaxItems.setAll(matchingSyntaxItems(query));

        updateInstructionNavigationState();
    }

    /**
     * The registers whose name, identifier or use mentions the query, widest first so the 64-bit container leads its
     * own 32 and 16-bit names. An empty query lists them all.
     */
    private List<RegisterEntry> matchingRegisters(String query) {
        String needle = normalize(query);
        return corpus.getAllRegisters().stream()
                .filter(reg -> needle.isEmpty()
                        || normalize(reg.name()).contains(needle)
                        || normalize(reg.id()).contains(needle)
                        || normalize(corpus.localize(reg.conventionalUse(), Locale.ENGLISH)).contains(needle)
                        || normalize(corpus.localize(reg.conventionalUse(), Locale.forLanguageTag("es"))).contains(needle)
                        || reg.views().stream().anyMatch(v -> normalize(v.name()).contains(needle)))
                .sorted(Comparator.comparingInt((RegisterEntry r) -> r.group().ordinal())
                        .thenComparingInt(r -> -r.sizeBits())
                        .thenComparing(RegisterEntry::name))
                .toList();
    }

    /** The signs, operators and directives whose token or summary mentions the query. An empty query lists them all. */
    private List<SyntaxItem> matchingSyntaxItems(String query) {
        String needle = normalize(query);
        return corpus.getAllSyntaxItems().stream()
                .filter(item -> needle.isEmpty()
                        || normalize(item.token()).contains(needle)
                        || normalize(item.id()).contains(needle)
                        || normalize(item.summaryEs()).contains(needle)
                        || normalize(item.summaryEn()).contains(needle))
                .sorted(Comparator.comparingInt((SyntaxItem i) -> i.syntaxClass().ordinal())
                        .thenComparing(SyntaxItem::token))
                .toList();
    }


    /** Lower case without accents, so "direccion" finds "dirección" and an empty or null text matches everything. */
    private static String normalize(String text) {
        if (text == null || text.isBlank()) return "";
        String decomposed = java.text.Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT),
                java.text.Normalizer.Form.NFD);
        StringBuilder plain = new StringBuilder(decomposed.length());
        decomposed.codePoints()
                .filter(cp -> Character.getType(cp) != Character.NON_SPACING_MARK
                        && Character.getType(cp) != Character.COMBINING_SPACING_MARK
                        && Character.getType(cp) != Character.ENCLOSING_MARK)
                .forEach(plain::appendCodePoint);
        return plain.toString();
    }

    private void recordHistory(Object entity) {
        if (entity == null) return;
        // Don't record consecutive duplicates
        if (historyIndex >= 0 && historyIndex < history.size() && history.get(historyIndex).equals(entity)) {
            return;
        }
        // Truncate forward history if navigating after a branch
        while (history.size() > historyIndex + 1) {
            history.removeLast();
        }
        history.add(entity);
        historyIndex = history.size() - 1;
        updateHistoryNavigationState();
    }

    private void updateHistoryNavigationState() {
        canNavigateBack.set(historyIndex > 0);
        canNavigateForward.set(historyIndex >= 0 && historyIndex < history.size() - 1);
    }

    private void updateInstructionNavigationState() {
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
