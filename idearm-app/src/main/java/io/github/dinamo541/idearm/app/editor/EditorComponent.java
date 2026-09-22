package io.github.dinamo541.idearm.app.editor;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.scene.Node;

/**
 * Presentation abstraction for the code editor (ADR-005).
 *
 * <p>Wraps the concrete editor control (RichTextFX CodeArea) so that the workbench,
 * tabs, and navigation logic do not couple directly to a third-party library.
 */
public interface EditorComponent {

    /** The JavaFX visual node of this editor to embed into layouts. */
    Node getNode();

    /** Gets the complete text content. */
    String getText();

    /** Sets the text content and resets dirty status. */
    void setText(String text);

    /** Navigates the caret to the 1-based line number and scrolls it into view. */
    void goToLine(int lineNumber);

    /** 1-based line index of current caret position. */
    ReadOnlyIntegerProperty caretLineProperty();

    /** 1-based column index of current caret position. */
    ReadOnlyIntegerProperty caretColumnProperty();

    /** Whether the editor has unsaved in-memory modifications. */
    BooleanProperty modifiedProperty();

    /** Requests keyboard focus. */
    void requestFocus();

    /** Gets the absolute 0-based character offset of the caret. */
    int getCaretPosition();

    /** Gets the identifier or word at the current caret position. */
    String getWordAtCaret();

    /** Gets the prefix typed immediately before the caret (for autocompletion). */
    String getPrefixAtCaret();

    /** Replaces the current word/prefix at the caret with the specified replacement text. */
    void replaceWordAtCaret(String replacement);

    /** Registers a handler invoked when the user requests "Go to Definition" (F12 or Ctrl+Click). */
    void setOnDefinitionRequested(java.util.function.Consumer<String> handler);

    /** Registers a handler invoked when the user requests "Find References" (Shift+F12). */
    void setOnReferencesRequested(java.util.function.Consumer<String> handler);

    /** Registers a provider invoked to retrieve hover information for a word under the mouse. */
    void setHoverProvider(java.util.function.Function<String, java.util.Optional<io.github.dinamo541.idearm.application.editor.HoverInfo>> provider);

    /** Registers a provider invoked to retrieve autocompletion candidates for a prefix. */
    void setCompletionProvider(java.util.function.Function<String, java.util.List<io.github.dinamo541.idearm.application.editor.CompletionItem>> provider);

    /** Toggles a breakpoint on the given 1-based line. */
    void toggleBreakpoint(int line);

    /** Sets the full set of 1-based lines that have active breakpoints. */
    void setBreakpoints(java.util.Set<Integer> lines);

    /** Gets the current set of 1-based lines with breakpoints. */
    java.util.Set<Integer> getBreakpoints();

    /** Registers a listener invoked when a breakpoint is toggled in the editor gutter or via shortcut. */
    void setOnBreakpointToggled(java.util.function.Consumer<Integer> handler);

    /** Sets or clears (null) the 1-based line of the current instruction pointer. */
    void setExecutionLine(Integer line);
}
