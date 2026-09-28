package io.github.dinamo541.idearm.language.knowledge;

import java.util.List;
import java.util.Optional;

/**
 * View mapping architectural registers and flags to live debugger visibility.
 */
public final class DebugView {

    private final Corpus corpus;

    public DebugView(Corpus corpus) {
        this.corpus = corpus;
    }

    public List<RegisterEntry> exposedRegisters(String backend) {
        return corpus.getAllRegisters().stream()
                .filter(r -> r.exposedBy().contains(backend) || r.exposedBy().contains("all"))
                .toList();
    }

    public Optional<RegisterEntry> findRegister(String name) {
        return corpus.findRegister(name);
    }
}
