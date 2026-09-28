package io.github.dinamo541.idearm.language.knowledge;

import io.github.dinamo541.idearm.language.catalog.InstructionCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Thread-safe in-memory Academic Knowledge Corpus.
 *
 * <p>Loaded lazily on first access and warmed up asynchronously on workbench startup (ADR-013).
 */
public final class Corpus {

    private static final class Holder {
        static final Corpus INSTANCE = CorpusLoader.load();
    }

    public static Corpus get() {
        return Holder.INSTANCE;
    }

    public static void warmUp() {
        Thread.ofVirtual().start(Corpus::get);
    }

    private final Map<String, InstructionEntry> instructionsById = new LinkedHashMap<>();
    private final Map<String, InstructionEntry> instructionsByMnemonic = new LinkedHashMap<>();
    private final Map<String, List<InstructionEntry>> instructionsByMnemonicList = new LinkedHashMap<>();
    private final Map<String, String> aliasToMnemonic = new LinkedHashMap<>();
    private final Set<String> knownMnemonics = new TreeSet<>();
    private final Map<Dialect, Set<String>> recognizedAssemblerMnemonics = new LinkedHashMap<>();
    private final Map<String, RegisterEntry> registersById = new LinkedHashMap<>();
    private final Map<String, RegisterEntry> registersByName = new LinkedHashMap<>();
    private final Set<String> allRegisterNames = new TreeSet<>();
    private final Map<String, SyntaxItem> syntaxItemsById = new LinkedHashMap<>();
    private final Map<String, List<SyntaxItem>> syntaxItemsByToken = new LinkedHashMap<>();
    private final Map<String, ServiceEntry> services = new LinkedHashMap<>();
    private final Map<String, List<ServiceEntry>> servicesByVector = new LinkedHashMap<>();
    private final List<VectorIndexEntry> vectorIndex = new ArrayList<>();
    private final Map<Integer, VectorIndexEntry> vectorIndexByNumber = new LinkedHashMap<>();
    private final Map<String, VectorIndexEntry> vectorIndexByHex = new LinkedHashMap<>();
    private final Map<String, ConceptEntry> concepts = new LinkedHashMap<>();
    private final Map<String, LearningPathEntry> learningPaths = new LinkedHashMap<>();
    private final Map<String, ExampleEntry> examples = new LinkedHashMap<>();
    private final Map<String, SourceReference> sources = new LinkedHashMap<>();
    private final Map<String, String[]> translations = new LinkedHashMap<>();

    private final ReferenceView referenceView = new ReferenceView(this);
    private final HoverView hoverView = new HoverView(this);
    private final CompletionView completionView = new CompletionView(this);
    private final AnalysisView analysisView = new AnalysisView(this);
    private final DebugView debugView = new DebugView(this);

    public Corpus() {
    }

    // --- Package-private builder methods for CorpusLoader ---

    void addInstruction(InstructionEntry entry) {
        instructionsById.put(entry.id(), entry);
        String upperMnemonic = entry.mnemonic().toUpperCase(Locale.ROOT);
        instructionsByMnemonic.putIfAbsent(upperMnemonic, entry);
        instructionsByMnemonicList.computeIfAbsent(upperMnemonic, k -> new ArrayList<>()).add(entry);
        knownMnemonics.add(upperMnemonic);

        for (AliasDeclaration alias : entry.aliases()) {
            String upperAlias = alias.name().toUpperCase(Locale.ROOT);
            aliasToMnemonic.put(upperAlias, upperMnemonic);
            knownMnemonics.add(upperAlias);
        }
    }

    void addRecognizedAssemblerMnemonic(Dialect dialect, String mnemonic) {
        if (mnemonic == null || mnemonic.isBlank()) return;
        recognizedAssemblerMnemonics
                .computeIfAbsent(dialect, d -> new TreeSet<>())
                .add(mnemonic.toUpperCase(Locale.ROOT));
    }

    void addRegister(RegisterEntry reg) {
        registersById.put(reg.id(), reg);
        String upperName = reg.name().toUpperCase(Locale.ROOT);
        registersByName.put(upperName, reg);
        allRegisterNames.add(upperName);
        for (RegisterView view : reg.views()) {
            String viewUpper = view.name().toUpperCase(Locale.ROOT);
            registersByName.putIfAbsent(viewUpper, reg);
            allRegisterNames.add(viewUpper);
        }
    }

