package io.github.dinamo541.idearm.app.view;

import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

/**
 * Small line icons for the explorer, drawn here so the project ships no third-party icon set.
 *
 * <p>Toolbar glyphs take the theme's text colour; file icons use fixed colours that read on light and dark
 * backgrounds, like VS Code's default file icons.
 */
final class ExplorerIcons {

    private static final String PAGE = "M3 1h6.4L13 4.6V15H3z M4 2v12h8V5.2H9V2z";
    private static final String PLUS = "M12 9h1v2.5h2.5v1H13V15h-1v-2.5H9.5v-1H12z";
    private static final String NEW_PAGE = "M3 1h6.4L13 4.6V8h-1V5.2H9V2H4v12h4.5v1H3z " + PLUS;
    private static final String FOLDER = "M1 3h5.2l1.2 1.3H15V14H1z M2 4v9h12V5.3H7l-1.2-1.3z";
    private static final String FOLDER_OPEN = "M1 3h5.2l1.2 1.3H14v2h1.6L13.8 14H1z M2 4v7.4L3.2 6.3H13V5.3H7L5.8 4z"
            + " M4 7.3 2.4 13h10.6l1.4-5.7z";
    private static final String NEW_FOLDER = "M1 3h5.2l1.2 1.3H15V8h-1V5.3H7L5.8 4H2v9h6.5v1H1z " + PLUS;
    private static final String REFRESH = "M13.5 8A5.5 5.5 0 1 1 11.9 4.1L10 6h4.5V1.5l-1.9 1.9A6.5 6.5 0 1 0 14.5 8z";
    private static final String COLLAPSE = "M5 1h10v10h-1V2H5z M1 5h10v10H1z M2 6v8h8V6z M3.5 9.5h5v1h-5z";

    private ExplorerIcons() {
    }

    static Node newFile() {
        return glyph(NEW_PAGE);
    }

    static Node newFolder() {
        return glyph(NEW_FOLDER);
    }

    static Node refresh() {
        return glyph(REFRESH);
    }

    static Node collapseAll() {
        return glyph(COLLAPSE);
    }

    static Node folder(boolean open) {
        return colored(open ? FOLDER_OPEN : FOLDER, Color.web("#d7a64a"));
    }

    /** A file icon coloured by type, so sources, includes and programs are told apart at a glance. */
    static Node file(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        Color color;
        if (lower.endsWith(".asm")) {
            color = Color.web("#4f9fe8");
        } else if (lower.endsWith(".inc")) {
            color = Color.web("#a074c4");
        } else if (lower.endsWith(".exe") || lower.endsWith(".com")) {
            color = Color.web("#5fae57");
        } else if (lower.endsWith(".toml")) {
            color = Color.web("#e37933");
        } else if (lower.endsWith(".lst") || lower.endsWith(".map") || lower.endsWith(".obj")) {
            color = Color.web("#8a8f98");
        } else {
            color = Color.web("#9da5b4");
        }
        return colored(PAGE, color);
    }

    private static SVGPath glyph(String path) {
        SVGPath icon = shape(path);
        icon.getStyleClass().add("explorer-glyph");
        icon.setStyle("-fx-fill: -color-fg-muted;");
        return icon;
    }

    private static SVGPath colored(String path, Color color) {
        SVGPath icon = shape(path);
        icon.setFill(color);
        return icon;
    }

    private static SVGPath shape(String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.setFillRule(FillRule.EVEN_ODD);
        return icon;
    }
}
