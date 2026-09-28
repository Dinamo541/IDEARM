package io.github.dinamo541.idearm.app.view;

import io.github.dinamo541.idearm.app.ui.WorkbenchIcons;
import javafx.scene.Node;

/** Explorer icons share the workbench's 16 px grid, line weight and theme tokens. */
final class ExplorerIcons {
    private ExplorerIcons() { }

    static Node newFile() { return WorkbenchIcons.NEW_FILE.create(); }
    static Node newFolder() { return WorkbenchIcons.NEW_FOLDER.create(); }
    static Node refresh() { return WorkbenchIcons.RESTART.create(); }
    static Node collapseAll() { return WorkbenchIcons.COLLAPSE.create(); }

    static Node folder(boolean open) {
        var icon = (open ? WorkbenchIcons.FOLDER_OPEN : WorkbenchIcons.FOLDER).create();
        icon.getStyleClass().add("file-icon-folder");
        return icon;
    }

    /** Color reinforces the shape, without depending on the host's symbol fonts. */
    static Node file(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        var symbol = lower.endsWith(".asm") ? WorkbenchIcons.ASSEMBLY
                : lower.endsWith(".inc") ? WorkbenchIcons.INCLUDE
                : lower.endsWith(".toml") ? WorkbenchIcons.CONFIG
                : lower.endsWith(".exe") || lower.endsWith(".com") ? WorkbenchIcons.CHIP
                : WorkbenchIcons.FILE;
        var icon = symbol.create();
        icon.getStyleClass().add("file-icon-" + symbol.name().toLowerCase(java.util.Locale.ROOT));
        return icon;
    }
}
