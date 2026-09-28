package io.github.dinamo541.idearm.language.knowledge;

import io.github.dinamo541.idearm.language.catalog.InstructionCategory;

import java.util.List;
import java.util.Optional;

/**
 * View for the Academic Center / Reference browser.
 */
public final class ReferenceView {

    private final Corpus corpus;

    public ReferenceView(Corpus corpus) {
        this.corpus = corpus;
    }

    public List<InstructionEntry> allInstructions() {
        return corpus.getAllInstructions();
    }

    public Optional<InstructionEntry> findInstruction(String mnemonic) {
        return corpus.findInstruction(mnemonic);
    }

    public List<InstructionEntry> filter(InstructionCategory category, String targetCpu, String searchPrefix) {
        return corpus.filter(category, targetCpu, searchPrefix);
    }

    public List<RegisterEntry> allRegisters() {
        return corpus.getAllRegisters();
    }

    public List<SyntaxItem> allSyntaxItems() {
        return corpus.getAllSyntaxItems();
    }

    public List<ServiceEntry> allServices() {
        return corpus.getAllServices();
    }

    public List<ConceptEntry> allConcepts() {
        return corpus.getAllConcepts();
    }

    public List<LearningPathEntry> allLearningPaths() {
        return corpus.getAllLearningPaths();
    }

    public List<ExampleEntry> allExamples() {
        return corpus.getAllExamples();
    }
}
