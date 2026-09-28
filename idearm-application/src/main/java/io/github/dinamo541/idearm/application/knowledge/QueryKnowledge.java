package io.github.dinamo541.idearm.application.knowledge;

import io.github.dinamo541.idearm.language.knowledge.*;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Transversal knowledge search query engine across all architectural entities.
 *
 * <p>Supports semantic synonym expansion (ES and EN), punctuation preservation,
 * alias resolution, and categorized result grouping.
 */
public final class QueryKnowledge {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private final Corpus corpus;

    public QueryKnowledge() {
        this(Corpus.get());
    }

    public QueryKnowledge(Corpus corpus) {
        this.corpus = Objects.requireNonNull(corpus, "Corpus must not be null");
    }

    /**
     * Executes transversal search across instructions, registers, syntax items,
     * interrupt services, and pedagogical concepts.
     *
     * @param rawQuery user input search string
     * @param language preferred UI language ("es" or "en")
     * @return grouped results by entity kind, ordered by relevance
     */
    public List<SearchResultGroup> search(String rawQuery, String language) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return List.of();
        }

        boolean isSpanish = "es".equalsIgnoreCase(language);
        String query = rawQuery.trim();
        String normalizedQuery = normalize(query);

        Map<KnowledgeEntityKind, List<KnowledgeSearchResult>> grouped = new EnumMap<>(KnowledgeEntityKind.class);

        // 1. Search Syntax Items (Preserves punctuation like ':' and '[')
        searchSyntaxItems(query, normalizedQuery, isSpanish, grouped);

        // 2. Search Registers and Flags
        searchRegisters(query, normalizedQuery, isSpanish, grouped);

        // 3. Search Instructions and Aliases
        searchInstructions(query, normalizedQuery, isSpanish, grouped);

        // 4. Search Interrupt Services
        searchServices(query, normalizedQuery, isSpanish, grouped);

        // 5. Search Concepts
        searchConcepts(query, normalizedQuery, isSpanish, grouped);

        // Build sorted result groups
        List<SearchResultGroup> resultGroups = new ArrayList<>();
        for (KnowledgeEntityKind kind : KnowledgeEntityKind.values()) {
            List<KnowledgeSearchResult> items = grouped.get(kind);
            if (items != null && !items.isEmpty()) {
                Collections.sort(items);
                resultGroups.add(new SearchResultGroup(kind, items));
            }
        }

        // Sort groups by the score of their top item
        resultGroups.sort((g1, g2) -> {
            int score1 = g1.items().isEmpty() ? 0 : g1.items().getFirst().score();
            int score2 = g2.items().isEmpty() ? 0 : g2.items().getFirst().score();
            return Integer.compare(score2, score1);
        });

        return Collections.unmodifiableList(resultGroups);
    }

    /**
     * Returns a flat list of all matching results ordered by score.
     */
    public List<KnowledgeSearchResult> searchFlat(String query, String language) {
        List<SearchResultGroup> groups = search(query, language);
        List<KnowledgeSearchResult> flat = new ArrayList<>();
        for (SearchResultGroup group : groups) {
            flat.addAll(group.items());
        }
        Collections.sort(flat);
        return Collections.unmodifiableList(flat);
    }

    private void searchSyntaxItems(String query, String norm, boolean isSpanish,
                                   Map<KnowledgeEntityKind, List<KnowledgeSearchResult>> grouped) {
        boolean matchColon = query.equals(":") || norm.equals("dos puntos") || norm.equals("dospuntos") || norm.equals("colon");
        boolean matchBrackets = query.equals("[") || query.equals("]") || query.equals("[]") ||
                norm.equals("corchetes") || norm.equals("corchete") || norm.equals("brackets") || norm.equals("bracket");

        for (SyntaxItem item : corpus.getAllSyntaxItems()) {
            int score = 0;
            String token = item.token();
            String normToken = normalize(token);
            String id = item.id();
            String summary = isSpanish ? item.summaryEs() : item.summaryEn();
            String desc = isSpanish ? item.descriptionEs() : item.descriptionEn();

            if (matchColon && (token.equals(":") || id.contains("colon"))) {
                score = 100;
            } else if (matchBrackets && (token.contains("[") || token.contains("]") || id.contains("bracket"))) {
                score = 100;
            } else if (token.equalsIgnoreCase(query)) {
                score = 100;
            } else if (token.startsWith(query)) {
                score = 85;
            } else if (normalize(id).contains(norm)) {
                score = 75;
            } else if (normalize(summary).contains(norm) || normalize(desc).contains(norm)) {
                score = 60;
            }

            if (score > 0) {
                String title = item.token();
                String subtitle = item.syntaxClass().name();
                addItem(grouped, KnowledgeEntityKind.SYNTAX,
                        new KnowledgeSearchResult(item.id(), title, subtitle, KnowledgeEntityKind.SYNTAX, summary, item, score));
            }
        }
    }

    private void searchRegisters(String query, String norm, boolean isSpanish,
                                 Map<KnowledgeEntityKind, List<KnowledgeSearchResult>> grouped) {
        boolean matchCarry = norm.equals("acarreo") || norm.equals("carry");
        boolean matchExtraSeg = norm.equals("segmento extra") || norm.equals("extra segment");
        boolean matchStack = norm.equals("stack") || norm.equals("pila");

        for (RegisterEntry reg : corpus.getAllRegisters()) {
            int score = 0;
            String name = reg.name();
            Locale textLocale = isSpanish ? Locale.forLanguageTag("es") : Locale.ENGLISH;
            String summary = corpus.localize(reg.conventionalUse(), textLocale);
            String matchedSubtitle = reg.group().description();

            // Direct register name match
            if (name.equalsIgnoreCase(query)) {
                score = 100;
            } else if (name.startsWith(query.toUpperCase(Locale.ROOT))) {
                score = 80;
            }

            // Subregister view match (e.g. AH, AL)
            for (RegisterView view : reg.views()) {
                if (view.name().equalsIgnoreCase(query)) {
                    score = Math.max(score, 95);
                    matchedSubtitle = view.name() + " (" + view.sizeBits() + "b sub-register of " + reg.name() + ")";
                }
            }

            // Flag field match (e.g. CF)
            for (FlagField field : reg.fields()) {
                if (field.name().equalsIgnoreCase(query) || field.id().equalsIgnoreCase(query)) {
                    score = Math.max(score, 95);
                    matchedSubtitle = "Flag " + field.name() + " (" + corpus.localize(field.meaning(), textLocale) + ")";
                }
                if (matchCarry && (field.name().equalsIgnoreCase("CF") || field.id().toLowerCase(Locale.ROOT).contains("cf"))) {
                    score = Math.max(score, 95);
                    matchedSubtitle = isSpanish ? "Bandera de acarreo (CF)" : "Carry Flag (CF)";
                }
            }

            if (matchCarry && (name.equalsIgnoreCase("FLAGS") || name.equalsIgnoreCase("EFLAGS") || name.equalsIgnoreCase("RFLAGS") || summary.contains("CF"))) {
                score = Math.max(score, 95);
                matchedSubtitle = isSpanish ? "Bandera de acarreo (CF)" : "Carry Flag (CF)";
            }

            // Semantic synonyms
            if (matchExtraSeg && (name.equalsIgnoreCase("ES") || reg.id().toLowerCase(Locale.ROOT).contains("es"))) {
                score = Math.max(score, 95);
                matchedSubtitle = isSpanish ? "Registro de segmento extra" : "Extra Segment Register";
            }
            if (matchStack && (name.equalsIgnoreCase("SS") || name.equalsIgnoreCase("SP") || name.equalsIgnoreCase("BP"))) {
                score = Math.max(score, 90);
                matchedSubtitle = isSpanish ? "Registro de pila (" + name + ")" : "Stack Register (" + name + ")";
            }

            if (score == 0 && normalize(summary).contains(norm)) {
                score = 55;
            }

            if (score > 0) {
                addItem(grouped, KnowledgeEntityKind.REGISTER,
                        new KnowledgeSearchResult(reg.id(), reg.name(), matchedSubtitle, KnowledgeEntityKind.REGISTER, summary, reg, score));
            }
        }
    }

    private void searchInstructions(String query, String norm, boolean isSpanish,
                                    Map<KnowledgeEntityKind, List<KnowledgeSearchResult>> grouped) {
        boolean matchCarry = norm.equals("acarreo") || norm.equals("carry");
        boolean matchStack = norm.equals("stack") || norm.equals("pila");

        for (InstructionEntry instr : corpus.getAllInstructions()) {
            int score = 0;
            String mnemonic = instr.mnemonic();
            String summary = isSpanish ? instr.summaryEs() : instr.summaryEn();
            String desc = isSpanish ? instr.descriptionEs() : instr.descriptionEn();
            String matchedSubtitle = instr.category().displayName(isSpanish ? "es" : "en");

            if (mnemonic.equalsIgnoreCase(query)) {
                score = 100;
            } else if (mnemonic.startsWith(query.toUpperCase(Locale.ROOT))) {
                score = 80;
            }

            // Check aliases
            for (AliasDeclaration alias : instr.aliases()) {
                if (alias.name().equalsIgnoreCase(query)) {
                    score = Math.max(score, 95);
                    matchedSubtitle = (isSpanish ? "Alias de " : "Alias for ") + mnemonic;
                } else if (alias.name().startsWith(query.toUpperCase(Locale.ROOT))) {
                    score = Math.max(score, 75);
                }
            }

            // Semantic synonyms
            if (matchCarry) {
                if (mnemonic.equalsIgnoreCase("ADC") || mnemonic.equalsIgnoreCase("SBB") ||
                        mnemonic.equalsIgnoreCase("STC") || mnemonic.equalsIgnoreCase("CLC") ||
                        mnemonic.equalsIgnoreCase("CMC") || mnemonic.equalsIgnoreCase("JC") ||
                        mnemonic.equalsIgnoreCase("JNC")) {
                    score = Math.max(score, 85);
                    matchedSubtitle = isSpanish ? "Instrucción de acarreo (" + mnemonic + ")" : "Carry instruction (" + mnemonic + ")";
                }
            }

            if (matchStack) {
                if (mnemonic.equalsIgnoreCase("PUSH") || mnemonic.equalsIgnoreCase("POP") ||
                        mnemonic.equalsIgnoreCase("PUSHF") || mnemonic.equalsIgnoreCase("POPF") ||
                        mnemonic.equalsIgnoreCase("ENTER") || mnemonic.equalsIgnoreCase("LEAVE")) {
                    score = Math.max(score, 85);
                    matchedSubtitle = isSpanish ? "Instrucción de pila (" + mnemonic + ")" : "Stack instruction (" + mnemonic + ")";
                }
            }

            if (score == 0) {
                if (normalize(summary).contains(norm) || normalize(desc).contains(norm)) {
                    score = 50;
                }
            }

            if (score > 0) {
                addItem(grouped, KnowledgeEntityKind.INSTRUCTION,
                        new KnowledgeSearchResult(instr.id(), instr.mnemonic(), matchedSubtitle, KnowledgeEntityKind.INSTRUCTION, summary, instr, score));
            }
        }
    }

    private void searchServices(String query, String norm, boolean isSpanish,
                                Map<KnowledgeEntityKind, List<KnowledgeSearchResult>> grouped) {
        boolean matchInt21 = norm.contains("21h") || norm.contains("int 21") || norm.contains("int21")
                || (norm.contains("interrupcion") && norm.contains("21"))
                || (norm.contains("interrupt") && norm.contains("21"));

        for (ServiceEntry service : corpus.getAllServices()) {
            int score = 0;
            String id = service.id();
            String summary = isSpanish ? service.summaryEs() : service.summaryEn();
            String desc = isSpanish ? service.descriptionEs() : service.descriptionEn();
            String selectorVal = service.selector() != null ? service.selector().value() : "";

            if (matchInt21 && service.vector().equalsIgnoreCase("21h")) {
                score = 90;
            } else if (service.vector().equalsIgnoreCase(query)) {
                score = 85;
            } else if (id.equalsIgnoreCase(query)) {
                score = 95;
            } else if (selectorVal.equalsIgnoreCase(query)) {
                score = 80;
            } else if (normalize(summary).contains(norm) || normalize(desc).contains(norm)) {
                score = 60;
            }

            if (score > 0) {
                String title = "INT " + service.vector() + (selectorVal.isBlank() ? "" : " AH=" + selectorVal);
                String subtitle = service.environment() + " " + service.vectorStatus().description();
                addItem(grouped, KnowledgeEntityKind.SERVICE,
                        new KnowledgeSearchResult(service.id(), title, subtitle, KnowledgeEntityKind.SERVICE, summary, service, score));
            }
        }
    }

    private void searchConcepts(String query, String norm, boolean isSpanish,
                                Map<KnowledgeEntityKind, List<KnowledgeSearchResult>> grouped) {
        for (ConceptEntry concept : corpus.getAllConcepts()) {
            int score = 0;
            String title = isSpanish ? concept.titleEs() : concept.titleEn();
            String summary = isSpanish ? concept.summaryEs() : concept.summaryEn();
            String content = isSpanish ? concept.contentEs() : concept.contentEn();

            if (normalize(title).equalsIgnoreCase(norm)) {
                score = 95;
            } else if (normalize(title).contains(norm)) {
                score = 75;
            } else if (normalize(summary).contains(norm) || normalize(content).contains(norm)) {
                score = 50;
            }

            if (score > 0) {
                addItem(grouped, KnowledgeEntityKind.CONCEPT,
                        new KnowledgeSearchResult(concept.id(), title, concept.category(), KnowledgeEntityKind.CONCEPT, summary, concept, score));
            }
        }
    }

    private static void addItem(Map<KnowledgeEntityKind, List<KnowledgeSearchResult>> grouped,
                                KnowledgeEntityKind kind, KnowledgeSearchResult item) {
        grouped.computeIfAbsent(kind, k -> new ArrayList<>()).add(item);
    }

    public static String normalize(String text) {
        if (text == null) return "";
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        return DIACRITICS.matcher(nfd).replaceAll("").toLowerCase(Locale.ROOT).trim();
    }
}
