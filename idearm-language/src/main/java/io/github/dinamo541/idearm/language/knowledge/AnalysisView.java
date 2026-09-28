package io.github.dinamo541.idearm.language.knowledge;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Strict boundary view for lexer, highlighter, and linter rules (ADR-013, Annex D §D.7).
 *
 * <p>Only exposes mnemonics that are recognized by the active assembler dialect,
 * preventing academic expansions from causing false syntax errors in user code.
 */
public final class AnalysisView {

    private final Corpus corpus;

    public AnalysisView(Corpus corpus) {
        this.corpus = corpus;
    }

    private final java.util.Map<Dialect, Set<String>> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public Set<String> recognizedMnemonics(Dialect dialect) {
        Dialect key = dialect != null ? dialect : Dialect.UNKNOWN;
        return cache.computeIfAbsent(key, this::computeRecognized);
    }

    private Set<String> computeRecognized(Dialect dialect) {
        Set<String> result = new TreeSet<>();
        for (InstructionEntry entry : corpus.getAllInstructions()) {
            if (entry.recognizedBy().isEmpty() || entry.recognizedBy().contains(Dialect.COMMON)
                    || dialect == null || dialect == Dialect.UNKNOWN || entry.recognizedBy().contains(dialect)) {
                result.add(entry.mnemonic().toUpperCase(Locale.ROOT));
                for (AliasDeclaration alias : entry.aliases()) {
                    if (alias.dialect() == null || alias.dialect() == Dialect.COMMON
                            || dialect == null || dialect == Dialect.UNKNOWN || alias.dialect() == dialect) {
                        result.add(alias.name().toUpperCase(Locale.ROOT));
                    }
                }
            }
        }
        // Also include the recognized assembler mnemonics inventory
        for (String m : corpus.getRecognizedAssemblerMnemonics(dialect)) {
            result.add(m.toUpperCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(result);
    }

    public boolean isRecognized(String word, Dialect dialect) {
        if (word == null || word.isBlank()) return false;
        String upper = word.trim().toUpperCase(Locale.ROOT);
        return recognizedMnemonics(dialect).contains(upper);
    }

    private final java.util.Map<Dialect, Set<String>> directivesCache = new java.util.concurrent.ConcurrentHashMap<>();

    public Set<String> recognizedDirectives(Dialect dialect) {
        Dialect key = dialect != null ? dialect : Dialect.UNKNOWN;
        return directivesCache.computeIfAbsent(key, this::computeRecognizedDirectives);
    }

    private Set<String> computeRecognizedDirectives(Dialect dialect) {
        Set<String> result = new TreeSet<>();
        if (dialect == Dialect.NASM) {
            result.addAll(io.github.dinamo541.idearm.language.lexer.AssemblyLexer.NASM_DIRECTIVES);
        } else if (dialect == Dialect.MASM || dialect == Dialect.TASM) {
            result.addAll(io.github.dinamo541.idearm.language.lexer.AssemblyLexer.MASM_TASM_DIRECTIVES);
        } else {
            result.addAll(io.github.dinamo541.idearm.language.lexer.AssemblyLexer.DIRECTIVES);
        }

        for (SyntaxItem item : corpus.getAllSyntaxItems()) {
            if (item.syntaxClass() == SyntaxClass.DIRECTIVE
                    || item.syntaxClass() == SyntaxClass.PREPROCESSOR
                    || item.syntaxClass() == SyntaxClass.OPERATOR) {
                boolean matches = false;
                if (dialect == null || dialect == Dialect.UNKNOWN || dialect == Dialect.COMMON) {
                    matches = true;
                } else {
                    for (DialectSupport ds : item.dialects()) {
                        if (ds.dialect() == Dialect.COMMON || ds.dialect() == dialect) {
                            matches = true;
                            break;
                        }
                    }
                }
                if (matches) {
                    result.add(item.token().toUpperCase(Locale.ROOT));
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public boolean isDirective(String word, Dialect dialect) {
        if (word == null || word.isBlank()) return false;
        String upper = word.trim().toUpperCase(Locale.ROOT);
        return recognizedDirectives(dialect).contains(upper);
    }
}
