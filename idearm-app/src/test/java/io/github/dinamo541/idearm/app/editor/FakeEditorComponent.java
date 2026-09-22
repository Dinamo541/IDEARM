package io.github.dinamo541.idearm.app.editor;

import io.github.dinamo541.idearm.application.editor.CompletionItem;
import io.github.dinamo541.idearm.application.editor.HoverInfo;
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
