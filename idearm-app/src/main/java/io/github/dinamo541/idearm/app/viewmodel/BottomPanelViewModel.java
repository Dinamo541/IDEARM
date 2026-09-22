package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * ViewModel managing the bottom tabbed panel (Problems, Build log, Output console, References).
 */
public final class BottomPanelViewModel {

    public enum BottomTab {
        PROBLEMS,
        BUILD,
        OUTPUT,
        REFERENCES,
        DEBUG,
        TERMINAL
    }

    /**
     * How the Output panel passes the keyboard to a running program: not at all; key by key, as a DOS program in
     * the built-in emulator reads it (the program echoes what it wants shown); or a line at a time with local echo,
     * as a program reading a pipe expects.
     */
    public enum ProgramInput {
        NONE,
        KEYS,
        LINE
    }

    private final ObservableList<DiagnosticItemViewModel> problems = FXCollections.observableArrayList();
    private final ObservableList<ReferenceItemViewModel> references = FXCollections.observableArrayList();
    private final DebugViewModel debugViewModel = new DebugViewModel();
    private TerminalViewModel terminalViewModel;
    private final StringProperty buildText = new SimpleStringProperty("");
    private final StringProperty outputText = new SimpleStringProperty("");
    private final ObjectProperty<BottomTab> activeTab = new SimpleObjectProperty<>(BottomTab.OUTPUT);
    private final ObjectProperty<ProgramInput> programInput = new SimpleObjectProperty<>(ProgramInput.NONE);
    private volatile java.util.function.Consumer<String> programInputSink = text -> {};

    public BottomPanelViewModel() {
        this(null);
    }

    public BottomPanelViewModel(io.github.dinamo541.idearm.domain.port.TerminalRunner terminalRunner) {
        this.terminalViewModel = new TerminalViewModel(terminalRunner);
    }

    public void initTerminal(io.github.dinamo541.idearm.domain.port.TerminalRunner runner) {
        if (this.terminalViewModel == null || runner != null) {
            this.terminalViewModel = new TerminalViewModel(runner);
        }
    }

    public TerminalViewModel getTerminalViewModel() {
        return terminalViewModel;
    }

    public DebugViewModel getDebugViewModel() {
        return debugViewModel;
    }

    public ObservableList<DiagnosticItemViewModel> getProblems() {
        return problems;
    }

    public ObservableList<ReferenceItemViewModel> getReferences() {
        return references;
    }

    public StringProperty buildTextProperty() {
        return buildText;
    }

    public String getBuildText() {
        return buildText.get();
    }

    public StringProperty outputTextProperty() {
        return outputText;
    }

    public String getOutputText() {
        return outputText.get();
    }

    public ObjectProperty<BottomTab> activeTabProperty() {
        return activeTab;
    }

    public BottomTab getActiveTab() {
        return activeTab.get();
    }

    public void setActiveTab(BottomTab tab) {
        FxDispatch.run(() -> activeTab.set(tab));
    }

    public void appendBuildLine(String line) {
        FxDispatch.run(() -> {
            String current = buildText.get();
            buildText.set(current.isEmpty() ? line : current + "\n" + line);
        });
    }

    public void clearBuild() {
        FxDispatch.run(() -> buildText.set(""));
    }

    /**
     * Adds a line of the IDE's own text on a line of its own: it starts a new line if the program left one
     * unfinished, and ends its own, so what the program prints next starts on a fresh line.
     */
    public void appendOutputLine(String line) {
        FxDispatch.run(() -> {
            String current = outputText.get();
            String separator = current.isEmpty() || current.endsWith("\n") ? "" : "\n";
            outputText.set(current + separator + line + "\n");
        });
    }

    /**
     * Adds what the program printed exactly as it printed it: DOS writes characters one at a time, and adding a line
     * break after each call put every character of {@code INT 21h/02h} output on its own line. Carriage returns are
     * dropped (DOS ends lines with CR LF) and a backspace removes the last character, as the console would show it.
     */
    public void appendOutputText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        FxDispatch.run(() -> {
            var current = new StringBuilder(outputText.get());
            for (char c : text.toCharArray()) {
                if (c == '\r') {
                    continue;
                }
                if (c == '\b') {
                    if (!current.isEmpty() && current.charAt(current.length() - 1) != '\n') {
                        current.setLength(current.length() - 1);
                    }
                    continue;
                }
                current.append(c);
            }
            outputText.set(current.toString());
        });
    }

    public ObjectProperty<ProgramInput> programInputProperty() {
        return programInput;
    }

    /** Lets the running program read what the user types in the Output panel; {@code NONE} stops it. */
    public void setProgramInput(ProgramInput mode, java.util.function.Consumer<String> sink) {
        this.programInputSink = sink == null ? text -> {} : sink;
        FxDispatch.run(() -> programInput.set(mode == null ? ProgramInput.NONE : mode));
    }

    /** Sends typed text to the running program. */
    public void sendProgramInput(String text) {
        if (text != null && !text.isEmpty() && programInput.get() != ProgramInput.NONE) {
            programInputSink.accept(text);
        }
    }

    public void clearOutput() {
        FxDispatch.run(() -> outputText.set(""));
    }

    public void setDiagnostics(List<Diagnostic> diagnostics) {
        setDiagnostics(diagnostics, null);
    }

    /** Replaces the problems; files inside {@code projectRoot} are listed relative to it. */
    public void setDiagnostics(List<Diagnostic> diagnostics, java.nio.file.Path projectRoot) {
        FxDispatch.run(() -> {
            problems.clear();
            if (diagnostics != null) {
                for (Diagnostic d : diagnostics) {
                    problems.add(new DiagnosticItemViewModel(d, projectRoot));
                }
            }
        });
    }

    /** Adds one problem to those already shown, such as a runtime error found while debugging. */
    public void addDiagnostic(Diagnostic diagnostic, java.nio.file.Path projectRoot) {
        if (diagnostic != null) {
            FxDispatch.run(() -> problems.add(new DiagnosticItemViewModel(diagnostic, projectRoot)));
        }
    }

    public void clearDiagnostics() {
        FxDispatch.run(problems::clear);
    }

    public void setReferences(List<ReferenceItemViewModel> items) {
        FxDispatch.run(() -> {
            references.clear();
            if (items != null) {
                references.addAll(items);
            }
        });
    }

    public void clearReferences() {
        FxDispatch.run(references::clear);
    }
}
