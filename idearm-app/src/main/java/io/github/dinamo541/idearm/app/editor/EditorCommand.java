package io.github.dinamo541.idearm.app.editor;

import javafx.scene.input.KeyCombination;

/** Shared command metadata used by the keyboard, Edit menu and command palette. */
public enum EditorCommand {
    UNDO("undo", "Ctrl+Z"), REDO("redo", "Ctrl+Y"),
    CUT("cut", "Ctrl+X"), COPY("copy", "Ctrl+C"), PASTE("paste", "Ctrl+V"),
    SELECT_ALL("selectAll", "Ctrl+A"), SELECT_LINE("selectLine", "Ctrl+L"),
    MOVE_UP("moveUp", "Alt+Up"), MOVE_DOWN("moveDown", "Alt+Down"),
    COPY_UP("copyUp", "Shift+Alt+Up"), COPY_DOWN("copyDown", "Shift+Alt+Down"),
    DELETE_LINE("deleteLine", "Ctrl+Shift+K"),
    INSERT_BELOW("insertBelow", "Ctrl+Enter"), INSERT_ABOVE("insertAbove", "Ctrl+Shift+Enter"),
    COMMENT("comment", "Ctrl+Slash"), INDENT("indent", "Ctrl+Close Bracket"),
    OUTDENT("outdent", "Ctrl+Open Bracket"),
    FIND("find", "Ctrl+F"), REPLACE("replace", "Ctrl+H"),
    FIND_NEXT("findNext", "F3"), FIND_PREVIOUS("findPrevious", "Shift+F3"),
    GO_TO_LINE("goToLine", "Ctrl+G"), WRAP("wrap", "Alt+Z");

    private final String key;
    private final KeyCombination shortcut;
    EditorCommand(String key, String shortcut) {
        this.key = "editor.command." + key;
        this.shortcut = KeyCombination.valueOf(shortcut);
    }
    public String key() { return key; }
    public KeyCombination shortcut() { return shortcut; }
}
