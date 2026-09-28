package io.github.dinamo541.idearm.language.knowledge;

import java.util.Locale;
import java.util.Optional;

/**
 * View for lightweight editor hover and quick info.
 */
public final class HoverView {

    private final Corpus corpus;

    public HoverView(Corpus corpus) {
        this.corpus = corpus;
    }

    public record HoverResult(
            String id,
            String title,
            String kind,
            String summary,
            String description,
            String primaryForm,
            String flagsSummary,
            String availabilityNote,
            boolean isUserSymbol
    ) {
    }

    public Optional<HoverResult> resolve(String token, CompatibilityContext context, Locale locale) {
        if (token == null || token.isBlank()) return Optional.empty();
        String upper = token.trim().toUpperCase(Locale.ROOT);

        // Check instructions
        Optional<InstructionEntry> instrOpt = corpus.findInstruction(upper);
        if (instrOpt.isPresent()) {
            InstructionEntry instr = instrOpt.get();
            boolean isEs = locale != null && "es".equalsIgnoreCase(locale.getLanguage());
            String summary = isEs ? instr.summaryEs() : instr.summaryEn();
            String desc = isEs ? instr.descriptionEs() : instr.descriptionEn();
            String formText = instr.forms().isEmpty() ? instr.mnemonic() : (instr.forms().get(0).syntax().isEmpty() ? instr.mnemonic() : instr.forms().get(0).syntax().get(0).text());
            String flags = instr.flagSummary().formatTable();

            return Optional.of(new HoverResult(
                    instr.id(),
                    instr.mnemonic(),
                    "Instruction",
                    summary,
                    desc,
                    formText,
                    flags,
                    null,
                    false
            ));
        }

        // Check registers
        Optional<RegisterEntry> regOpt = corpus.findRegister(upper);
        if (regOpt.isPresent()) {
            RegisterEntry reg = regOpt.get();
            return Optional.of(new HoverResult(
                    reg.id(),
                    reg.name(),
                    "Register",
                    corpus.localize(reg.conventionalUse(), locale),
                    corpus.localize(reg.writeSemantics(), locale),
                    reg.name() + " (" + reg.sizeBits() + "-bit)",
                    null,
                    null,
                    false
            ));
        }

        // Check syntax items
        Optional<SyntaxItem> synOpt = corpus.findSyntaxItem(token);
        if (synOpt.isPresent()) {
            SyntaxItem item = synOpt.get();
            boolean isEs = locale != null && "es".equalsIgnoreCase(locale.getLanguage());
            return Optional.of(new HoverResult(
                    item.id(),
                    item.token(),
                    item.syntaxClass().name(),
                    isEs ? item.summaryEs() : item.summaryEn(),
                    isEs ? item.descriptionEs() : item.descriptionEn(),
                    item.token(),
                    null,
                    null,
                    false
            ));
        }

        return Optional.empty();
    }
}
