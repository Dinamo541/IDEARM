package io.github.dinamo541.idearm.app.editor;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.application.editor.CompletionItem;
import io.github.dinamo541.idearm.application.editor.HoverInfo;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.TwoDimensional;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Concrete implementation of {@link EditorComponent} wrapping RichTextFX {@link CodeArea}.
 *
 * <p>Uses per-paragraph syntax highlighting to keep typing latency strictly below 50 ms
 * even for large source files (>100 KB, ~5000 lines).
 *
 * <p>Integrates educational features:
 * <ul>
 *   <li>F12: Go to Definition</li>
 *   <li>Shift+F12: Find References</li>
 *   <li>Ctrl+Space: Autocompletion dropdown</li>
 *   <li>Mouse hover: Educational instruction card & numeric base conversions</li>
 * </ul>
 */
public final class RichTextFxEditorComponent implements EditorComponent {

    private final CodeArea codeArea;
    private final VirtualizedScrollPane<CodeArea> scrollPane;
    private final javafx.scene.layout.BorderPane editorRoot;
    private final EditorActions actions;
    private final ReadOnlyIntegerWrapper caretLine = new ReadOnlyIntegerWrapper(1);
    private final ReadOnlyIntegerWrapper caretColumn = new ReadOnlyIntegerWrapper(1);
    private final BooleanProperty modified = new SimpleBooleanProperty(false);
    private boolean suppressChangeEvents = false;

    private final HoverCardPopup hoverPopup = new HoverCardPopup();
    private final CompletionPopup completionPopup = new CompletionPopup();
    private Localization localization = new Localization();

    private Consumer<String> onDefinitionRequested;
    private Consumer<String> onReferencesRequested;
    private Function<String, Optional<HoverInfo>> hoverProvider;
    private Function<String, List<CompletionItem>> completionProvider;

    private final Set<Integer> breakpoints = new ConcurrentSkipListSet<>();
    private Integer executionLine = null;
    private Consumer<Integer> onBreakpointToggled;