    void addSyntaxItem(SyntaxItem item) {
        syntaxItemsById.put(item.id(), item);
        String tokenUpper = item.token().toUpperCase(Locale.ROOT);
        syntaxItemsByToken.computeIfAbsent(tokenUpper, k -> new ArrayList<>()).add(item);
    }

    void addService(ServiceEntry service) {
        services.put(service.id(), service);
        String vKey = service.vector().toUpperCase(Locale.ROOT);
        servicesByVector.computeIfAbsent(vKey, k -> new ArrayList<>()).add(service);
    }

    void addVectorIndexEntry(VectorIndexEntry entry) {
        vectorIndex.add(entry);
        vectorIndexByNumber.put(entry.vectorNumber(), entry);
        vectorIndexByHex.put(entry.vector().toUpperCase(Locale.ROOT), entry);
    }

    void addConcept(ConceptEntry concept) {
        concepts.put(concept.id(), concept);
    }

    void addLearningPath(LearningPathEntry path) {
        learningPaths.put(path.id(), path);
    }

    void addExample(ExampleEntry example) {
        examples.put(example.id(), example);
    }

    void addTranslation(String source, String english, String spanish) {
        if (source == null || source.isBlank()) return;
        translations.put(source, new String[] {english, spanish});
    }

    /**
     * The given corpus text in the language of the locale. Fields such as a register's conventional use, a flag's
     * meaning or a service's inputs hold a single text, written in whichever language its author used; the
     * translation table in {@code text/translations.json} is keyed by that text and gives both languages. A text
     * the table does not know is returned as it is, so an untranslated entry still shows something.
     */
    public String localize(String text, Locale locale) {
        if (text == null || text.isBlank()) return text;
        String[] pair = translations.get(text);
        if (pair == null) return text;
        boolean spanish = locale != null && "es".equalsIgnoreCase(locale.getLanguage());
        String chosen = spanish ? pair[1] : pair[0];
        return chosen == null || chosen.isBlank() ? text : chosen;
    }

    /** Whether the translation table has an entry for this exact text. */
    public boolean hasTranslation(String text) {
        return text != null && translations.containsKey(text);
    }

    void addSource(SourceReference source) {
        sources.put(source.id(), source);
    }

    // --- Query API ---

    public Optional<InstructionEntry> findInstruction(String mnemonic) {
        if (mnemonic == null || mnemonic.isBlank()) return Optional.empty();
        if (instructionsById.containsKey(mnemonic)) {
            return Optional.of(instructionsById.get(mnemonic));
        }
        String upper = mnemonic.trim().toUpperCase(Locale.ROOT);
        InstructionEntry entry = instructionsByMnemonic.get(upper);
        if (entry != null) return Optional.of(entry);

        String target = aliasToMnemonic.get(upper);
        if (target != null) {
            return Optional.ofNullable(instructionsByMnemonic.get(target));
        }
        return Optional.empty();
    }

    public List<InstructionEntry> findAllByMnemonic(String mnemonic) {
        if (mnemonic == null || mnemonic.isBlank()) return List.of();
        String upper = mnemonic.trim().toUpperCase(Locale.ROOT);
        List<InstructionEntry> list = instructionsByMnemonicList.get(upper);
        if (list != null && !list.isEmpty()) return Collections.unmodifiableList(list);
        String target = aliasToMnemonic.get(upper);
        if (target != null) {
            List<InstructionEntry> tlist = instructionsByMnemonicList.get(target);
            if (tlist != null) return Collections.unmodifiableList(tlist);
        }
        return List.of();
    }

    public boolean isKnownInstruction(String mnemonic) {
        return findInstruction(mnemonic).isPresent();
    }

    public List<InstructionEntry> getAllInstructions() {
        return List.copyOf(instructionsById.values());
    }

    public Set<String> knownMnemonics() {
        return Collections.unmodifiableSet(knownMnemonics);
    }

    public Set<String> getRecognizedAssemblerMnemonics(Dialect dialect) {
        if (dialect == null || dialect == Dialect.UNKNOWN || dialect == Dialect.COMMON) {
            Set<String> all = new TreeSet<>();
            for (Set<String> s : recognizedAssemblerMnemonics.values()) {
                all.addAll(s);
            }
            return Collections.unmodifiableSet(all);
        }
        Set<String> set = recognizedAssemblerMnemonics.get(dialect);
        return set != null ? Collections.unmodifiableSet(set) : Set.of();
    }

