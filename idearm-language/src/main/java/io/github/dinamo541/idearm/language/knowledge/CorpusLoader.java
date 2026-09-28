package io.github.dinamo541.idearm.language.knowledge;

import io.github.dinamo541.idearm.language.catalog.CpuLevel;
import io.github.dinamo541.idearm.language.catalog.FlagEffect;
import io.github.dinamo541.idearm.language.catalog.FlagSummary;
import io.github.dinamo541.idearm.language.catalog.InstructionCategory;

import java.io.InputStream;
import java.util.*;

/**
 * Loads the Academic Knowledge Corpus from module resources.
 *
 * <p>Uses pure JDK Class.getResourceAsStream to comply strictly with ADR-007
 * (no java.nio.file.Files in Layer 3).
 */
public final class CorpusLoader {

    private static final System.Logger LOG = System.getLogger(CorpusLoader.class.getName());

    private static final String RESOURCE_BASE = "/io/github/dinamo541/idearm/language/knowledge/";

    private CorpusLoader() {
    }

    public static Corpus load() {
        Corpus corpus = new Corpus();
        try {
            // 1. Load localized text maps
            Map<String, String> textEn = loadTextMap(RESOURCE_BASE + "text/en/instructions.json");
            Map<String, String> textEs = loadTextMap(RESOURCE_BASE + "text/es/instructions.json");
            loadTranslations(corpus);

            // 2. Load manifest corpus.json
            InputStream manifestStream = CorpusLoader.class.getResourceAsStream(RESOURCE_BASE + "corpus.json");
            if (manifestStream == null) {
                // If resources are not yet built or empty, return empty corpus
                return corpus;
            }

            Map<String, Object> manifest = Json.parseObject(manifestStream);

            // Load recognized assembler mnemonics
            Map<String, Object> recMap = Json.getObject(manifest, "recognizedByAssembler");
            if (recMap != null) {
                for (Map.Entry<String, Object> entry : recMap.entrySet()) {
                    Dialect dialect = Dialect.parse(entry.getKey());
                    if (entry.getValue() instanceof List<?> list) {
                        for (Object m : list) {
                            if (m != null) {
                                corpus.addRecognizedAssemblerMnemonic(dialect, m.toString());
                            }
                        }
                    }
                }
            }

            // Load instruction files declared in manifest
            List<String> files = Json.getStringList(manifest, "files");
            for (String file : files) {
                String path = RESOURCE_BASE + file;
                try (InputStream is = CorpusLoader.class.getResourceAsStream(path)) {
                    if (is != null) {
                        List<Object> entries = Json.parseArray(is);
                        for (Object o : entries) {
                            if (o instanceof Map<?, ?> mapObj) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> map = (Map<String, Object>) mapObj;
                                InstructionEntry entry = parseInstructionEntry(map, textEn, textEs);
                                corpus.addInstruction(entry);
                            }
                        }
                    }
                }
            }

            // Load registers
            loadRegisters(corpus);

            // Load syntax items
            loadSyntaxItems(corpus);

            // Load services
            loadServices(corpus);

            // Load vector index
            loadVectorIndex(corpus);

            // Load concepts
            loadConcepts(corpus);

            // Load learning paths
            loadLearningPaths(corpus);

            // Load sources
            loadSources(corpus);

        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "The knowledge corpus could not be fully loaded", e);
        }
        return corpus;
    }

    /** The table that gives both languages of the single-language texts; see {@link Corpus#localize}. */
    private static void loadTranslations(Corpus corpus) {
        try (InputStream in = CorpusLoader.class.getResourceAsStream(RESOURCE_BASE + "text/translations.json")) {
            if (in == null) return;
            for (Map.Entry<String, Object> e : Json.parseObject(in).entrySet()) {
                if (e.getValue() instanceof Map<?, ?> pair) {
                    Object en = pair.get("en");
                    Object es = pair.get("es");
                    corpus.addTranslation(e.getKey(), en == null ? null : en.toString(), es == null ? null : es.toString());
                }
            }
        } catch (Exception unreadable) {
            LOG.log(System.Logger.Level.WARNING, "The corpus translation table could not be read", unreadable);
        }
    }

    private static Map<String, String> loadTextMap(String path) {
        Map<String, String> map = new LinkedHashMap<>();
        try (InputStream in = CorpusLoader.class.getResourceAsStream(path)) {
            if (in != null) {
                Map<String, Object> parsed = Json.parseObject(in);
                for (Map.Entry<String, Object> e : parsed.entrySet()) {
                    if (e.getValue() != null) {
                        map.put(e.getKey(), e.getValue().toString());
                    }
                }
            }
        } catch (Exception unreadable) {
            // The rest of the corpus still loads; the missing part is logged instead of disappearing silently.
            LOG.log(System.Logger.Level.WARNING, "Part of the knowledge corpus could not be read", unreadable);
        }
        return map;
    }

    private static InstructionEntry parseInstructionEntry(Map<String, Object> map,
                                                          Map<String, String> textEn,
                                                          Map<String, String> textEs) {
        String id = Json.getString(map, "id", "");
        String mnemonic = Json.getString(map, "mnemonic", "");
        InstructionKind kind = parseEnum(InstructionKind.class, Json.getString(map, "kind", "INSTRUCTION"), InstructionKind.INSTRUCTION);
        String family = Json.getString(map, "family", "F-01");
        InstructionCategory category = parseEnum(InstructionCategory.class, Json.getString(map, "category", "DATA_TRANSFER"), InstructionCategory.DATA_TRANSFER);
        PedagogicalLevel level = parseEnum(PedagogicalLevel.class, Json.getString(map, "pedagogicalLevel", "BASIC"), PedagogicalLevel.BASIC);
        List<String> prerequisites = Json.getStringList(map, "prerequisites");
        List<String> homonyms = Json.getStringList(map, "homonyms");
        List<String> pitfalls = Json.getStringList(map, "pitfalls");
        List<String> counterExamples = Json.getStringList(map, "counterExamples");
        List<String> sources = Json.getStringList(map, "sources");
        List<String> syntaxVariants = Json.getStringList(map, "syntaxVariants");
        String example = Json.getString(map, "example", "");
        CpuGeneration minCpuGen = CpuGeneration.parse(Json.getString(map, "minCpu", "8086"));

        // Parse Aliases
        List<AliasDeclaration> aliases = new ArrayList<>();
        List<Map<String, Object>> aliasList = Json.getObjectList(map, "aliases");
        for (Map<String, Object> a : aliasList) {
            String name = Json.getString(a, "name", "");
            Dialect dialect = Dialect.parse(Json.getString(a, "dialect", "COMMON"));
            String version = Json.getString(a, "version", null);
            String target = Json.getString(a, "targetMnemonic", mnemonic);
            String note = Json.getString(a, "note", null);
            if (!name.isBlank()) {
                aliases.add(new AliasDeclaration(name, dialect, version, target, note));
            }
        }

        // Parse Flags
        FlagSummary flags = parseFlagSummary(Json.getObject(map, "flags"));

        // Parse RecognizedBy
        Set<Dialect> recognizedBy = new LinkedHashSet<>();
        for (String dStr : Json.getStringList(map, "recognizedBy")) {
            recognizedBy.add(Dialect.parse(dStr));
        }

        // Parse Forms
        List<InstructionForm> forms = new ArrayList<>();
        List<Map<String, Object>> formList = Json.getObjectList(map, "forms");
        for (int fIdx = 0; fIdx < formList.size(); fIdx++) {
            Map<String, Object> fObj = formList.get(fIdx);
            String formId = Json.getString(fObj, "id", id + "#form." + (fIdx + 1));
            String formText = Json.getString(fObj, "text", mnemonic);
            CpuGeneration formCpu = CpuGeneration.parse(Json.getString(fObj, "minCpu", minCpuGen.displayName()));

            // Rich syntax parsing
            List<SyntaxForm> syntax = new ArrayList<>();
            List<Map<String, Object>> synList = Json.getObjectList(fObj, "syntax");
            for (Map<String, Object> sMap : synList) {
                Dialect dialect = Dialect.parse(Json.getString(sMap, "dialect", "COMMON"));
                String sText = Json.getString(sMap, "text", formText);
                String version = Json.getString(sMap, "version", null);
                syntax.add(new SyntaxForm(dialect, version, sText));
            }
            if (syntax.isEmpty()) {
                syntax = List.of(SyntaxForm.of(Dialect.COMMON, formText));
            }

            // Rich requirement parsing
            Map<String, Object> reqObj = Json.getObject(fObj, "requirement");
            if (reqObj == null || reqObj.isEmpty()) {
                reqObj = Json.getObject(map, "requirement");
            }
            Requirement req;
            if (reqObj != null && !reqObj.isEmpty()) {
                CpuGeneration minGen = CpuGeneration.parse(Json.getString(reqObj, "minGeneration", formCpu.displayName()));
                Set<Feature> features = parseFeatureSet(Json.getStringList(reqObj, "features"));
                Set<ProcessorMode> validModes = parseProcessorModeSet(Json.getStringList(reqObj, "validModes"));
                List<String> invModesList = Json.getStringList(reqObj, "invalidModes");
                if (invModesList.isEmpty()) {
                    invModesList = Json.getStringList(map, "invalidModes");
                }
                Set<ProcessorMode> invalidModes = parseProcessorModeSet(invModesList);
                Privilege priv = parseEnum(Privilege.class, Json.getString(reqObj, "privilege", "ANY"), Privilege.ANY);
                List<Integer> opSizes = parseIntList(reqObj, "operandSizes");
                List<Integer> addrSizes = parseIntList(reqObj, "addressSizes");
                req = new Requirement(minGen, features, validModes, invalidModes, priv, opSizes, addrSizes);
            } else {
                List<String> invModesList = Json.getStringList(fObj, "invalidModes");
                if (invModesList.isEmpty()) {
                    invModesList = Json.getStringList(map, "invalidModes");
                }
                if (!invModesList.isEmpty()) {
                    req = new Requirement(formCpu, Set.of(), Set.of(), parseProcessorModeSet(invModesList), Privilege.ANY, List.of(), List.of());
                } else {
                    req = Requirement.forGeneration(formCpu);
                }
            }

            List<Operand> operands = parseOperands(Json.getObjectList(fObj, "operands"));
            List<Operand> implicitOperands = parseOperands(Json.getObjectList(fObj, "implicitOperands"));
            List<FlagEffectSpec> formFlags = parseFlagEffectSpecs(Json.getObjectList(fObj, "flags"));
            List<String> otherState = Json.getStringList(fObj, "otherState");
            if (otherState.isEmpty()) {
                otherState = Json.getStringList(map, "otherState");
            }
            String operation = Json.getString(fObj, "operation", formText);
            String operationPlain = Json.getString(fObj, "operationPlain", operation);

            List<BackendAvailability> availability = new ArrayList<>();
            List<Map<String, Object>> availList = Json.getObjectList(fObj, "availability");
            for (Map<String, Object> aMap : availList) {
                String backend = Json.getString(aMap, "backend", "all");
                BackendAvailability.AvailabilityStatus st = parseEnum(BackendAvailability.AvailabilityStatus.class, Json.getString(aMap, "status", "AVAILABLE"), BackendAvailability.AvailabilityStatus.AVAILABLE);
                String caveat = Json.getString(aMap, "caveat", null);
                String reason = Json.getString(aMap, "reason", null);
                availability.add(new BackendAvailability(backend, st, caveat, reason));
            }
            if (availability.isEmpty()) {
                availability = List.of(new BackendAvailability("all", BackendAvailability.AvailabilityStatus.AVAILABLE));
            }

            List<String> formSources = Json.getStringList(fObj, "sources");
            if (formSources.isEmpty()) formSources = sources;

            forms.add(new InstructionForm(
                    formId,
                    syntax,
                    operands,
                    implicitOperands,
                    req,
                    formFlags,
                    otherState,
                    operation,
                    operationPlain,
                    List.of(),
                    availability,
                    formSources
            ));
        }

        String baseKey = "x86.instr." + mnemonic.toLowerCase(Locale.ROOT);
        String summaryEn = textEn.getOrDefault(id + ".summary", textEn.getOrDefault(baseKey + ".summary", Json.getString(map, "summaryEn", "")));
        String descriptionEn = textEn.getOrDefault(id + ".description", textEn.getOrDefault(baseKey + ".description", Json.getString(map, "descriptionEn", "")));
        String summaryEs = textEs.getOrDefault(id + ".summary", textEs.getOrDefault(baseKey + ".summary", Json.getString(map, "summaryEs", "")));
        String descriptionEs = textEs.getOrDefault(id + ".description", textEs.getOrDefault(baseKey + ".description", Json.getString(map, "descriptionEs", "")));

        return new InstructionEntry(
                id,
                mnemonic,
                kind,
                family,
                category,
                level,
                prerequisites,
                aliases,
                homonyms,
                List.of(),
                forms,
                pitfalls,
                counterExamples,
                sources,
                recognizedBy,
                summaryEn,
                summaryEs,
                descriptionEn,
                descriptionEs,
                example,
                syntaxVariants,
                minCpuGen,
                flags
        );
    }

    private static FlagSummary parseFlagSummary(Map<String, Object> flagMap) {
        if (flagMap == null) return FlagSummary.none();
        return new FlagSummary(
                parseFlagEffect(flagMap.get("O")),
                parseFlagEffect(flagMap.get("D")),
                parseFlagEffect(flagMap.get("I")),
                parseFlagEffect(flagMap.get("T")),
                parseFlagEffect(flagMap.get("S")),
                parseFlagEffect(flagMap.get("Z")),
                parseFlagEffect(flagMap.get("A")),
                parseFlagEffect(flagMap.get("P")),
                parseFlagEffect(flagMap.get("C"))
        );
    }

    private static FlagEffect parseFlagEffect(Object val) {
        if (val == null) return FlagEffect.UNAFFECTED;
        String s = val.toString().toUpperCase(Locale.ROOT).trim();
        return switch (s) {
            case "M", "MODIFIED" -> FlagEffect.MODIFIED;
            case "0", "CLEARED" -> FlagEffect.CLEARED;
            case "1", "SET" -> FlagEffect.SET;
            case "U", "UNDEFINED" -> FlagEffect.UNDEFINED;
            default -> FlagEffect.UNAFFECTED;
        };
    }

    private static List<Operand> parseOperands(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return List.of();
        List<Operand> result = new ArrayList<>(list.size());
        for (Map<String, Object> opMap : list) {
            int pos = Json.getInt(opMap, "position", 0);
            OperandKind kind = OperandKind.valueOf(Json.getString(opMap, "kind", "REG"));
            List<Integer> sizes = new ArrayList<>();
            Object sizesObj = opMap.get("sizes");
            if (sizesObj instanceof List<?> slist) {
                for (Object item : slist) {
                    if (item instanceof Number n) sizes.add(n.intValue());
                    else if (item != null) {
                        try { sizes.add(Integer.parseInt(item.toString())); } catch (NumberFormatException ignored) {}
                    }
                }
            }
            OperandAccess access = OperandAccess.valueOf(Json.getString(opMap, "access", "READ"));
            OperandRole role = OperandRole.valueOf(Json.getString(opMap, "role", "EXPLICIT"));
            String defaultSeg = Json.getString(opMap, "defaultSegment", null);
            OperandExtension ext = OperandExtension.valueOf(Json.getString(opMap, "extension", "NONE"));
            List<String> constraints = Json.getStringList(opMap, "constraints");
            String note = Json.getString(opMap, "note", null);
            String reg = Json.getString(opMap, "register", null);
            result.add(new Operand(pos, kind, sizes, access, role, defaultSeg, ext, constraints, note, reg));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<FlagEffectSpec> parseFlagEffectSpecs(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return List.of();
        List<FlagEffectSpec> result = new ArrayList<>(list.size());
        for (Map<String, Object> fMap : list) {
            String flagId = Json.getString(fMap, "flagId", Json.getString(fMap, "flag", "CF"));
            FlagEffect effect = parseFlagEffect(fMap.get("effect"));
            String condition = Json.getString(fMap, "condition", null);
            String note = Json.getString(fMap, "note", null);
            result.add(new FlagEffectSpec(flagId, effect, condition, note));
        }
        return Collections.unmodifiableList(result);
    }

    private static Set<Feature> parseFeatureSet(List<String> list) {
        if (list == null || list.isEmpty()) return Set.of();
        Set<Feature> set = new LinkedHashSet<>();
        for (String s : list) {
            try {
                set.add(Feature.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {}
        }
        return Collections.unmodifiableSet(set);
    }

    private static Set<ProcessorMode> parseProcessorModeSet(List<String> list) {
        if (list == null || list.isEmpty()) return Set.of();
        Set<ProcessorMode> set = new LinkedHashSet<>();
        for (String s : list) {
            try {
                set.add(ProcessorMode.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {}
        }
        return Collections.unmodifiableSet(set);
    }

    private static List<Integer> parseIntList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List<?> l) {
            List<Integer> res = new ArrayList<>();
            for (Object item : l) {
                if (item instanceof Number n) res.add(n.intValue());
                else if (item != null) {
                    try { res.add(Integer.parseInt(item.toString())); } catch (NumberFormatException ignored) {}
                }
            }
            return Collections.unmodifiableList(res);
        }
        return List.of();
    }

    private static void loadRegisters(Corpus corpus) {
        String[] regFiles = {
                "registers.json",
                "general.json",
                "pointers.json",
                "segments.json",
                "flags.json",
                "x87_simd.json",
                "system.json"
        };
        for (String file : regFiles) {
            try (InputStream in = CorpusLoader.class.getResourceAsStream(RESOURCE_BASE + "semantic/registers/" + file)) {
                if (in != null) {
                    List<Object> list = Json.parseArray(in);
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> regMap = (Map<String, Object>) map;
                            corpus.addRegister(parseRegisterEntry(regMap));
                        }
                    }
                }
            } catch (Exception unreadable) {
                // The rest of the corpus still loads; the missing part is logged instead of disappearing silently.
                LOG.log(System.Logger.Level.WARNING, "Part of the knowledge corpus could not be read", unreadable);
            }
        }
    }

    private static RegisterEntry parseRegisterEntry(Map<String, Object> map) {
        String id = Json.getString(map, "id", "");
        String name = Json.getString(map, "name", "");
        RegisterGroup group = parseRegisterGroup(Json.getString(map, "group", "G1_GENERAL"));
        int sizeBits = Json.getInt(map, "sizeBits", 16);
        String parent = Json.getString(map, "parent", null);
        int parentOffset = Json.getInt(map, "parentOffsetBits", 0);
        String conventionalUse = Json.getString(map, "conventionalUse", "");
        String writeSemantics = Json.getString(map, "writeSemantics", "");
        List<String> sources = Json.getStringList(map, "sources");
        List<String> exposedBy = Json.getStringList(map, "exposedBy");

        List<RegisterView> views = new ArrayList<>();
        for (Map<String, Object> vm : Json.getObjectList(map, "views")) {
            views.add(new RegisterView(
                    Json.getString(vm, "id", ""),
                    Json.getString(vm, "name", ""),
                    Json.getInt(vm, "sizeBits", 8),
                    Json.getInt(vm, "offsetBits", 0)
            ));
        }

        List<FlagField> fields = new ArrayList<>();
        for (Map<String, Object> fm : Json.getObjectList(map, "fields")) {
            fields.add(new FlagField(
                    Json.getString(fm, "id", ""),
                    Json.getString(fm, "name", ""),
                    Json.getInt(fm, "bitPosition", 0),
                    Json.getString(fm, "meaning", ""),
                    Json.getString(fm, "setCondition", ""),
                    Json.getString(fm, "clearCondition", "")
            ));
        }

        List<ArchitecturalUse> archUses = new ArrayList<>();
        for (Map<String, Object> am : Json.getObjectList(map, "architecturalUse")) {
            archUses.add(new ArchitecturalUse(
                    Json.getString(am, "instruction", ""),
                    Json.getString(am, "role", "")
            ));
        }

        Map<String, Object> reqObj = Json.getObject(map, "requirement");
        Requirement requirement;
        if (reqObj != null && !reqObj.isEmpty()) {
            CpuGeneration minGen = CpuGeneration.parse(Json.getString(reqObj, "minGeneration", "8086"));
            Set<Feature> features = parseFeatureSet(Json.getStringList(reqObj, "features"));
            Set<ProcessorMode> validModes = parseProcessorModeSet(Json.getStringList(reqObj, "validModes"));
            Set<ProcessorMode> invalidModes = parseProcessorModeSet(Json.getStringList(reqObj, "invalidModes"));
            Privilege priv = parseEnum(Privilege.class, Json.getString(reqObj, "privilege", "ANY"), Privilege.ANY);
            requirement = new Requirement(minGen, features, validModes, invalidModes, priv, List.of(), List.of());
        } else {
            requirement = Requirement.forGeneration(CpuGeneration.I8086);
        }

        List<String> accessConstraints = Json.getStringList(map, "accessConstraints");

        return new RegisterEntry(
                id, name, group, sizeBits, parent, parentOffset,
                views, fields, conventionalUse, archUses, writeSemantics,
                accessConstraints, requirement, exposedBy, sources
        );
    }

    private static RegisterGroup parseRegisterGroup(String s) {
        if (s == null) return RegisterGroup.G1_GENERAL;
        String u = s.toUpperCase(Locale.ROOT).replace("-", "_").trim();
        if (u.equals("G1") || u.startsWith("G1_") || u.contains("GENERAL")) return RegisterGroup.G1_GENERAL;
        if (u.equals("G2") || u.startsWith("G2_") || u.contains("POINTER") || u.contains("INDEX")) return RegisterGroup.G2_POINTERS_INDEXES;
        if (u.equals("G3") || u.startsWith("G3_") || u.contains("SEGMENT")) return RegisterGroup.G3_SEGMENT;
        if (u.equals("G4") || u.startsWith("G4_") || u.contains("CONTROL") || u.contains("FLAG")) return RegisterGroup.G4_EXECUTION_CONTROL;
        if (u.equals("G5") || u.startsWith("G5_") || u.contains("X87") || u.contains("SIMD")) return RegisterGroup.G5_X87_SIMD;
        if (u.equals("G6") || u.startsWith("G6_") || u.contains("SYSTEM")) return RegisterGroup.G6_SYSTEM;
        return RegisterGroup.G1_GENERAL;
    }

    private static void loadSyntaxItems(Corpus corpus) {
        List<String> syntaxFiles = List.of(
                "semantic/syntax/common.json",
                "semantic/syntax/masm.json",
                "semantic/syntax/tasm.json",
                "semantic/syntax/nasm.json"
        );
        for (String file : syntaxFiles) {
            try (InputStream in = CorpusLoader.class.getResourceAsStream(RESOURCE_BASE + file)) {
                if (in != null) {
                    List<Object> list = Json.parseArray(in);
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> sm = (Map<String, Object>) map;
                            corpus.addSyntaxItem(parseSyntaxItem(sm));
                        }
                    }
                }
            } catch (Exception unreadable) {
                // The rest of the corpus still loads; the missing part is logged instead of disappearing silently.
                LOG.log(System.Logger.Level.WARNING, "Part of the knowledge corpus could not be read", unreadable);
            }
        }
    }

    private static SyntaxItem parseSyntaxItem(Map<String, Object> map) {
        String id = Json.getString(map, "id", "");
        String token = Json.getString(map, "token", "");
        SyntaxClass sClass = parseEnum(SyntaxClass.class, Json.getString(map, "syntaxClass", "PUNCTUATION"), SyntaxClass.PUNCTUATION);
        boolean notation = Json.getBoolean(map, "notation", false);
        boolean assemblable = Json.getBoolean(map, "assemblable", true);
        List<String> contexts = Json.getStringList(map, "contexts");
        List<String> contrastWith = Json.getStringList(map, "contrastWith");
        List<String> sources = Json.getStringList(map, "sources");
        String summaryEn = Json.getString(map, "summaryEn", "");
        String summaryEs = Json.getString(map, "summaryEs", "");
        String descEn = Json.getString(map, "descriptionEn", "");
        String descEs = Json.getString(map, "descriptionEs", "");
        String example = Json.getString(map, "example", "");

        List<DialectSupport> dialects = new ArrayList<>();
        for (Map<String, Object> dm : Json.getObjectList(map, "dialects")) {
            dialects.add(new DialectSupport(
                    Dialect.parse(Json.getString(dm, "dialect", "COMMON")),
                    Json.getString(dm, "version", null),
                    Json.getBoolean(dm, "assemblable", assemblable),
                    Json.getString(dm, "note", null)
            ));
        }

        return new SyntaxItem(
                id, token, sClass, dialects, contexts, notation, assemblable,
                contrastWith, summaryEn, summaryEs, descEn, descEs, example, sources
        );
    }

    private static void loadServices(Corpus corpus) {
        List<String> serviceFiles = List.of(
                "semantic/services/dos-int21.json",
                "semantic/services/bios-int10.json",
                "semantic/services/bios-int16.json",
                "semantic/services/cpu-exceptions.json"
        );
        for (String file : serviceFiles) {
            try (InputStream in = CorpusLoader.class.getResourceAsStream(RESOURCE_BASE + file)) {
                if (in != null) {
                    List<Object> list = Json.parseArray(in);
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> sm = (Map<String, Object>) map;
                            corpus.addService(parseServiceEntry(sm));
                        }
                    }
                }
            } catch (Exception unreadable) {
                // The rest of the corpus still loads; the missing part is logged instead of disappearing silently.
                LOG.log(System.Logger.Level.WARNING, "Part of the knowledge corpus could not be read", unreadable);
            }
        }
    }

    private static void loadVectorIndex(Corpus corpus) {
        try (InputStream in = CorpusLoader.class.getResourceAsStream(RESOURCE_BASE + "semantic/services/vectors-index.json")) {
            if (in != null) {
                List<Object> list = Json.parseArray(in);
                for (Object o : list) {
                    if (o instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> vm = (Map<String, Object>) map;
                        corpus.addVectorIndexEntry(new VectorIndexEntry(
                                Json.getString(vm, "vector", ""),
                                Json.getInt(vm, "vectorNumber", 0),
                                parseEnum(VectorStatus.class, Json.getString(vm, "status", "RESERVED"), VectorStatus.RESERVED),
                                Json.getString(vm, "titleEn", ""),
                                Json.getString(vm, "titleEs", ""),
                                Json.getString(vm, "descriptionEn", ""),
                                Json.getString(vm, "descriptionEs", ""),
                                Json.getString(vm, "defaultHandler", null),
                                Json.getString(vm, "primaryServiceId", null)
                        ));
                    }
                }
            }
        } catch (Exception unreadable) {
            // The rest of the corpus still loads; the missing part is logged instead of disappearing silently.
            LOG.log(System.Logger.Level.WARNING, "Part of the knowledge corpus could not be read", unreadable);
        }
    }

    private static ServiceEntry parseServiceEntry(Map<String, Object> map) {
        String id = Json.getString(map, "id", "");
        String env = Json.getString(map, "environment", "DOS");
        String vector = Json.getString(map, "vector", "21h");
        VectorStatus vStatus = parseEnum(VectorStatus.class, Json.getString(map, "vectorStatus", "DOS_KERNEL"), VectorStatus.DOS_KERNEL);
        Map<String, Object> selMap = Json.getObject(map, "selector");
        ServiceSelector selector = selMap != null
                ? new ServiceSelector(Json.getString(selMap, "register", "AH"), Json.getString(selMap, "value", ""))
                : new ServiceSelector("AH", "");
        String since = Json.getString(map, "sinceVersion", "1.0");
        String flagsAndRegs = Json.getString(map, "flagsAndRegisters", "");
        String summaryEn = Json.getString(map, "summaryEn", "");
        String summaryEs = Json.getString(map, "summaryEs", "");
        String descEn = Json.getString(map, "descriptionEn", "");
        String descEs = Json.getString(map, "descriptionEs", "");
        String example = Json.getString(map, "example", "");
        List<String> sources = Json.getStringList(map, "sources");

        List<ServiceInput> inputs = new ArrayList<>();
        for (Map<String, Object> im : Json.getObjectList(map, "inputs")) {
            inputs.add(new ServiceInput(
                    Json.getString(im, "register", ""),
                    Json.getString(im, "value", ""),
                    Json.getString(im, "meaning", "")
            ));
        }

        List<BufferField> buffer = new ArrayList<>();
        for (Map<String, Object> bm : Json.getObjectList(map, "bufferFormat")) {
            buffer.add(new BufferField(
                    Json.getInt(bm, "offset", 0),
                    Json.getString(bm, "size", "1"),
                    Json.getString(bm, "direction", "IN"),
                    Json.getString(bm, "meaning", "")
            ));
        }

        List<ServiceOutput> outputs = new ArrayList<>();
        for (Map<String, Object> om : Json.getObjectList(map, "outputs")) {
            outputs.add(new ServiceOutput(
                    Json.getString(om, "target", ""),
                    Json.getString(om, "meaning", "")
            ));
        }

        List<BackendAvailability> availability = new ArrayList<>();
        for (Map<String, Object> am : Json.getObjectList(map, "availability")) {
            availability.add(new BackendAvailability(
                    Json.getString(am, "backend", "all"),
                    parseEnum(BackendAvailability.AvailabilityStatus.class, Json.getString(am, "status", "AVAILABLE"), BackendAvailability.AvailabilityStatus.AVAILABLE),
                    Json.getString(am, "caveat", null),
                    Json.getString(am, "reason", null)
            ));
        }

        return new ServiceEntry(
                id, env, vector, vStatus, selector, since, inputs, buffer, outputs,
                flagsAndRegs, List.of(), availability, summaryEn, summaryEs, descEn, descEs, example, sources
        );
    }

    private static void loadConcepts(Corpus corpus) {
        try (InputStream in = CorpusLoader.class.getResourceAsStream(RESOURCE_BASE + "semantic/concepts/concepts.json")) {
            if (in != null) {
                List<Object> list = Json.parseArray(in);
                for (Object o : list) {
                    if (o instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> cm = (Map<String, Object>) map;
                        corpus.addConcept(new ConceptEntry(
                                Json.getString(cm, "id", ""),
                                Json.getString(cm, "titleEn", ""),
                                Json.getString(cm, "titleEs", ""),
                                Json.getString(cm, "category", ""),
                                parseEnum(PedagogicalLevel.class, Json.getString(cm, "level", "BASIC"), PedagogicalLevel.BASIC),
                                Json.getStringList(cm, "prerequisites"),
                                Json.getStringList(cm, "relatedInstructions"),
                                Json.getStringList(cm, "relatedConcepts"),
                                Json.getString(cm, "summaryEn", ""),
                                Json.getString(cm, "summaryEs", ""),
                                Json.getString(cm, "contentEn", ""),
                                Json.getString(cm, "contentEs", ""),
                                Json.getStringList(cm, "sources")
                        ));
                    }
                }
            }
        } catch (Exception unreadable) {
            // The rest of the corpus still loads; the missing part is logged instead of disappearing silently.
            LOG.log(System.Logger.Level.WARNING, "Part of the knowledge corpus could not be read", unreadable);
        }
    }

    private static void loadLearningPaths(Corpus corpus) {
        try (InputStream in = CorpusLoader.class.getResourceAsStream(RESOURCE_BASE + "semantic/learning-paths/paths.json")) {
            if (in != null) {
                List<Object> list = Json.parseArray(in);
                for (Object o : list) {
                    if (o instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> pm = (Map<String, Object>) map;
                        List<ExerciseEntry> exercises = new ArrayList<>();
                        for (Map<String, Object> exMap : Json.getObjectList(pm, "exercises")) {
                            exercises.add(new ExerciseEntry(
                                    Json.getString(exMap, "id", ""),
                                    Json.getString(exMap, "promptEn", ""),
                                    Json.getString(exMap, "promptEs", ""),
                                    Json.getString(exMap, "starterCode", ""),
                                    Json.getString(exMap, "solutionCode", ""),
                                    Json.getString(exMap, "solutionExplanationEn", ""),
                                    Json.getString(exMap, "solutionExplanationEs", "")
                            ));
                        }
                        corpus.addLearningPath(new LearningPathEntry(
                                Json.getString(pm, "id", ""),
                                Json.getInt(pm, "sequenceNumber", 1),
                                Json.getString(pm, "titleEn", ""),
                                Json.getString(pm, "titleEs", ""),
                                parseEnum(PedagogicalLevel.class, Json.getString(pm, "level", "BASIC"), PedagogicalLevel.BASIC),
                                Json.getStringList(pm, "prerequisites"),
                                Json.getString(pm, "objectiveEn", ""),
                                Json.getString(pm, "objectiveEs", ""),
                                Json.getStringList(pm, "stepConceptIds"),
                                Json.getStringList(pm, "stepInstructionIds"),
                                exercises,
                                Json.getStringList(pm, "sources")
                        ));
                    }
                }
            }
        } catch (Exception unreadable) {
            // The rest of the corpus still loads; the missing part is logged instead of disappearing silently.
            LOG.log(System.Logger.Level.WARNING, "Part of the knowledge corpus could not be read", unreadable);
        }
    }

    private static void loadSources(Corpus corpus) {
        try (InputStream in = CorpusLoader.class.getResourceAsStream(RESOURCE_BASE + "semantic/sources.json")) {
            if (in != null) {
                List<Object> list = Json.parseArray(in);
                for (Object o : list) {
                    if (o instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> sm = (Map<String, Object>) map;
                        corpus.addSource(new SourceReference(
                                Json.getString(sm, "id", ""),
                                Json.getString(sm, "kind", "BOOK"),
                                Json.getString(sm, "author", ""),
                                Json.getString(sm, "title", ""),
                                Json.getString(sm, "edition", ""),
                                Json.getString(sm, "url", null),
                                Json.getString(sm, "notes", null)
                        ));
                    }
                }
            }
        } catch (Exception unreadable) {
            // The rest of the corpus still loads; the missing part is logged instead of disappearing silently.
            LOG.log(System.Logger.Level.WARNING, "Part of the knowledge corpus could not be read", unreadable);
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> clazz, String name, E defaultValue) {
        if (name == null || name.isBlank()) return defaultValue;
        try {
            return Enum.valueOf(clazz, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}
