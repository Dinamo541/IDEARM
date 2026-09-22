package io.github.dinamo541.idearm.domain.files;

import java.util.Locale;

/**
 * The naming convention for every file the IDE writes into a project.
 *
 * <p>Names keep the spelling the user chose, and extensions are always lower case ({@code Main.ASM} becomes
 * {@code Main.asm}). DOS does not care: it upper-cases names itself inside the emulator, which is why staging
 * copies there stay upper case while nothing upper case reaches the project.
 */
public final class FileNames {

    private FileNames() {
    }

    /** The name with its extension in lower case; a leading dot (".gitignore") is part of the name. */
    public static String withLowerCaseExtension(String fileName) {
        int dot = extensionDot(fileName);
        if (dot < 0) {
            return fileName;
        }
        return fileName.substring(0, dot) + fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    /** The name without its extension, spelled as written. */
    public static String stem(String fileName) {
        int dot = extensionDot(fileName);
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    /** The extension without its dot, in lower case, or an empty string. */
    public static String extension(String fileName) {
        int dot = extensionDot(fileName);
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** The last segment of a slash- or backslash-separated path. */
    public static String lastSegment(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private static int extensionDot(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 || dot == fileName.length() - 1 ? -1 : dot;
    }
}