    public List<InstructionEntry> getByCategory(InstructionCategory category) {
        if (category == null) return getAllInstructions();
        return instructionsById.values().stream()
                .filter(i -> i.category() == category)
                .toList();
    }

    public List<InstructionEntry> getForCpu(String targetCpu) {
        if (targetCpu == null || targetCpu.isBlank()) return getAllInstructions();
        CpuGeneration targetGen = CpuGeneration.parse(targetCpu);
        if (targetGen == CpuGeneration.UNKNOWN) return getAllInstructions();
        return instructionsById.values().stream()
                .filter(i -> i.minCpuGen().isSupportedOn(targetGen))
                .toList();
    }

    public List<InstructionEntry> searchStartingWith(String prefix) {
        if (prefix == null || prefix.isBlank()) return getAllInstructions();
        String upper = prefix.toUpperCase(Locale.ROOT).trim();
        List<InstructionEntry> result = new ArrayList<>();
        for (InstructionEntry entry : instructionsById.values()) {
            if (entry.mnemonic().startsWith(upper)) {
                result.add(entry);
                continue;
            }
            for (AliasDeclaration alias : entry.aliases()) {
                if (alias.name().startsWith(upper)) {
                    result.add(entry);
                    break;
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<InstructionEntry> filter(InstructionCategory category, String targetCpu, String searchPrefix) {
        CpuGeneration targetGen = (targetCpu != null && !targetCpu.isBlank()) ? CpuGeneration.parse(targetCpu) : null;
        return instructionsById.values().stream()
                .filter(i -> category == null || i.category() == category)
                .filter(i -> targetGen == null || targetGen == CpuGeneration.UNKNOWN || i.minCpuGen().isSupportedOn(targetGen))
                .filter(i -> i.matches(searchPrefix))
                .toList();
    }

    public List<String> suggest(String mnemonic) {
        if (mnemonic == null || mnemonic.isBlank()) return List.of();
        String word = mnemonic.trim().toUpperCase(Locale.ROOT);
        if (knownMnemonics.contains(word)) return List.of();

        int threshold = word.length() <= 4 ? 1 : 2;
        return knownMnemonics.stream()
                .map(candidate -> Map.entry(candidate, editDistance(word, candidate)))
                .filter(entry -> entry.getValue() <= threshold)
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
    }

    public Optional<RegisterEntry> findRegister(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) return Optional.empty();
        String upper = nameOrId.trim().toUpperCase(Locale.ROOT);
        RegisterEntry reg = registersByName.get(upper);
        if (reg != null) return Optional.of(reg);
        return Optional.ofNullable(registersById.get(nameOrId.trim()));
    }

    public List<RegisterEntry> getAllRegisters() {
        return List.copyOf(registersById.values());
    }

    /**
     * The whole width family of a register, widest first: the 64-bit container, then its 32, 16 and 8-bit views.
     *
     * <p>A student who opens AX must be able to see that the same storage is called RAX at 64 bits, EAX at 32 and
     * AH/AL at 8, so the family is resolved from whichever member is asked for: the chain of {@code parentId} is
     * walked up to the widest register, and that one contributes itself plus its declared views.
     *
     * <p>The result is empty only when the register is unknown. A register with no wider container and no views,
     * such as a segment register, is a family of one.
     */
    public List<RegisterView> registerFamily(String nameOrId) {
        return findRegister(nameOrId).map(this::registerFamily).orElseGet(List::of);
    }

    /** The width family of this register; see {@link #registerFamily(String)}. */
    public List<RegisterView> registerFamily(RegisterEntry register) {
        if (register == null) return List.of();
        RegisterEntry root = widestOf(register);
        List<RegisterView> family = new ArrayList<>();
        family.add(new RegisterView(root.id(), root.name(), root.sizeBits(), 0));
        for (RegisterView view : root.views()) {
            if (family.stream().noneMatch(existing -> existing.id().equals(view.id()))) {
                family.add(view);
            }
        }
        // Widest first, and within one width the high half before the low one, the way a register is drawn.
        family.sort(Comparator.comparingInt(RegisterView::sizeBits).reversed()
                .thenComparing(Comparator.comparingInt(RegisterView::offsetBits).reversed()));
        return List.copyOf(family);
    }

    /**
     * The widest register that contains this one: RAX for AL, AX or EAX. Follows {@code parentId} and stops on a
     * register with no parent, on an unknown parent, or on a cycle in the data.
     */
    public RegisterEntry widestOf(RegisterEntry register) {
        if (register == null) return null;
        RegisterEntry current = register;
        Set<String> seen = new LinkedHashSet<>();
        seen.add(current.id());
        while (current.parentId() != null && !current.parentId().isBlank()) {
            RegisterEntry parent = registersById.get(current.parentId());
            if (parent == null || !seen.add(parent.id())) break;
            current = parent;
        }
        return current;
    }

    public Set<String> getAllRegisterNames() {
        return Collections.unmodifiableSet(allRegisterNames);
    }

    public Optional<SyntaxItem> findSyntaxItem(String tokenOrId) {
        if (tokenOrId == null || tokenOrId.isBlank()) return Optional.empty();
        String trimmed = tokenOrId.trim();
        SyntaxItem byId = syntaxItemsById.get(trimmed);
        if (byId != null) return Optional.of(byId);
        List<SyntaxItem> byToken = syntaxItemsByToken.get(trimmed.toUpperCase(Locale.ROOT));
        if (byToken != null && !byToken.isEmpty()) {
            return Optional.of(byToken.getFirst());
        }
        return Optional.empty();
    }

    public List<SyntaxItem> findSyntaxItemsByToken(String token) {
        if (token == null || token.isBlank()) return List.of();
        List<SyntaxItem> list = syntaxItemsByToken.get(token.trim().toUpperCase(Locale.ROOT));
        return list != null ? Collections.unmodifiableList(list) : List.of();
    }

    public List<SyntaxItem> getAllSyntaxItems() {
        return List.copyOf(syntaxItemsById.values());
    }

    public List<ServiceEntry> getAllServices() {
        return List.copyOf(services.values());
    }

    public Optional<ServiceEntry> findService(String id) {
        return Optional.ofNullable(services.get(id));
    }

    public Optional<ServiceEntry> findService(String vector, String selectorValue) {
        if (vector == null || selectorValue == null) return Optional.empty();
        String vNorm = vector.trim().toUpperCase(Locale.ROOT);
        String sNorm = selectorValue.trim().toUpperCase(Locale.ROOT);
        List<ServiceEntry> list = servicesByVector.get(vNorm);
        if (list == null) return Optional.empty();
        for (ServiceEntry s : list) {
            if (s.selector() != null && s.selector().value().equalsIgnoreCase(sNorm)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    public List<ServiceEntry> findServicesByVector(String vector) {
        if (vector == null) return List.of();
        String vNorm = vector.trim().toUpperCase(Locale.ROOT);
        List<ServiceEntry> list = servicesByVector.get(vNorm);
        return list != null ? Collections.unmodifiableList(list) : List.of();
    }

    public List<VectorIndexEntry> getVectorIndex() {
        return Collections.unmodifiableList(vectorIndex);
    }

    public Optional<VectorIndexEntry> findVector(int number) {
        return Optional.ofNullable(vectorIndexByNumber.get(number));
    }

    public Optional<VectorIndexEntry> findVector(String vectorHex) {
        if (vectorHex == null) return Optional.empty();
        return Optional.ofNullable(vectorIndexByHex.get(vectorHex.trim().toUpperCase(Locale.ROOT)));
    }

    public List<ConceptEntry> getAllConcepts() {
        return List.copyOf(concepts.values());
    }

    public Optional<ConceptEntry> findConcept(String id) {
        return Optional.ofNullable(concepts.get(id));
    }

    public List<LearningPathEntry> getAllLearningPaths() {
        return List.copyOf(learningPaths.values());
    }

    public List<ExampleEntry> getAllExamples() {
        return List.copyOf(examples.values());
    }

    public List<SourceReference> getAllSources() {
        return List.copyOf(sources.values());
    }

    // --- Views ---

    public ReferenceView referenceView() {
        return referenceView;
    }

    public HoverView hoverView() {
        return hoverView;
    }

    public CompletionView completionView() {
        return completionView;
    }

    public AnalysisView analysisView() {
        return analysisView;
    }

    public DebugView debugView() {
        return debugView;
    }

    private static int editDistance(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 2) return Integer.MAX_VALUE;

        int[][] distance = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) distance[i][0] = i;
        for (int j = 0; j <= b.length(); j++) distance[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int substitution = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                distance[i][j] = Math.min(
                        Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1),
                        distance[i - 1][j - 1] + substitution);
                boolean transposed = i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1);
                if (transposed) {
                    distance[i][j] = Math.min(distance[i][j], distance[i - 2][j - 2] + 1);
                }
            }
        }
        return distance[a.length()][b.length()];
    }
}
