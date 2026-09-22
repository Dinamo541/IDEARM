package io.github.dinamo541.idearm.domain.files;

import io.github.dinamo541.idearm.domain.build.FileNameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checks and normalizes what the user types when creating or renaming an entry in the explorer.
 *
 * <p>It behaves like VS Code: a slash in a new name creates the intermediate folders ({@code lib/io.asm}), a
 * trailing slash asks for a folder, and problems are reported while the user types. On top of that, extensions
 * are always stored in lower case, and in a DOS project a name the DOS tools cannot see is flagged before the
 * build fails on it.
 */
public final class EntryNames {

    /** What the typed text creates. */
    public enum Kind { FILE, FOLDER }

    /** How serious a finding is: an error blocks the operation, the others only inform. */
    public enum Severity { OK, INFO, WARNING, ERROR }

    /** A finding as a message key and its arguments; the presentation layer translates it. */
    public record Check(Severity severity, String code, List<Object> arguments) {
        public static final Check OK = new Check(Severity.OK, "", List.of());

        public Check {
            arguments = List.copyOf(arguments);
        }

        static Check of(Severity severity, String code, Object... arguments) {
            return new Check(severity, code, List.of(arguments));
        }

        public boolean blocking() {
            return severity == Severity.ERROR;
        }
    }

    private static final Pattern DEVICE = Pattern.compile("CON|PRN|AUX|NUL|CLOCK\\$|COM[0-9]|LPT[0-9]");
    private static final String FORBIDDEN = "<>:\"|?*";
    private static final int MAX_SEGMENT = 255;

    private EntryNames() {
    }

    /**
     * Checks a typed name without touching the file system.
     *
     * @param dosTarget whether the project builds for DOS, whose tools only see 8.3 names
     */
    public static Check check(String typed, Kind kind, boolean dosTarget) {
        if (typed == null || typed.isBlank()) {
            return Check.of(Severity.ERROR, "explorer.name.empty");
        }
        if (typed.startsWith("/") || typed.startsWith("\\") || typed.matches("^[A-Za-z]:.*")) {
            return Check.of(Severity.ERROR, "explorer.name.absolute");
        }
        List<String> segments = segments(typed);
        if (segments.isEmpty()) {
            return Check.of(Severity.ERROR, "explorer.name.empty");
        }
        for (String segment : segments) {
            Check problem = checkSegment(segment);
            if (problem.blocking()) {
                return problem;
            }
        }
        if (dosTarget) {
            for (String segment : segments) {
                if (!FileNameRules.isDosName(segment)) {
                    return Check.of(Severity.WARNING, "explorer.name.notDos", segment);
                }
            }
        }
        String normalized = normalize(typed, kind);
        String asTyped = String.join("/", segments);
        if (!normalized.equals(asTyped)) {
            return Check.of(Severity.INFO, "explorer.name.lowerCaseExtension", FileNames.lastSegment(normalized));
        }
        return Check.OK;
    }

    /**
     * The relative path to create: slash-separated, without empty segments, and with a lower-case extension on
     * the final file name. Folder names are kept exactly as typed.
     */
    public static String normalize(String typed, Kind kind) {
        List<String> segments = new ArrayList<>(segments(typed));
        if (segments.isEmpty()) {
            return "";
        }
        if (kind == Kind.FILE) {
            int last = segments.size() - 1;
            segments.set(last, FileNames.withLowerCaseExtension(segments.get(last)));
        }
        return String.join("/", segments);
    }

    /** A typed name ending in a separator asks for a folder, as in VS Code. */
    public static Kind requestedKind(String typed, Kind requested) {
        if (typed != null && (typed.endsWith("/") || typed.endsWith("\\"))) {
            return Kind.FOLDER;
        }
        return requested;
    }

    /** A rename changes one name in place; moving an entry is a different operation. */
    public static Check checkRename(String typed, Kind kind, boolean dosTarget) {
        if (typed != null && (typed.contains("/") || typed.contains("\\"))) {
            return Check.of(Severity.ERROR, "explorer.name.separator");
        }
        return check(typed, kind, dosTarget);
    }

    private static List<String> segments(String typed) {
        var segments = new ArrayList<String>();
        String[] parts = typed.replace('\\', '/').split("/", -1);
        for (int i = 0; i < parts.length; i++) {
            boolean trailing = i == parts.length - 1;
            if (parts[i].isEmpty() && trailing) {
                continue;
            }
            segments.add(parts[i]);
        }
        return segments;
    }

    private static Check checkSegment(String segment) {
        if (segment.isEmpty()) {
            return Check.of(Severity.ERROR, "explorer.name.emptySegment");
        }
        if (!segment.equals(segment.strip())) {
            return Check.of(Severity.ERROR, "explorer.name.whitespace");
        }
        if (segment.equals(".") || segment.equals("..")) {
            return Check.of(Severity.ERROR, "explorer.name.relative");
        }
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c < 32 || FORBIDDEN.indexOf(c) >= 0) {
                return Check.of(Severity.ERROR, "explorer.name.invalidCharacter", c < 32 ? "?" : String.valueOf(c));
            }
        }
        if (segment.endsWith(".")) {
            return Check.of(Severity.ERROR, "explorer.name.trailingDot");
        }
        String device = segment.contains(".") ? segment.substring(0, segment.indexOf('.')) : segment;
        if (DEVICE.matcher(device.toUpperCase(Locale.ROOT)).matches()) {
            return Check.of(Severity.ERROR, "explorer.name.reserved", segment);
        }
        if (segment.length() > MAX_SEGMENT) {
            return Check.of(Severity.ERROR, "explorer.name.tooLong");
        }
        return Check.OK;
    }
}
