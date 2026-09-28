package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import java.util.ArrayList;
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
    /**
     * The two sources of problems are kept apart so neither erases the other: a build reports what the assembler
     * said, while validation reports what the editor found while typing, and both runs happen on their own clock.
     */
    private final List<DiagnosticItemViewModel> buildProblems = new ArrayList<>();
    private final List<DiagnosticItemViewModel> liveProblems = new ArrayList<>();
    private final ObservableList<ReferenceItemViewModel> references = FXCollections.observableArrayList();
    private final DebugViewModel debugViewModel = new DebugViewModel();
    private TerminalViewModel terminalViewModel;
    private final StringProperty buildText = new SimpleStringProperty("");
    private final StringProperty outputText = new SimpleStringProperty("");

    /**
     * The Output panel keeps at most this many characters: a program printing in an endless loop would otherwise grow
     * the text, and the time to copy it, without limit.
     */
    static final int MAX_OUTPUT_CHARS = 1_000_000;
    /** Where the oldest output was dropped. */
    static final String TRUNCATED_MARKER = "\u2026\n";

    /** Program output not shown yet; a single JavaFX task drains it, however many writes arrive before it runs. */
    private final StringBuilder pendingOutput = new StringBuilder();
    private boolean outputFlushScheduled;
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
            flushPendingOutput();
            String current = outputText.get();
            String separator = current.isEmpty() || current.endsWith("\n") ? "" : "\n";
            outputText.set(capped(new StringBuilder(current).append(separator).append(line).append('\n')));
        });
    }

    /**
     * Adds what the program printed exactly as it printed it: DOS writes characters one at a time, and adding a line
     * break after each call put every character of {@code INT 21h/02h} output on its own line. Carriage returns are
     * dropped (DOS ends lines with CR LF) and a backspace removes the last character, as the console would show it.
     *
     * <p>Writes from the program's thread are collected and shown by one JavaFX task, so a program that prints
     * without pause cannot flood the JavaFX thread with one task per character.
     */
    public void appendOutputText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        boolean schedule;
        synchronized (pendingOutput) {
            pendingOutput.append(text);
            schedule = !outputFlushScheduled;
            outputFlushScheduled = true;
        }
        if (schedule) {
            FxDispatch.run(this::flushPendingOutput);
        }
    }

    private void flushPendingOutput() {
        String text;
        synchronized (pendingOutput) {
            text = pendingOutput.toString();
            pendingOutput.setLength(0);
            outputFlushScheduled = false;
        }
        if (text.isEmpty()) {
            return;
        }
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
        outputText.set(capped(current));
    }

    /** The text without its oldest part when it is over {@link #MAX_OUTPUT_CHARS}, cut at a line start if possible. */
    private static String capped(StringBuilder text) {
        if (text.length() <= MAX_OUTPUT_CHARS) {
            return text.toString();
        }
        int start = text.length() - MAX_OUTPUT_CHARS * 3 / 4;
        int lineStart = text.indexOf("\n", start);
        if (lineStart >= 0 && lineStart - start < 4096) {
            start = lineStart + 1;
        }
        return TRUNCATED_MARKER + text.substring(start);
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
        synchronized (pendingOutput) {
            pendingOutput.setLength(0);
        }
        FxDispatch.run(() -> outputText.set(""));
    }

    public void setDiagnostics(List<Diagnostic> diagnostics) {
        setDiagnostics(diagnostics, null);
    }

    /** Replaces the problems the build reported; files inside {@code projectRoot} are listed relative to it. */
    public void setDiagnostics(List<Diagnostic> diagnostics, java.nio.file.Path projectRoot) {
        FxDispatch.run(() -> {
            replace(buildProblems, diagnostics, projectRoot);
            publishProblems();
        });
    }

    /**
     * Replaces the problems the editor found while typing. Kept separate from the build's, because a validation pass
     * runs every few hundred milliseconds and must not wipe what the assembler reported.
     */
    public void setLiveDiagnostics(List<Diagnostic> diagnostics, java.nio.file.Path projectRoot) {
        FxDispatch.run(() -> {
            replace(liveProblems, diagnostics, projectRoot);
            publishProblems();
        });
    }

    /** Adds one problem to those already shown, such as a runtime error found while debugging. */
    public void addDiagnostic(Diagnostic diagnostic, java.nio.file.Path projectRoot) {
        if (diagnostic != null) {
            FxDispatch.run(() -> {
                buildProblems.add(new DiagnosticItemViewModel(diagnostic, projectRoot));
                publishProblems();
            });
        }
    }

    public void clearDiagnostics() {
        FxDispatch.run(() -> {
            buildProblems.clear();
            liveProblems.clear();
            publishProblems();
        });
    }

    private static void replace(List<DiagnosticItemViewModel> target, List<Diagnostic> diagnostics,
                                java.nio.file.Path projectRoot) {
        target.clear();
        if (diagnostics != null) {
            for (Diagnostic d : diagnostics) {
                target.add(new DiagnosticItemViewModel(d, projectRoot));
            }
        }
    }

    /** Build problems first, then the live ones, so the order the user already knows does not shift under them. */
    private void publishProblems() {
        problems.setAll(concat(buildProblems, liveProblems));
    }

    private static List<DiagnosticItemViewModel> concat(List<DiagnosticItemViewModel> first,
                                                       List<DiagnosticItemViewModel> second) {
        List<DiagnosticItemViewModel> all = new ArrayList<>(first.size() + second.size());
        all.addAll(first);
        all.addAll(second);
        return all;
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
