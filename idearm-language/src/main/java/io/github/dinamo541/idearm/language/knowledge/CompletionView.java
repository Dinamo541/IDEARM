package io.github.dinamo541.idearm.language.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * View for contextual code completion candidates matching active target and dialect.
 */
public final class CompletionView {

    private final Corpus corpus;

    public CompletionView(Corpus corpus) {
        this.corpus = corpus;
    }

    public record Candidate(
            String text,
            String kind,
            String description,
            String insertText
    ) {
    }

    public List<Candidate> candidates(String prefix, CompatibilityContext context, Locale locale) {
        String clean = prefix != null ? prefix.trim().toUpperCase(Locale.ROOT) : "";
        boolean isEs = locale != null && "es".equalsIgnoreCase(locale.getLanguage());
        List<Candidate> result = new ArrayList<>();

        // 1. Instructions
        for (InstructionEntry entry : corpus.getAllInstructions()) {
            if (context != null) {
                // If the entire instruction has forms, check if at least one is not UNAVAILABLE
                if (!entry.forms().isEmpty()) {
                    boolean anyAvailable = false;
                    for (InstructionForm form : entry.forms()) {
                        CompatibilityResult res = CompatibilityResolver.resolve(form.requirement(), context);
                        if (res.availability() != Availability.UNAVAILABLE) {
                            anyAvailable = true;
                            break;
                        }
                    }
                    if (!anyAvailable) continue; // Skip completely unavailable instructions
                }
            }
            if (entry.mnemonic().startsWith(clean)) {
                String desc = isEs ? entry.summaryEs() : entry.summaryEn();
                result.add(new Candidate(entry.mnemonic(), "Instruction", desc, entry.mnemonic()));
            }
            for (AliasDeclaration alias : entry.aliases()) {
                if (alias.name().startsWith(clean)) {
                    if (context != null && context.dialect() != null && context.dialect() != Dialect.COMMON) {
                        if (alias.dialect() != null && alias.dialect() != Dialect.COMMON && alias.dialect() != context.dialect()) {
                            continue;
                        }
                    }
                    String desc = isEs ? entry.summaryEs() : entry.summaryEn();
                    result.add(new Candidate(alias.name(), "Alias", desc + " (" + entry.mnemonic() + ")", alias.name()));
                }
            }
        }

        // 2. Registers. AL is its own entry and also a view of AX, EAX and RAX: each name is offered once, and a
        // register's own entry wins over the same name seen as a view of a wider register.
        List<RegisterEntry> availableRegisters = new ArrayList<>();
        Set<String> offeredRegisters = new HashSet<>();
        for (RegisterEntry reg : corpus.getAllRegisters()) {
            if (context != null && reg.requirement() != null) {
                CompatibilityResult res = CompatibilityResolver.resolve(reg.requirement(), context);
                if (res.availability() == Availability.UNAVAILABLE) {
                    continue;
                }
            }
            availableRegisters.add(reg);
            if (reg.name().startsWith(clean) && offeredRegisters.add(reg.name())) {
                String regDesc = isEs ? (reg.conventionalUse() != null && !reg.conventionalUse().isBlank() ? reg.conventionalUse() : "Registro") : "Register";
                result.add(new Candidate(reg.name(), "Register", regDesc, reg.name()));
            }
        }
        for (RegisterEntry reg : availableRegisters) {
            for (RegisterView view : reg.views()) {
                if (view.name().startsWith(clean) && !offeredRegisters.contains(view.name())) {
                    if (context != null && context.generation() == CpuGeneration.I8086 && view.sizeBits() > 16) {
                        continue;
                    }
                    if (context != null && context.generation() != null && !context.generation().is64Bit() && view.sizeBits() > 32) {
                        continue;
                    }
                    String viewDesc = isEs ? ("Subregistro de " + reg.name()) : ("Subregister of " + reg.name());
                    offeredRegisters.add(view.name());
                    result.add(new Candidate(view.name(), "Register", viewDesc, view.name()));
                }
            }
        }

        // 3. Syntax items / Directives
        for (SyntaxItem item : corpus.getAllSyntaxItems()) {
            if (item.token().toUpperCase(Locale.ROOT).startsWith(clean)) {
                if (context != null && context.dialect() != null && context.dialect() != Dialect.COMMON) {
                    boolean supported = item.dialects().isEmpty();
                    for (DialectSupport ds : item.dialects()) {
                        if (ds.dialect() == Dialect.COMMON || ds.dialect() == context.dialect()) {
                            supported = true;
                            break;
                        }
                    }
                    if (!supported) continue;
                }
                String desc = isEs ? item.summaryEs() : item.summaryEn();
                result.add(new Candidate(item.token(), item.syntaxClass().name(), desc, item.token()));
            }
        }

        return Collections.unmodifiableList(result);
    }
}
