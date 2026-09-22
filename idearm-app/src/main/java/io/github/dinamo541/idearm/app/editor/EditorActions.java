package io.github.dinamo541.idearm.app.editor;

import io.github.dinamo541.idearm.app.i18n.Localization;
import java.util.function.Supplier;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import org.fxmisc.richtext.CodeArea;

/** Focus-scoped editing commands and a non-modal find/replace widget. */
final class EditorActions {
    private final CodeArea area;
    private final Supplier<Localization> localization;
    private final VBox search = new VBox(4);
    private final TextField query = new TextField();
    private final TextField replacement = new TextField();
    private final Label count = new Label();
    private final java.util.Map<Button, String> labels = new java.util.HashMap<>();
    private final HBox replaceRow;

    EditorActions(CodeArea area, Supplier<Localization> localization) {
        this.area = area;
        this.localization = localization;
        query.setId("editor-find");
        replacement.setId("editor-replace");
        search.getStyleClass().add("editor-search");
        var first = new HBox(6, query, count, button("↑", () -> find(false)),
                button("↓", () -> find(true)), button("×", this::hide));
        HBox.setHgrow(query, Priority.ALWAYS);
        replaceRow = new HBox(6, replacement, button("editor.replaceOne", this::replaceOne),
                button("editor.replaceAll", this::replaceAll));
        HBox.setHgrow(replacement, Priority.ALWAYS);
        search.getChildren().addAll(first, replaceRow);
        hide();
        query.setOnAction(e -> find(true));
        search.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) { hide(); area.requestFocus(); e.consume(); }
        });
    }

    void localize() { labels.forEach(this::localizeButton); }
    private void localizeButton(Button button, String key) {
        if (key.startsWith("editor.")) button.textProperty().bind(localization.get().text(key));
        String help = switch (key) {
            case "↑" -> "editor.command.findPrevious";
            case "↓" -> "editor.command.findNext";
            case "×" -> "help.closeSearch";
            default -> key;
        };
        io.github.dinamo541.idearm.app.ui.HoverHelp.install(button, localization.get(), help,
                key.equals("↑") ? "Shift+F3" : key.equals("↓") ? "F3" : key.equals("×") ? "Esc" : "");
    }
    VBox searchBar() { return search; }
    private Button button(String key, Runnable action) {
        var button = new Button(key.startsWith("editor.") ? "" : key);
        labels.put(button, key);
        if (!key.startsWith("editor.")) {
            button.setText("");
            button.setGraphic((key.equals("↑") ? io.github.dinamo541.idearm.app.ui.WorkbenchIcons.UP
                    : key.equals("↓") ? io.github.dinamo541.idearm.app.ui.WorkbenchIcons.DOWN
                    : io.github.dinamo541.idearm.app.ui.WorkbenchIcons.CLOSE).create());
        }
        localizeButton(button, key);
        button.setOnAction(e -> action.run());
        button.setFocusTraversable(false);
        return button;
    }
    boolean handle(KeyEvent event) {
        for (var command : EditorCommand.values()) {
            if (command.shortcut().match(event)) { execute(command); event.consume(); return true; }
        }
        if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.Z) {
            area.redo(); event.consume(); return true;
        }
        if (event.getCode() == KeyCode.TAB && !event.isControlDown() && !event.isAltDown()) {
            if (event.isShiftDown() || area.getSelectedText().contains("\n")) {
                execute(event.isShiftDown() ? EditorCommand.OUTDENT : EditorCommand.INDENT);
            } else area.replaceSelection("    ");
            event.consume(); return true;
        }
        return false;
    }

    void execute(EditorCommand command) {
        String text = area.getText();
        int anchor = area.getAnchor(), caret = area.getCaretPosition();
        switch (command) {
            case UNDO -> area.undo();
            case REDO -> area.redo();
            case PASTE -> area.paste();
            case CUT, COPY -> {
                if (area.getSelection().getLength() > 0) {
                    if (command == EditorCommand.CUT) area.cut(); else area.copy();
                } else {
                    var lines = LineEditing.lines(text, caret, caret);
                    var clipboard = new ClipboardContent();
                    clipboard.putString(text.substring(lines.start(), lines.end()) + "\n");
                    Clipboard.getSystemClipboard().setContent(clipboard);
                    if (command == EditorCommand.CUT) apply(LineEditing.delete(text, caret, caret));
                }
            }
            case SELECT_ALL -> area.selectAll();
            case SELECT_LINE -> {
                var lines = LineEditing.lines(text, anchor, caret);
                area.selectRange(lines.start(), Math.min(text.length(), lines.end() + 1));
            }
            case MOVE_UP, MOVE_DOWN -> apply(LineEditing.move(text, anchor, caret, command == EditorCommand.MOVE_DOWN));
            case COPY_UP, COPY_DOWN -> apply(LineEditing.duplicate(text, anchor, caret, command == EditorCommand.COPY_DOWN));
            case DELETE_LINE -> apply(LineEditing.delete(text, anchor, caret));
            case INSERT_ABOVE, INSERT_BELOW -> apply(LineEditing.insert(text, caret, command == EditorCommand.INSERT_ABOVE));
            case COMMENT, INDENT, OUTDENT -> apply(LineEditing.transform(text, anchor, caret,
                    command == EditorCommand.INDENT ? "indent" : command == EditorCommand.OUTDENT ? "outdent" : "comment"));
            case FIND, REPLACE -> show(command == EditorCommand.REPLACE);
            case FIND_NEXT -> find(true);
            case FIND_PREVIOUS -> find(false);
            case WRAP -> area.setWrapText(!area.isWrapText());
            case GO_TO_LINE -> {
                var dialog = new TextInputDialog(Integer.toString(area.getCurrentParagraph() + 1));
                dialog.initOwner(area.getScene().getWindow());
                dialog.setTitle(localization.get().get(command.key()));
                dialog.setHeaderText(localization.get().get("editor.linePrompt", area.getParagraphs().size()));
                dialog.showAndWait().ifPresent(value -> {
                    try {
                        int line = Integer.parseInt(value.trim());
                        area.moveTo(Math.clamp(line - 1, 0, area.getParagraphs().size() - 1), 0);
                        area.requestFollowCaret();
                    } catch (NumberFormatException ignored) { /* Invalid input leaves the caret untouched. */ }
                });
            }
        }
    }

    private void apply(LineEditing.Edit edit) {
        if (edit == null) return;
        area.getUndoManager().preventMerge();
        area.replaceText(edit.start(), edit.end(), edit.text());
        area.selectRange(edit.anchor(), edit.caret());
        area.getUndoManager().preventMerge();
        area.requestFollowCaret();
    }
    private void show(boolean replace) {
        query.promptTextProperty().bind(localization.get().text("editor.command.find"));
        replacement.promptTextProperty().bind(localization.get().text("editor.command.replace"));
        if (!area.getSelectedText().isEmpty() && !area.getSelectedText().contains("\n")) query.setText(area.getSelectedText());
        replaceRow.setVisible(replace); replaceRow.setManaged(replace);
        search.setVisible(true); search.setManaged(true);
        query.requestFocus(); query.selectAll();
    }
    private void hide() { search.setVisible(false); search.setManaged(false); }
    private int index(String text, String needle, int start, boolean forward) {
        for (int i = start; i >= 0 && i <= text.length() - needle.length(); i += forward ? 1 : -1) {
            if (text.regionMatches(true, i, needle, 0, needle.length())) return i;
        }
        return -1;
    }
    private void find(boolean forward) {
        String needle = query.getText(), text = area.getText();
        if (needle.isEmpty()) { show(false); return; }
        int start = forward ? area.getSelection().getEnd() : Math.min(text.length() - needle.length(), area.getSelection().getStart() - 1);
        int found = index(text, needle, start, forward);
        if (found < 0) found = index(text, needle, forward ? 0 : text.length() - needle.length(), forward);
        count.setText(found < 0 ? localization.get().get("editor.noMatches") : "");
        if (found >= 0) { area.selectRange(found, found + needle.length()); area.requestFollowCaret(); }
    }
    private void replaceOne() {
        if (!query.getText().isEmpty() && area.getSelectedText().equalsIgnoreCase(query.getText())) {
            area.replaceSelection(replacement.getText());
        }
        find(true);
    }
    private void replaceAll() {
        String needle = query.getText(), text = area.getText();
        if (needle.isEmpty()) return;
        var matches = new java.util.ArrayList<Integer>();
        int at = index(text, needle, 0, true);
        while (at >= 0) { matches.add(at); at = index(text, needle, at + needle.length(), true); }
        // One RichTextFX multi-change makes Replace All one undoable operation.
        if (!matches.isEmpty()) {
            var change = area.createMultiChange(matches.size());
            for (int position : matches) change.replaceText(position, position + needle.length(), replacement.getText());
            area.getUndoManager().preventMerge();
            change.commit();
            area.getUndoManager().preventMerge();
        }
    }
}
