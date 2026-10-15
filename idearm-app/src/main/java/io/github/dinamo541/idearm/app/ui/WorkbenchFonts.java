package io.github.dinamo541.idearm.app.ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javafx.scene.text.Font;

/**
 * The typefaces bundled with the workbench, so the interface and the code look the same on every desktop.
 *
 * <p>Neither Segoe UI nor Consolas exists on Linux, where the editor fell back to a proportional font and
 * misaligned every column of Assembly. Inter sets the interface and JetBrains Mono sets code; both are under the
 * SIL Open Font License 1.1 (see the license files beside the fonts).
 *
 * <p>JavaFX selects a face only as regular or bold within a family, so the medium and semibold weights are
 * registered as their own families: style sheets ask for {@link #UI_MEDIUM} or {@link #UI_SEMIBOLD} by name.
 */
public final class WorkbenchFonts {
    /** Interface text: menus, labels, tabs and dialogs. */
    public static final String UI = "Inter";
    /** Emphasis that stays lighter than bold: active tabs and section titles. */
    public static final String UI_MEDIUM = "Inter Medium";
    /** Headings and titles. */
    public static final String UI_SEMIBOLD = "Inter SemiBold";
    /** Source code, registers, memory and console text. */
    public static final String MONO = "JetBrains Mono";

    private static final String FOLDER = "/io/github/dinamo541/idearm/app/fonts/";
    private static final List<String> FILES = List.of(
            "Inter-Regular.ttf", "Inter-Medium.ttf", "Inter-SemiBold.ttf", "Inter-Bold.ttf",
            "JetBrainsMono-Regular.ttf", "JetBrainsMono-Bold.ttf", "JetBrainsMono-Italic.ttf");
    private static boolean loaded;

    private WorkbenchFonts() { }

    /** Registers every bundled face once; call it before the first scene applies its style sheets. */
    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        for (String file : FILES) {
            try (InputStream in = WorkbenchFonts.class.getResourceAsStream(FOLDER + file)) {
                // A missing face only costs its look: JavaFX falls back to the system font for that family.
                if (in == null || Font.loadFont(in, 13) == null) {
                    System.err.println("IDEARM could not load the bundled font " + file);
                }
            } catch (IOException failure) {
                System.err.println("IDEARM could not load the bundled font " + file + ": " + failure.getMessage());
            }
        }
    }

    /** The bundled font files, for packaging checks. */
    public static List<String> files() {
        return FILES;
    }
}