    public RichTextFxEditorComponent() {
        this.codeArea = new CodeArea();
        this.codeArea.getStyleClass().add("idearm-code-area");
        this.codeArea.setParagraphGraphicFactory(createGutterFactory());

        this.scrollPane = new VirtualizedScrollPane<>(codeArea);
        this.actions = new EditorActions(codeArea, () -> this.localization);
        this.editorRoot = new javafx.scene.layout.BorderPane(scrollPane);
        this.editorRoot.setTop(actions.searchBar());
        this.scrollPane.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("editor.css"), "editor.css not found").toExternalForm()
        );

        // Bind caret line & column (1-based for user display)
        this.codeArea.currentParagraphProperty().addListener((obs, oldVal, newVal) ->
                caretLine.set(newVal != null ? newVal.intValue() + 1 : 1));
        this.codeArea.caretColumnProperty().addListener((obs, oldVal, newVal) ->
                caretColumn.set(newVal != null ? newVal.intValue() + 1 : 1));

        // Listen for text changes to update dirty status and apply per-paragraph styling
        this.codeArea.plainTextChanges().subscribe(change -> {
            if (suppressChangeEvents) {
                return;
            }
            modified.set(true);

            // Hide popups on edit
            if (hoverPopup.isShowing()) {
                hoverPopup.hide();
            }

            // Calculate paragraph index affected by this change
            int changePos = change.getPosition();
            int paragraph = codeArea.offsetToPosition(changePos, TwoDimensional.Bias.Backward).getMajor();
            int insertedLines = (int) change.getInserted().chars().filter(ch -> ch == '\n').count();

            // Highlight all touched paragraphs
            for (int p = paragraph; p <= paragraph + insertedLines && p < codeArea.getParagraphs().size(); p++) {
                String paragraphText = codeArea.getParagraph(p).getText();
                StyleSpans<Collection<String>> spans = AssemblySyntaxHighlighter.computeHighlighting(paragraphText);
                codeArea.setStyleSpans(p, 0, spans);
            }
        });

        setupKeyboardShortcuts();
        setupHoverListener();
    }

    public void setLocalization(Localization localization) {
        if (localization != null) {
            this.localization = localization;
            this.actions.localize();
        }
    }

    private void setupKeyboardShortcuts() {
        this.codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            // 0. F9 -> Toggle Breakpoint on current line
            if (event.getCode() == KeyCode.F9) {
                toggleBreakpoint(caretLine.get());
                event.consume();
                return;
            }

            // 1. If completion popup is showing, forward navigation keys
            if (completionPopup.isShowing() && completionPopup.handleEditorKeyEvent(event)) {
                return;
            }
            if (actions.handle(event)) return;

            // 2. F12 -> Go to Definition
            if (event.getCode() == KeyCode.F12 && !event.isShiftDown()) {
                String word = getWordAtCaret();
                if (word != null && !word.isBlank() && onDefinitionRequested != null) {
                    onDefinitionRequested.accept(word);
                    event.consume();
                }
                return;
            }

            // 3. Shift+F12 -> Find References
            if (event.getCode() == KeyCode.F12 && event.isShiftDown()) {
                String word = getWordAtCaret();
                if (word != null && !word.isBlank() && onReferencesRequested != null) {
                    onReferencesRequested.accept(word);
                    event.consume();
                }
                return;
            }

            // 4. Ctrl+Space -> Trigger Autocompletion
            if (event.getCode() == KeyCode.SPACE && event.isControlDown()) {
                triggerCompletion();
                event.consume();
            }
        });
    }

    private void setupHoverListener() {
        this.codeArea.setMouseOverTextDelay(Duration.ofMillis(350));

        this.codeArea.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, event -> {
            if (hoverProvider == null) {
                return;
            }
            int charIndex = event.getCharacterIndex();
            String word = getWordAt(charIndex);
            if (word == null || word.isBlank()) {
                return;
            }

            Optional<HoverInfo> infoOpt = hoverProvider.apply(word);
            if (infoOpt.isPresent()) {
                var screenPt = event.getScreenPosition();
                hoverPopup.showHover(codeArea, screenPt.getX(), screenPt.getY() + 16, infoOpt.get(), localization);
            }
        });

        this.codeArea.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, event -> {
            if (hoverPopup.isShowing()) {
                hoverPopup.hide();
            }
        });
    }

    private void triggerCompletion() {
        if (completionProvider == null) {
            return;
        }
        String prefix = getPrefixAtCaret();
        List<CompletionItem> items = completionProvider.apply(prefix);
        if (items == null || items.isEmpty()) {
            return;
        }

        int pos = codeArea.getCaretPosition();
        var boundsOpt = codeArea.getCharacterBoundsOnScreen(Math.max(0, pos - 1), pos);
        double screenX;
        double screenY;
        if (boundsOpt.isPresent()) {
            var bounds = boundsOpt.get();
            screenX = bounds.getMinX();
            screenY = bounds.getMaxY() + 4;
        } else {
            var localPt = codeArea.localToScreen(20, 40);
            screenX = localPt != null ? localPt.getX() : 100;
            screenY = localPt != null ? localPt.getY() : 100;
        }

        completionPopup.showCompletions(codeArea, screenX, screenY, items, selected -> {
            replaceWordAtCaret(selected.insertText());
            codeArea.requestFocus();
        });
    }

    @Override
    public Node getNode() {
        return editorRoot;
    }

    public void execute(EditorCommand command) { actions.execute(command); }

    @Override
    public String getText() {
        return codeArea.getText();
    }

    @Override
    public void setText(String text) {
        suppressChangeEvents = true;
        try {
            codeArea.replaceText(text != null ? text : "");
            if (text != null && !text.isEmpty()) {
                StyleSpans<Collection<String>> spans = AssemblySyntaxHighlighter.computeHighlighting(text);
                codeArea.setStyleSpans(0, spans);
            }
            modified.set(false);
            codeArea.moveTo(0, 0);
        } finally {
            suppressChangeEvents = false;
        }
    }

    @Override
    public void goToLine(int lineNumber) {
        int totalLines = codeArea.getParagraphs().size();
        int target = Math.clamp(lineNumber, 1, Math.max(1, totalLines)) - 1;
        codeArea.moveTo(target, 0);
        codeArea.requestFollowCaret();
        Platform.runLater(codeArea::requestFocus);
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
        codeArea.requestFocus();
    }

    @Override
    public int getCaretPosition() {
        return codeArea.getCaretPosition();
    }

    @Override
    public String getWordAtCaret() {
        return getWordAt(codeArea.getCaretPosition());
    }

    @Override
    public String getPrefixAtCaret() {
        String text = codeArea.getText();
        int pos = codeArea.getCaretPosition();
        if (text.isEmpty() || pos <= 0) {
            return "";
        }

        int start = pos;
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        return text.substring(start, pos);
    }

    @Override
    public void replaceWordAtCaret(String replacement) {
        if (replacement == null) {
            return;
        }
        String text = codeArea.getText();
        int pos = codeArea.getCaretPosition();

        int start = pos;
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }

        int end = pos;
        while (end < text.length() && isWordChar(text.charAt(end))) {
            end++;
        }

        codeArea.replaceText(start, end, replacement);
        codeArea.moveTo(start + replacement.length());
    }

    @Override
    public void setOnDefinitionRequested(Consumer<String> handler) {
        this.onDefinitionRequested = handler;
    }

    @Override
    public void setOnReferencesRequested(Consumer<String> handler) {
        this.onReferencesRequested = handler;
    }

    @Override
    public void setHoverProvider(Function<String, Optional<HoverInfo>> provider) {
        this.hoverProvider = provider;
    }

    @Override
    public void setCompletionProvider(Function<String, List<CompletionItem>> provider) {
        this.completionProvider = provider;
    }

    private String getWordAt(int position) {
        String text = codeArea.getText();
        if (text.isEmpty() || position < 0 || position > text.length()) {
            return "";
        }

        int index = Math.min(position, text.length() - 1);
        if (!isWordChar(text.charAt(index)) && position > 0 && isWordChar(text.charAt(position - 1))) {
            index = position - 1;
        }

        if (!isWordChar(text.charAt(index))) {
            return "";
        }

        int start = index;
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }

        int end = index;
        while (end < text.length() - 1 && isWordChar(text.charAt(end + 1))) {
            end++;
        }

        return text.substring(start, end + 1);
    }

    private static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '@' || ch == '$' || ch == '?' || ch == '.';
    }

    private IntFunction<Node> createGutterFactory() {
        IntFunction<Node> lineNumbers = LineNumberFactory.get(codeArea);
        return paragraphIndex -> {
            int line = paragraphIndex + 1;
            Node lineNode = lineNumbers.apply(paragraphIndex);

            StackPane bpGutter = new StackPane();
            bpGutter.setPrefWidth(18);
            bpGutter.setMinWidth(18);
            bpGutter.setMaxWidth(18);
            bpGutter.getStyleClass().add("idearm-gutter-breakpoint");

            Label marker = new Label();
            marker.setAlignment(Pos.CENTER);

            boolean isBp = breakpoints.contains(line);
            boolean isExec = executionLine != null && executionLine == line;

            if (isExec) {
                marker.setText("▶");
                marker.getStyleClass().add("idearm-execution-arrow");
                marker.setVisible(true);
            } else if (isBp) {
                marker.setText("●");
                marker.getStyleClass().add("idearm-breakpoint-active");
                marker.setVisible(true);
            } else {
                marker.setText("●");
                marker.getStyleClass().add("idearm-breakpoint-ghost");
                marker.setVisible(false);
            }

            bpGutter.getChildren().add(marker);

            bpGutter.setOnMouseEntered(e -> {
                if (!breakpoints.contains(line) && (executionLine == null || executionLine != line)) {
                    marker.setVisible(true);
                }
            });
            bpGutter.setOnMouseExited(e -> {
                if (!breakpoints.contains(line) && (executionLine == null || executionLine != line)) {
                    marker.setVisible(false);
                }
            });
            bpGutter.setOnMouseClicked(e -> toggleBreakpoint(line));

            HBox compound = new HBox(bpGutter, lineNode);
            compound.setAlignment(Pos.CENTER_LEFT);
            compound.getStyleClass().add("idearm-compound-gutter");
            return compound;
        };
    }

    @Override
    public void toggleBreakpoint(int line) {
        if (breakpoints.contains(line)) {
            breakpoints.remove(line);
        } else {
            breakpoints.add(line);
        }
        updateGutter();
        if (onBreakpointToggled != null) {
            onBreakpointToggled.accept(line);
        }
    }

    @Override
    public void setBreakpoints(Set<Integer> lines) {
        breakpoints.clear();
        if (lines != null) {
            breakpoints.addAll(lines);
        }
        updateGutter();
    }

    @Override
    public Set<Integer> getBreakpoints() {
        return Collections.unmodifiableSet(breakpoints);
    }

    @Override
    public void setOnBreakpointToggled(Consumer<Integer> handler) {
        this.onBreakpointToggled = handler;
    }

    @Override
    public void setExecutionLine(Integer line) {
        this.executionLine = line;
        updateGutter();
        if (line != null && line > 0) {
            goToLine(line);
        }
    }

    private void updateGutter() {
        Platform.runLater(() -> codeArea.setParagraphGraphicFactory(createGutterFactory()));
    }

    public CodeArea getCodeArea() {
        return codeArea;
    }
}
