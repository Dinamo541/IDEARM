package io.github.dinamo541.idearm.app.editor;

import io.github.dinamo541.idearm.app.i18n.Localization;
import io.github.dinamo541.idearm.app.i18n.Problem;
import io.github.dinamo541.idearm.application.editor.CompletionItem;
import io.github.dinamo541.idearm.application.editor.HoverInfo;
import io.github.dinamo541.idearm.application.editor.HoverKind;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
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
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.TwoDimensional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
    private final CodeMinimap minimap;
    private final javafx.scene.layout.BorderPane editorRoot;
    private final EditorActions actions;
    private final ReadOnlyIntegerWrapper caretLine = new ReadOnlyIntegerWrapper(1);
    private final ReadOnlyIntegerWrapper caretColumn = new ReadOnlyIntegerWrapper(1);
    private final BooleanProperty modified = new SimpleBooleanProperty(false);
    private boolean suppressChangeEvents = false;

    private final HoverCardPopup hoverPopup = new HoverCardPopup();
    private final CompletionPopup completionPopup = new CompletionPopup();
    private Localization localization = new Localization();

    /**
     * The diagnostics to underline, grouped by their 1-based line. Kept here because RichTextFX overwrites a
     * paragraph's styles wholesale, so every restyle has to redraw the marks along with the syntax colours.
     */
    private final Map<Integer, List<Diagnostic>> diagnosticsByLine = new HashMap<>();
    private Runnable onTextChanged;

    private Consumer<String> onDefinitionRequested;
    private Consumer<String> onReferencesRequested;
    private Function<String, Optional<HoverInfo>> hoverProvider;
    private Function<String, List<CompletionItem>> completionProvider;

    private final Set<Integer> breakpoints = new ConcurrentSkipListSet<>();
    /** Breakpoints the user switched off: shown hollow, because the line still matters to them. */
    private final Set<Integer> disabledBreakpoints = new ConcurrentSkipListSet<>();
    private Integer executionLine = null;
    private Consumer<Integer> onBreakpointToggled;
    /** Set while a gutter repaint is already queued, so a run of steps does not queue one repaint per step. */
    private final java.util.concurrent.atomic.AtomicBoolean gutterRepaintQueued =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public RichTextFxEditorComponent() {
        this.codeArea = new CodeArea();
        this.codeArea.getStyleClass().add("idearm-code-area");
        this.codeArea.setParagraphGraphicFactory(createGutterFactory());

        this.scrollPane = new VirtualizedScrollPane<>(codeArea);
        this.actions = new EditorActions(codeArea, () -> this.localization);
        this.editorRoot = new javafx.scene.layout.BorderPane(scrollPane);
        this.minimap = new CodeMinimap(codeArea);
        this.minimap.setLocalization(localization);
        this.minimap.managedProperty().bind(minimap.visibleProperty());
        this.editorRoot.setRight(minimap);
        this.editorRoot.setTop(actions.searchBar());
        this.editorRoot.getStylesheets().add(
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

            // Hide hover card on edit
            if (hoverPopup.isShowing()) {
                hoverPopup.hide();
            }

            // Dynamically update or hide completion popup on edit
            if (completionPopup.isShowing()) {
                Platform.runLater(this::updateCompletionIfShowing);
            }

            // Calculate paragraph index affected by this change
            int changePos = change.getPosition();
            int paragraph = codeArea.offsetToPosition(changePos, TwoDimensional.Bias.Backward).getMajor();
            int insertedLines = (int) change.getInserted().chars().filter(ch -> ch == '\n').count();

            // Highlight all touched paragraphs
            for (int p = paragraph; p <= paragraph + insertedLines && p < codeArea.getParagraphs().size(); p++) {
                codeArea.setStyleSpans(p, 0, stylesFor(p, codeArea.getParagraph(p).getText()));
            }

            if (onTextChanged != null) {
                onTextChanged.run();
            }
        });

        setupKeyboardShortcuts();
        setupHoverListener();
        setupDismissalListeners();
    }

    public void setLocalization(Localization localization) {
        if (localization != null) {
            this.localization = localization;
            this.actions.localize();
            this.minimap.setLocalization(localization);
        }
    }

    private void setupDismissalListeners() {
        this.codeArea.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (completionPopup.isShowing()) {
                completionPopup.hide();
            }
            if (hoverPopup.isShowing()) {
                hoverPopup.hide();
            }
        });

        this.codeArea.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (completionPopup.isShowing()) {
                completionPopup.hide();
            }
            if (hoverPopup.isShowing()) {
                hoverPopup.hide();
            }
        });
    }

    private void updateCompletionIfShowing() {
        if (!completionPopup.isShowing() || completionProvider == null) {
            return;
        }
        String prefix = getPrefixAtCaret();
        List<CompletionItem> items = completionProvider.apply(prefix);
        if (items == null || items.isEmpty()) {
            completionPopup.hide();
        } else {
            completionPopup.updateItems(items);
        }
    }

    private void setupKeyboardShortcuts() {
        this.codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (hoverPopup.isShowing()) {
                hoverPopup.hide();
            }

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
            int charIndex = event.getCharacterIndex();

            // A marked word explains its own problem: that is more useful than what it happens to look up to.
            Optional<Diagnostic> marked = diagnosticAt(charIndex);
            if (marked.isPresent()) {
                var markPt = event.getScreenPosition();
                hoverPopup.showHover(codeArea, markPt.getX(), markPt.getY() + 16,
                        diagnosticHover(marked.get()), localization);
                return;
            }

            if (hoverProvider == null) {
                return;
            }
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
        if (hoverPopup.isShowing()) {
            hoverPopup.hide();
        }
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
            // The marks belonged to the text being replaced; a fresh validation pass brings the new ones.
            diagnosticsByLine.clear();
            minimap.setDiagnostics(List.of());
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
    public void setDiagnostics(List<Diagnostic> diagnostics) {
        if (!Platform.isFxApplicationThread()) {
            List<Diagnostic> published = diagnostics == null ? List.of() : List.copyOf(diagnostics);
            Platform.runLater(() -> setDiagnostics(published));
            return;
        }

        Set<Integer> previouslyMarked = new TreeSet<>(diagnosticsByLine.keySet());
        minimap.setDiagnostics(diagnostics);
        diagnosticsByLine.clear();
        if (diagnostics != null) {
            for (Diagnostic diagnostic : diagnostics) {
                Location location = diagnostic.location();
                if (location == null || location.line() == null || location.column() == null) {
                    continue;
                }
                diagnosticsByLine.computeIfAbsent(location.line(), line -> new ArrayList<>()).add(diagnostic);
            }
        }

        // Only the lines that gained or lost a mark are restyled: a whole-document pass on every validation would
        // cost far more than the ADR-005 typing budget allows.
        Set<Integer> touched = new TreeSet<>(previouslyMarked);
        touched.addAll(diagnosticsByLine.keySet());
        int paragraphCount = codeArea.getParagraphs().size();
        for (int line : touched) {
            int paragraph = line - 1;
            if (paragraph >= 0 && paragraph < paragraphCount) {
                codeArea.setStyleSpans(paragraph, 0, stylesFor(paragraph, codeArea.getParagraph(paragraph).getText()));
            }
        }
    }

    @Override
    public void setOnTextChanged(Runnable handler) {
        this.onTextChanged = handler;
    }

    /** The syntax colours of one paragraph with its diagnostic underlines laid over them. */
    private StyleSpans<Collection<String>> stylesFor(int paragraphIndex, String paragraphText) {
        return DiagnosticMarks.overlay(AssemblySyntaxHighlighter.computeHighlighting(paragraphText),
                diagnosticsByLine.get(paragraphIndex + 1), paragraphText);
    }

    /** The diagnostic marked at this character offset, if the pointer is inside one of its underlines. */
    private Optional<Diagnostic> diagnosticAt(int characterIndex) {
        if (diagnosticsByLine.isEmpty() || characterIndex < 0 || characterIndex >= codeArea.getLength()) {
            return Optional.empty();
        }
        var position = codeArea.offsetToPosition(characterIndex, TwoDimensional.Bias.Backward);
        List<Diagnostic> marks = diagnosticsByLine.get(position.getMajor() + 1);
        if (marks == null) {
            return Optional.empty();
        }

        String paragraphText = codeArea.getParagraph(position.getMajor()).getText();
        int column = position.getMinor();
        return marks.stream()
                .filter(diagnostic -> DiagnosticMarks.covers(diagnostic, paragraphText, column))
                .findFirst();
    }

    /** Shows a diagnostic in the hover card the instruction and symbol hovers already use. */
    private HoverInfo diagnosticHover(Diagnostic diagnostic) {
        return new HoverInfo(localization.describe(Problem.of(diagnostic)), null, null, null, null,
                HoverKind.DIAGNOSTIC);
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
        // Only the caret's line: a word never spans lines, and copying the whole document on every keystroke of
        // an open completion list cost time in proportion to the file's size.
        String text = codeArea.getParagraph(codeArea.getCurrentParagraph()).getText();
        int pos = Math.min(codeArea.getCaretColumn(), text.length());
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
        String text = codeArea.getParagraph(codeArea.getCurrentParagraph()).getText();
        int pos = Math.min(codeArea.getCaretColumn(), text.length());
        int lineStart = codeArea.getCaretPosition() - pos;

        int start = pos;
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }

        int end = pos;
        while (end < text.length() && isWordChar(text.charAt(end))) {
            end++;
        }

        codeArea.replaceText(lineStart + start, lineStart + end, replacement);
        codeArea.moveTo(lineStart + start + replacement.length());
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

    private String getWordAt(int documentPosition) {
        if (documentPosition < 0 || documentPosition > codeArea.getLength()) {
            return "";
        }
        // The line under the position is enough, and copying the whole document on every hover is not.
        var where = codeArea.offsetToPosition(documentPosition, TwoDimensional.Bias.Forward);
        String line = codeArea.getParagraph(where.getMajor()).getText();
        if (inCommentOrString(line, where.getMinor())) {
            // "mov" in "; mov the value" is prose, not an instruction to explain.
            return "";
        }
        return wordIn(line, where.getMinor());
    }

    /** Whether {@code column} of the line lies in a comment or a quoted string, read as the lexer reads them. */
    static boolean inCommentOrString(String line, int column) {
        char quote = 0;
        for (int i = 0; i < Math.min(column, line.length()); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == ';') {
                return true;
            }
        }
        return quote != 0;
    }

    /** The word, or the single {@code : [ ] $}, at {@code position} of one line. */
    static String wordIn(String text, int position) {
        if (text.isEmpty() || position < 0 || position > text.length()) {
            return "";
        }

        int index = Math.min(position, text.length() - 1);
        char currentCh = text.charAt(index);
        if (currentCh == ':' || currentCh == '[' || currentCh == ']') {
            return String.valueOf(currentCh);
        }

        if (!isWordChar(currentCh) && position > 0 && isWordChar(text.charAt(position - 1))) {
            index = position - 1;
        }

        if (!isWordChar(text.charAt(index))) {
            char fallbackCh = text.charAt(index);
            if (fallbackCh == ':' || fallbackCh == '[' || fallbackCh == ']' || fallbackCh == '$') {
                return String.valueOf(fallbackCh);
            }
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
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '@' || ch == '$' || ch == '?' || ch == '.' || ch == '%';
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
            boolean isDisabledBp = disabledBreakpoints.contains(line);
            boolean isExec = executionLine != null && executionLine == line;

            if (isExec) {
                marker.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.RUN.create(12));
                marker.getStyleClass().add("idearm-execution-arrow");
                marker.setVisible(true);
            } else if (isDisabledBp) {
                // A hollow ring: the breakpoint is remembered but will not stop the program.
                marker.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.BREAKPOINT_DISABLED.create(14));
                marker.getStyleClass().add("idearm-breakpoint-disabled");
                marker.setVisible(true);
            } else if (isBp) {
                marker.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.BREAKPOINT.create(14));
                marker.getStyleClass().add("idearm-breakpoint-active");
                marker.setVisible(true);
            } else {
                marker.setGraphic(io.github.dinamo541.idearm.app.ui.WorkbenchIcons.BREAKPOINT.create(14));
                marker.getStyleClass().add("idearm-breakpoint-ghost");
                marker.setVisible(false);
            }

            bpGutter.getChildren().add(marker);

            bpGutter.setOnMouseEntered(e -> {
                if (!isMarked(line)) {
                    marker.setVisible(true);
                }
            });
            bpGutter.setOnMouseExited(e -> {
                if (!isMarked(line)) {
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
    public void setDisabledBreakpoints(Set<Integer> lines) {
        disabledBreakpoints.clear();
        if (lines != null) {
            disabledBreakpoints.addAll(lines);
        }
        updateGutter();
    }

    /** Whether the line already shows something in the gutter, so the hover ghost must stay out of the way. */
    private boolean isMarked(int line) {
        return breakpoints.contains(line) || disabledBreakpoints.contains(line)
                || (executionLine != null && executionLine == line);
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
        Integer previous = this.executionLine;
        this.executionLine = line;
        Platform.runLater(() -> highlightExecutionRow(previous, line));
        updateGutter();
        if (line != null && line > 0) {
            goToLine(line);
        }
    }

    /**
     * Marks the whole row of the instruction about to run, not only the arrow in the margin: while stepping, the
     * eye follows a highlighted line far more easily than an 18-pixel glyph.
     */
    private void highlightExecutionRow(Integer previous, Integer current) {
        int paragraphs = codeArea.getParagraphs().size();
        if (previous != null && previous >= 1 && previous <= paragraphs) {
            codeArea.setParagraphStyle(previous - 1, List.of());
        }
        if (current != null && current >= 1 && current <= paragraphs) {
            codeArea.setParagraphStyle(current - 1, List.of("idearm-execution-line"));
        }
    }

    /**
     * Queues one gutter repaint. The factory is asked for the visible paragraphs only, but a run of steps would
     * otherwise queue a repaint per step, so they are coalesced into one.
     */
    private void updateGutter() {
        if (gutterRepaintQueued.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                gutterRepaintQueued.set(false);
                minimap.setDebugMarks(breakpoints, executionLine);
                codeArea.setParagraphGraphicFactory(createGutterFactory());
            });
        }
    }

    public CodeArea getCodeArea() {
        return codeArea;
    }

    public void setMinimapVisible(boolean visible) { minimap.setVisible(visible); }
}
