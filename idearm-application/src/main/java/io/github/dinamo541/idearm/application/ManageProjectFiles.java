package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.files.EntryNames;
import io.github.dinamo541.idearm.domain.files.EntryNames.Check;
import io.github.dinamo541.idearm.domain.files.EntryNames.Kind;
import io.github.dinamo541.idearm.domain.port.ProjectFiles;
import io.github.dinamo541.idearm.domain.port.ProjectFiles.Deletion;
import io.github.dinamo541.idearm.domain.port.ProjectFiles.Entry;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The explorer's file operations: what a folder shows, and creating, renaming and deleting entries the way VS Code
 * does, with IDEARM's naming rules on top (lower-case extensions, DOS 8.3 warnings).
 *
 * <p>Every path must stay inside the project, and the project folder itself can be neither renamed nor deleted.
 */
public final class ManageProjectFiles {

    private static final Comparator<Entry> ORDER = Comparator
            .comparing((Entry entry) -> !entry.directory())
            .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT))
            .thenComparing(Entry::name);

    private final ProjectFiles files;

    public ManageProjectFiles(ProjectFiles files) {
        this.files = Objects.requireNonNull(files, "files");
    }

    /**
     * Every child of a folder, hidden and tool files included, so the explorer shows exactly what is on disk:
     * folders first, then names in alphabetical order.
     */
    public List<Entry> children(Path projectRoot, Path directory) {
        Path folder = inside(projectRoot, directory, true);
        return files.list(folder).stream()
                .sorted(ORDER)
                .toList();
    }

    /**
     * Checks a name while it is typed.
     *
     * @param renamed the entry being renamed, or {@code null} when creating
     */
    public Check check(Path projectRoot, Path parent, String typed, Kind kind, boolean dosTarget, Path renamed) {
        Kind effective = renamed == null ? EntryNames.requestedKind(typed, kind) : kind;
        Check check = renamed == null
                ? EntryNames.check(typed, effective, dosTarget)
                : EntryNames.checkRename(typed, effective, dosTarget);
        if (check.blocking()) {
            return check;
        }
        Path target = inside(projectRoot, parent, true).resolve(EntryNames.normalize(typed, effective)).normalize();
        if (!target.startsWith(root(projectRoot))) {
            return new Check(EntryNames.Severity.ERROR, "explorer.name.outside", List.of());
        }
        boolean sameEntry = renamed != null && files.isSameEntry(renamed, target);
        if (renamed != null && target.getFileName().toString().equals(renamed.getFileName().toString())) {
            return Check.OK;
        }
        if (files.exists(target) && !sameEntry) {
            return new Check(EntryNames.Severity.ERROR, "explorer.name.exists",
                    List.of(target.getFileName().toString()));
        }
        // Windows and the DOS tools see Main.asm and main.asm as one file, so a project made on Linux must not
        // hold both, or it would not build or open elsewhere.
        Entry sameName = sameNameIgnoringCase(target, renamed);
        if (sameName != null) {
            return new Check(EntryNames.Severity.ERROR, "explorer.name.exists", List.of(sameName.name()));
        }
        return check;
    }

    /** Another entry of the target's folder whose name differs only in letter case, or {@code null}. */
    private Entry sameNameIgnoringCase(Path target, Path renamed) {
        Path folder = target.getParent();
        if (folder == null || !files.isDirectory(folder)) {
            return null;
        }
        String name = target.getFileName().toString();
        Path self = renamed == null ? null : renamed.toAbsolutePath().normalize();
        return files.list(folder).stream()
                .filter(entry -> entry.name().equalsIgnoreCase(name))
                .filter(entry -> !entry.path().toAbsolutePath().normalize().equals(self))
                .findFirst()
                .orElse(null);
    }

    /** Creates a file, and the folders a nested name asks for; a trailing slash creates a folder instead. */
    public Path createFile(Path projectRoot, Path parent, String typed, boolean dosTarget) {
        Kind kind = EntryNames.requestedKind(typed, Kind.FILE);
        Path target = targetFor(projectRoot, parent, typed, kind, dosTarget, null);
        return kind == Kind.FOLDER ? files.createDirectory(target) : files.createFile(target);
    }

    public Path createFolder(Path projectRoot, Path parent, String typed, boolean dosTarget) {
        Path target = targetFor(projectRoot, parent, typed, Kind.FOLDER, dosTarget, null);
        return files.createDirectory(target);
    }

    /** Renames an entry in place; changing only the letter case is allowed. */
    public Path rename(Path projectRoot, Path entry, String typed, boolean dosTarget) {
        Path source = inside(projectRoot, entry, false);
        Kind kind = files.isDirectory(source) ? Kind.FOLDER : Kind.FILE;
        Path target = targetFor(projectRoot, source.getParent(), typed, kind, dosTarget, source);
        if (target.equals(source) && target.getFileName().toString().equals(source.getFileName().toString())) {
            return source;
        }
        return files.rename(source, target);
    }

    public boolean trashAvailable() {
        return files.trashAvailable();
    }

    public Deletion delete(Path projectRoot, Path entry, boolean permanently) {
        return files.delete(inside(projectRoot, entry, false), permanently);
    }

    /** Watches the whole project; the explorer refreshes when {@code onChange} runs. */
    public AutoCloseable watch(Path projectRoot, Runnable onChange) {
        return files.watch(root(projectRoot), onChange);
    }

    private Path targetFor(Path projectRoot, Path parent, String typed, Kind kind, boolean dosTarget, Path renamed) {
        Check check = check(projectRoot, parent, typed, kind, dosTarget, renamed);
        if (check.blocking()) {
            throw new DomainException(check.code(), "Invalid name '" + typed + "': " + check.code(),
                    check.arguments().toArray());
        }
        return inside(projectRoot, parent, true).resolve(EntryNames.normalize(typed, kind)).normalize();
    }

    /**
     * @param allowRoot whether the project folder itself is acceptable (it is a valid parent, never a target)
     */
    private static Path inside(Path projectRoot, Path path, boolean allowRoot) {
        Path root = root(projectRoot);
        Path candidate = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (!candidate.startsWith(root) || (!allowRoot && candidate.equals(root))) {
            throw new DomainException("explorer.path.outside", "Not inside the project: " + candidate, candidate);
        }
        return candidate;
    }

    private static Path root(Path projectRoot) {
        return Objects.requireNonNull(projectRoot, "projectRoot").toAbsolutePath().normalize();
    }
}
