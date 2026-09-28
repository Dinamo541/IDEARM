package io.github.dinamo541.idearm.app.editor;

import io.github.dinamo541.idearm.application.editor.CompletionItem;
import io.github.dinamo541.idearm.application.editor.HoverInfo;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public final class FakeEditorComponent implements EditorComponent {

    private String text = "";
    private List<Diagnostic> diagnostics = List.of();
    private int diagnosticsSetCount = 0;
    private Runnable textChangedHandler;
    private final BooleanProperty modified = new SimpleBooleanProperty(false);
    private final ReadOnlyIntegerWrapper caretLine = new ReadOnlyIntegerWrapper(1);
    private final ReadOnlyIntegerWrapper caretColumn = new ReadOnlyIntegerWrapper(1);
    private int caretPosition = 0;
    private Consumer<String> definitionHandler;
    private Consumer<String> referencesHandler;
    private Function<String, Optional<HoverInfo>> hoverProvider;
    private Function<String, List<CompletionItem>> completionProvider;

    @Override
    public Node getNode() {
        return new Region();
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public void setText(String text) {
        this.text = text != null ? text : "";
        this.modified.set(false);
        if (textChangedHandler != null) {
            textChangedHandler.run();
        }
    }

    @Override
    public void setDiagnostics(List<Diagnostic> diagnostics) {
        this.diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        this.diagnosticsSetCount++;
    }

    @Override
    public void setOnTextChanged(Runnable handler) {
        this.textChangedHandler = handler;
    }

    /** The diagnostics the workbench last published to this editor. */
    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    /** How many times diagnostics were published, which is what a debounce test counts. */
    public int getDiagnosticsSetCount() {
        return diagnosticsSetCount;
    }

    @Override
    public void goToLine(int lineNumber) {
        caretLine.set(lineNumber);
    }

    @Override
    public ReadOnlyIntegerProperty caretLineProperty() {
        return caretLine.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyIntegerProperty caretColumnProperty() {
        return caretColumn.getReadOnlyProperty();
    }

    @Override
    public BooleanProperty modifiedProperty() {
        return modified;
    }

    @Override
    public void requestFocus() {
    }

    @Override
    public int getCaretPosition() {
        return caretPosition;
    }

    public void setCaretPosition(int pos) {
        this.caretPosition = pos;
    }

    @Override
    public String getWordAtCaret() {
        return "";
    }

    @Override
    public String getPrefixAtCaret() {
        return "";
    }

    @Override
    public void replaceWordAtCaret(String replacement) {
        this.text = replacement != null ? replacement : "";
    }

    @Override
    public void setOnDefinitionRequested(Consumer<String> handler) {
        this.definitionHandler = handler;
    }

    @Override
    public void setOnReferencesRequested(Consumer<String> handler) {
        this.referencesHandler = handler;
    }

    @Override
    public void setHoverProvider(Function<String, Optional<HoverInfo>> provider) {
        this.hoverProvider = provider;
    }

    @Override
    public void setCompletionProvider(Function<String, List<CompletionItem>> provider) {
        this.completionProvider = provider;
    }

    public Consumer<String> getDefinitionHandler() {
        return definitionHandler;
    }

    public Consumer<String> getReferencesHandler() {
        return referencesHandler;
    }

    public Function<String, Optional<HoverInfo>> getHoverProvider() {
        return hoverProvider;
    }

    public Function<String, List<CompletionItem>> getCompletionProvider() {
        return completionProvider;
    }

    private final java.util.Set<Integer> breakpoints = new java.util.HashSet<>();
    private final java.util.Set<Integer> disabledBreakpoints = new java.util.HashSet<>();
    private Consumer<Integer> breakpointToggledHandler;
    private Integer executionLine;

    @Override
    public void toggleBreakpoint(int line) {
        if (breakpoints.contains(line)) {
            breakpoints.remove(line);
        } else {
            breakpoints.add(line);
        }
        if (breakpointToggledHandler != null) {
            breakpointToggledHandler.accept(line);
        }
    }

    @Override
    public void setDisabledBreakpoints(java.util.Set<Integer> lines) {
        disabledBreakpoints.clear();
        if (lines != null) {
            disabledBreakpoints.addAll(lines);
        }
    }

    /** The lines whose breakpoint is switched off, as the workbench last published them. */
    public java.util.Set<Integer> getDisabledBreakpoints() {
        return java.util.Collections.unmodifiableSet(disabledBreakpoints);
    }

    @Override
    public void setBreakpoints(java.util.Set<Integer> lines) {
        breakpoints.clear();
        if (lines != null) {
            breakpoints.addAll(lines);
        }
    }

    @Override
    public java.util.Set<Integer> getBreakpoints() {
        return java.util.Collections.unmodifiableSet(breakpoints);
    }

    @Override
    public void setOnBreakpointToggled(Consumer<Integer> handler) {
        this.breakpointToggledHandler = handler;
    }

    @Override
    public void setExecutionLine(Integer line) {
        this.executionLine = line;
    }

    public Integer getExecutionLine() {
        return executionLine;
    }
}
