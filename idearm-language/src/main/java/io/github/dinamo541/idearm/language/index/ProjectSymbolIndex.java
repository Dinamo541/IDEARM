package io.github.dinamo541.idearm.language.index;

import io.github.dinamo541.idearm.language.lexer.AssemblyLexer;
import io.github.dinamo541.idearm.language.lexer.Token;
import io.github.dinamo541.idearm.language.lexer.TokenType;
import io.github.dinamo541.idearm.language.model.*;
import io.github.dinamo541.idearm.language.parser.AssemblyParser;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe multi-file symbol index for an Assembly project.
 * Supports fast incremental updates as individual files are modified.
 */
public final class ProjectSymbolIndex {

    private final Map<String, FileSymbols> files = new ConcurrentHashMap<>();
    private final AssemblyParser parser;
    private final AssemblyLexer lexer;

    public ProjectSymbolIndex() {
        this.lexer = new AssemblyLexer();
        this.parser = new AssemblyParser(lexer);
    }

    public ProjectSymbolIndex(AssemblyParser parser, AssemblyLexer lexer) {
        this.parser = Objects.requireNonNull(parser, "parser cannot be null");
        this.lexer = Objects.requireNonNull(lexer, "lexer cannot be null");
    }

    /**
     * Incrementally updates symbol definitions and references for a specific file.
     */
    public void updateFile(String filePath, String sourceText) {
        if (filePath == null) return;
        String normalizedPath = normalizePath(filePath);

        if (sourceText == null || sourceText.isBlank()) {
            files.remove(normalizedPath);
            return;
        }

        List<Token> tokens = lexer.tokenize(sourceText);
        SourceFileNode ast = parser.parse(tokens);

        List<SymbolDefinition> defs = new ArrayList<>();
        List<SymbolReference> refs = new ArrayList<>();

        // 1. Procedures
        for (ProcedureNode proc : ast.procedures()) {
            defs.add(new SymbolDefinition(
                    proc.name(),
                    SymbolKind.PROCEDURE,
                    normalizedPath,
                    proc.line(),
                    proc.column(),
                    proc.name() + " PROC" + (proc.isFar() ? " FAR" : " NEAR"),
                    null
            ));
        }

        // 2. Labels
        for (LabelNode lbl : ast.labels()) {
            defs.add(new SymbolDefinition(
                    lbl.name(),
                    SymbolKind.LABEL,
                    normalizedPath,
                    lbl.line(),
                    lbl.column(),
                    lbl.name() + ":",
                    null
            ));
        }

        // 3. Variables / Data
        for (DataNode data : ast.dataDefinitions()) {
            defs.add(new SymbolDefinition(
                    data.name(),
                    SymbolKind.VARIABLE,
                    normalizedPath,
                    data.line(),
                    data.column(),
                    data.name() + " " + data.directive() + " " + data.initialValue(),
                    null
            ));
        }

        // 4. Constants
        for (ConstantNode c : ast.constants()) {
            defs.add(new SymbolDefinition(
                    c.name(),
                    SymbolKind.CONSTANT,
                    normalizedPath,
                    c.line(),
                    c.column(),
                    c.name() + " EQU " + c.value(),
                    null
            ));
        }

        // 5. Segments
        for (SegmentNode seg : ast.segments()) {
            defs.add(new SymbolDefinition(
                    seg.name(),
                    SymbolKind.SEGMENT,
                    normalizedPath,
                    seg.line(),
                    seg.column(),
                    seg.name() + " SEGMENT",
                    null
            ));
        }

        // 6. Extract identifier references from tokens
        for (Token tok : tokens) {
            if (tok.is(TokenType.IDENTIFIER)) {
                refs.add(new SymbolReference(tok.text(), normalizedPath, tok.line(), tok.column()));
            }
        }

        files.put(normalizedPath, new FileSymbols(normalizedPath, defs, refs));
    }

    public void removeFile(String filePath) {
        if (filePath != null) {
            files.remove(normalizePath(filePath));
        }
    }

    public void clear() {
        files.clear();
    }

    /**
     * Looks up the primary definition of a symbol across all indexed project files.
     */
    public Optional<SymbolDefinition> findDefinition(String symbolName) {
        if (symbolName == null || symbolName.isBlank()) return Optional.empty();
        String target = symbolName.trim();

        for (FileSymbols fs : files.values()) {
            for (SymbolDefinition def : fs.definitions()) {
                if (def.name().equalsIgnoreCase(target)) {
                    return Optional.of(def);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds all references to a given symbol name across all indexed project files.
     */
    public List<SymbolReference> findReferences(String symbolName) {
        if (symbolName == null || symbolName.isBlank()) return List.of();
        String target = symbolName.trim();

        List<SymbolReference> matches = new ArrayList<>();
        for (FileSymbols fs : files.values()) {
            for (SymbolReference ref : fs.references()) {
                if (ref.name().equalsIgnoreCase(target)) {
                    matches.add(ref);
                }
            }
        }
        return List.copyOf(matches);
    }

    /**
     * Returns all symbol definitions starting with the specified prefix (for autocompletion).
     */
    public List<SymbolDefinition> findDefinitionsStartingWith(String prefix) {
        if (prefix == null) return List.of();
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);

        return files.values().stream()
                .flatMap(fs -> fs.definitions().stream())
                .filter(def -> def.name().toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }

    /**
     * Gets all symbols defined within a specific file.
     */
    public List<SymbolDefinition> getDefinitionsForFile(String filePath) {
        if (filePath == null) return List.of();
        FileSymbols fs = files.get(normalizePath(filePath));
        return fs != null ? fs.definitions() : List.of();
    }

    public int getFileCount() {
        return files.size();
    }

    private static String normalizePath(String path) {
        return path.replace('\\', '/').trim();
    }
}
