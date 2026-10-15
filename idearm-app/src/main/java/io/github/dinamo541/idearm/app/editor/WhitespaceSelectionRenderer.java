package io.github.dinamo541.idearm.app.editor;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.IndexRange;
import javafx.scene.text.TextFlow;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.TextExt;
import org.reactfx.Subscription;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * High-fidelity whitespace rendering on selection, matching VS Code's
 * {@code "editor.renderWhitespace": "selection"} behavior.
 *
 * <p>When text is selected in the editor, space characters within the active
 * selection range are dynamically rendered as middle dots ({@code '\u00B7'}),
 * while characters outside the selection remain normal spaces.
 *
 * <p>Crucially, this operates purely at the visual presentation layer (scenegraph
 * {@link TextExt} nodes), leaving the underlying document text, file serialization,
 * clipboard copying, and assembly linter parsing completely untouched.
 */
public final class WhitespaceSelectionRenderer {

    private static final String ORIG_TEXT_KEY = "idearm.originalText";
    private static final char MIDDLE_DOT = '\u00B7';

    private final CodeArea codeArea;
    private final Set<TextExt> modifiedNodes = new HashSet<>();
    private final Set<org.fxmisc.richtext.SelectionPath> clippedSelectionNodes = new HashSet<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private boolean enabled = true;

    public WhitespaceSelectionRenderer(CodeArea codeArea) {
        this.codeArea = codeArea;
        setupListeners();
    }

    private void setupListeners() {
        // Selection change listener
        codeArea.selectionProperty().addListener((obs, oldSel, newSel) -> {
            if (enabled) {
                updateWhitespace();
            }
        });

        // Viewport scrolling / layout events
        subscriptions.add(codeArea.viewportDirtyEvents().subscribe(event -> {
            if (enabled && codeArea.getSelection().getLength() > 0) {
                // Run after layout so newly visible cells have populated scenegraph nodes
                Platform.runLater(this::updateWhitespace);
            }
        }));

        // Plain text edits
        subscriptions.add(codeArea.plainTextChanges().subscribe(change -> {
            if (enabled && codeArea.getSelection().getLength() > 0) {
                Platform.runLater(this::updateWhitespace);
            }
        }));
    }

    /**
     * Updates the visible paragraph boxes according to the current selection range.
     */
    public void updateWhitespace() {
        if (!enabled) {
            restoreAll();
            return;
        }

        IndexRange selection = codeArea.getSelection();
        if (selection.getLength() == 0) {
            restoreAll();
            return;
        }

        int selStart = selection.getStart();
        int selEnd = selection.getEnd();

        var boxNodes = codeArea.lookupAll(".paragraph-box");
        var boxList = new ArrayList<>(boxNodes);
        boxList.sort(java.util.Comparator.comparingDouble(n -> n.getBoundsInParent().getMinY()));

        for (int v = 0; v < boxList.size(); v++) {
            Node box = boxList.get(v);
            int allPar;
            try {
                allPar = codeArea.visibleParToAllParIndex(v);
            } catch (Exception ignored) {
                continue;
            }

            if (allPar < 0 || allPar >= codeArea.getParagraphs().size()) {
                restoreBox(box);
                continue;
            }

            int parStart = codeArea.getAbsolutePosition(allPar, 0);
            int parLen = codeArea.getParagraph(allPar).length();
            int parEnd = parStart + parLen;

            if (parEnd < selStart || parStart > selEnd) {
                restoreBox(box);
                continue;
            }

            int segOffset = 0;
            double maxX = 0;
            var textExtNodes = extractTextExtNodes(box);
            for (TextExt textExt : textExtNodes) {
                maxX = Math.max(maxX, textExt.getBoundsInParent().getMaxX());
                String orig = getOriginalText(textExt);
                int segStart = parStart + segOffset;
                int segEnd = segStart + orig.length();
                segOffset += orig.length();

                if (segEnd <= selStart || segStart >= selEnd) {
                    restoreTextExt(textExt);
                } else {
                    applyWhitespace(textExt, orig, segStart, selStart, selEnd);
                }
            }

            var sp = findSelectionPath(box);
            if (sp != null) {
                double clipWidth = Math.max(9.0, maxX + 8.0);
                double h = Math.max(16.0, box.getLayoutBounds().getHeight());
                var clip = new javafx.scene.shape.Rectangle(0, -2, clipWidth, h + 4);
                clip.setArcWidth(7);
                clip.setArcHeight(7);
                sp.setClip(clip);
                clippedSelectionNodes.add(sp);
            }
        }
    }

    private void applyWhitespace(TextExt textExt, String orig, int segStart, int selStart, int selEnd) {
        StringBuilder sb = new StringBuilder(orig.length());
        boolean hasDot = false;

        for (int i = 0; i < orig.length(); i++) {
            char ch = orig.charAt(i);
            int docPos = segStart + i;

            if (docPos >= selStart && docPos < selEnd && ch == ' ') {
                sb.append(MIDDLE_DOT);
                hasDot = true;
            } else {
                sb.append(ch);
            }
        }

        String target = sb.toString();
        if (hasDot) {
            if (!textExt.getText().equals(target)) {
                textExt.setText(target);
            }
            modifiedNodes.add(textExt);
        } else {
            restoreTextExt(textExt);
        }
    }

    private String getOriginalText(TextExt textExt) {
        Object stored = textExt.getProperties().get(ORIG_TEXT_KEY);
        if (stored instanceof String s) {
            return s;
        }
        String current = textExt.getText();
        textExt.getProperties().put(ORIG_TEXT_KEY, current);
        return current;
    }

    private void restoreTextExt(TextExt textExt) {
        Object stored = textExt.getProperties().get(ORIG_TEXT_KEY);
        if (stored instanceof String s) {
            if (!textExt.getText().equals(s)) {
                textExt.setText(s);
            }
        }
        modifiedNodes.remove(textExt);
    }

    private void restoreBox(Node box) {
        for (TextExt textExt : extractTextExtNodes(box)) {
            restoreTextExt(textExt);
        }
        var sp = findSelectionPath(box);
        if (sp != null) {
            sp.setClip(null);
            clippedSelectionNodes.remove(sp);
        }
    }

    private void restoreAll() {
        if (!modifiedNodes.isEmpty()) {
            for (TextExt textExt : new ArrayList<>(modifiedNodes)) {
                restoreTextExt(textExt);
            }
            modifiedNodes.clear();
        }
        if (!clippedSelectionNodes.isEmpty()) {
            for (org.fxmisc.richtext.SelectionPath sp : clippedSelectionNodes) {
                sp.setClip(null);
            }
            clippedSelectionNodes.clear();
        }
    }

    private org.fxmisc.richtext.SelectionPath findSelectionPath(Node box) {
        if (box instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (child instanceof TextFlow flow) {
                    for (Node seg : flow.getChildren()) {
                        if (seg instanceof org.fxmisc.richtext.SelectionPath sp) {
                            return sp;
                        }
                    }
                }
            }
        }
        return null;
    }

    private List<TextExt> extractTextExtNodes(Node box) {
        List<TextExt> list = new ArrayList<>();
        if (box instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (child instanceof TextFlow flow) {
                    for (Node seg : flow.getChildren()) {
                        if (seg instanceof TextExt textExt) {
                            list.add(textExt);
                        }
                    }
                }
            }
        }
        return list;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            restoreAll();
        } else {
            updateWhitespace();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void dispose() {
        restoreAll();
        for (Subscription sub : subscriptions) {
            sub.unsubscribe();
        }
        subscriptions.clear();
    }
}
