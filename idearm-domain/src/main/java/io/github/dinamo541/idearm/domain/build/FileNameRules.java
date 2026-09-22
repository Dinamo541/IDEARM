package io.github.dinamo541.idearm.domain.build;

import io.github.dinamo541.idearm.domain.DomainException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Conservative DOS 8.3 names that also remain literal in generated tool commands. No renaming occurs. */
public final class FileNameRules {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_-]{0,7}(\\.[A-Za-z0-9_][A-Za-z0-9_-]{0,2})?");
    private static final Pattern DEVICE = Pattern.compile("CON|PRN|AUX|NUL|CLOCK\\$|COM[1-9]|LPT[1-9]");
    private FileNameRules() {}

    /** Whether one path segment is a name DOS tools can see as written (8.3, safe characters). */
    public static boolean isDosName(String segment) {
        if (segment == null || !NAME.matcher(segment).matches()) {
            return false;
        }
        String stem = segment.split("\\.", -1)[0].toUpperCase(Locale.ROOT);
        return !DEVICE.matcher(stem).matches();
    }

    public static void validateRelativePath(String path) {
        validateRelativePath(path, true);
    }

    /**
     * Checks a project-relative path.
     *
     * @param dosNames whether every segment must also be a DOS 8.3 name; native 32/64-bit tools accept any name
     */
    public static void validateRelativePath(String path, boolean dosNames) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains(":")) {
            throw new DomainException("path.relative.required", "Use a relative slash-separated path: " + path, path);
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new DomainException("path.relative.required", "Use a relative slash-separated path: " + path, path);
            }
            // Device names are reserved by Windows as well as DOS, whatever the target.
            String stem = segment.split("\\.", -1)[0].toUpperCase(Locale.ROOT);
            if (DEVICE.matcher(stem).matches()) {
                throw new DomainException("path.dos.reserved", "DOS device names cannot be used as paths: " + path, path);
            }
            if (!dosNames) {
                continue;
            }
            if (!NAME.matcher(segment).matches()) {
                throw new DomainException("path.dos.invalid", "Every source path segment must use a safe DOS 8.3 name: " + path, path);
            }
        }
    }

    /** Detects case aliases in any path segment, duplicate files, and file/directory conflicts. */
    public static void validateDistinctPaths(Collection<String> paths) {
        validateDistinctPaths(paths, true);
    }

    /**
     * Detects case aliases, duplicates and file/folder conflicts, which collide on Windows and in DOS alike.
     *
     * @param dosNames whether the paths must also be DOS 8.3 names
     */
    public static void validateDistinctPaths(Collection<String> paths, boolean dosNames) {
        Map<String, String> spellings = new HashMap<>();
        Set<String> files = new HashSet<>();
        Set<String> directories = new HashSet<>();
        for (String path : paths) {
            validateRelativePath(path, dosNames);
            String prefix = "";
            String[] segments = path.split("/");
            for (int i = 0; i < segments.length; i++) {
                prefix = prefix.isEmpty() ? segments[i] : prefix + "/" + segments[i];
                String folded = prefix.toUpperCase(Locale.ROOT);
                String earlier = spellings.putIfAbsent(folded, prefix);
                if (earlier != null && !earlier.equals(prefix)) collision(earlier, prefix);
                boolean leaf = i == segments.length - 1;
                if (leaf) {
                    if (directories.contains(folded) || !files.add(folded)) collision(prefix, path);
                } else {
                    if (files.contains(folded)) collision(prefix, path);
                    directories.add(folded);
                }
            }
        }
    }

    private static void collision(String first, String second) {
        throw new DomainException("path.collision", "Paths collide: " + first + " and " + second, first, second);
    }
}
