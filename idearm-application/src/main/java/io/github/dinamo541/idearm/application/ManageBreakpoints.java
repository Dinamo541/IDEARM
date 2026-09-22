package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.port.BreakpointStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Use case managing editor and debugger breakpoints for a project.
 */
public final class ManageBreakpoints {

    private final BreakpointStore store;

    public ManageBreakpoints(BreakpointStore store) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
    }

    public List<Breakpoint> getBreakpoints(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        return store.loadBreakpoints(projectRoot);
    }

    public List<Breakpoint> getBreakpointsForFile(Path projectRoot, String relativePath) {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        Objects.requireNonNull(relativePath, "relativePath cannot be null");
        final String normalized = normalizePath(relativePath);
        return store.loadBreakpoints(projectRoot).stream()
                .filter(bp -> bp.path().equalsIgnoreCase(normalized))
                .toList();
    }

    /**
     * Toggles a breakpoint on a file line: removes it if present, adds it if absent.
     *
     * @return the added breakpoint, or empty if it was removed
     */
    public Optional<Breakpoint> toggleBreakpoint(Path projectRoot, String relativePath, int line) {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        Objects.requireNonNull(relativePath, "relativePath cannot be null");
        var list = new ArrayList<>(store.loadBreakpoints(projectRoot));
        final String normalized = normalizePath(relativePath);

        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            Breakpoint bp = list.get(i);
            if (bp.path().equalsIgnoreCase(normalized) && bp.line() == line) {
                index = i;
                break;
            }
        }

        if (index >= 0) {
            list.remove(index);
            store.saveBreakpoints(projectRoot, List.copyOf(list));
            return Optional.empty();
        } else {
            Breakpoint created = new Breakpoint(normalized, line, true);
            list.add(created);
            store.saveBreakpoints(projectRoot, List.copyOf(list));
            return Optional.of(created);
        }
    }

    public void setBreakpointEnabled(Path projectRoot, String relativePath, int line, boolean enabled) {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        Objects.requireNonNull(relativePath, "relativePath cannot be null");
        var list = new ArrayList<>(store.loadBreakpoints(projectRoot));
        final String normalized = normalizePath(relativePath);

        for (int i = 0; i < list.size(); i++) {
            Breakpoint bp = list.get(i);
            if (bp.path().equalsIgnoreCase(normalized) && bp.line() == line) {
                list.set(i, bp.withEnabled(enabled));
                store.saveBreakpoints(projectRoot, List.copyOf(list));
                return;
            }
        }
    }

    public void clearBreakpoints(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        store.saveBreakpoints(projectRoot, List.of());
    }

    /**
     * Keeps breakpoints attached to their lines when a file, or a folder above it, is renamed in the explorer.
     */
    public void movePath(Path projectRoot, String fromRelative, String toRelative) {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        String from = normalizePath(fromRelative);
        String to = normalizePath(toRelative);
        boolean changed = false;
        var list = new ArrayList<Breakpoint>();
        for (Breakpoint bp : store.loadBreakpoints(projectRoot)) {
            String moved = relocate(bp.path(), from, to);
            changed |= !moved.equals(bp.path());
            list.add(moved.equals(bp.path()) ? bp : new Breakpoint(moved, bp.line(), bp.enabled()));
        }
        if (changed) {
            store.saveBreakpoints(projectRoot, List.copyOf(list));
        }
    }

    /** Drops the breakpoints of a deleted file, or of every file under a deleted folder. */
    public void removePath(Path projectRoot, String relative) {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        String removed = normalizePath(relative);
        var list = store.loadBreakpoints(projectRoot);
        var kept = list.stream().filter(bp -> !within(bp.path(), removed)).toList();
        if (kept.size() != list.size()) {
            store.saveBreakpoints(projectRoot, kept);
        }
    }

    private static String relocate(String path, String from, String to) {
        if (path.equalsIgnoreCase(from)) {
            return to;
        }
        if (within(path, from)) {
            return to + path.substring(from.length());
        }
        return path;
    }

    private static boolean within(String path, String folderOrFile) {
        return path.equalsIgnoreCase(folderOrFile)
                || path.regionMatches(true, 0, folderOrFile + "/", 0, folderOrFile.length() + 1);
    }

    private static String normalizePath(String path) {
        String stripped = path.replace('\\', '/').strip();
        return stripped.startsWith("/") ? stripped.substring(1) : stripped;
    }
}
