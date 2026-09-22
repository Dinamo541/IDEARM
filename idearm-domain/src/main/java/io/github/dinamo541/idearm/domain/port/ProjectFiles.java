package io.github.dinamo541.idearm.domain.port;

import java.nio.file.Path;
import java.util.List;

/**
 * The project tree as the explorer sees it: listing, creating, renaming and deleting entries, and noticing when
 * something changes on disk.
 *
 * <p>Implementations never follow links out of a folder and never overwrite an existing entry; validation of
 * names and project boundaries happens in the application layer before a call reaches this port.
 */
public interface ProjectFiles {

    /** One entry of a folder. */
    record Entry(Path path, boolean directory) {
        public String name() {
            Path name = path.getFileName();
            return name == null ? path.toString() : name.toString();
        }
    }

    /** What a deletion did, so the user can be told whether it can be undone. */
    enum Deletion { MOVED_TO_TRASH, DELETED_PERMANENTLY }

    /** The direct children of a folder, unsorted. */
    List<Entry> list(Path directory);

    boolean exists(Path path);

    boolean isDirectory(Path path);

    /** Whether two paths name the same entry, which is how a case-only rename is recognised. */
    boolean isSameEntry(Path first, Path second);

    /** Creates an empty file, and any missing parent folder; fails if the file already exists. */
    Path createFile(Path file);

    /** Creates a folder and any missing parent; fails if it already exists. */
    Path createDirectory(Path directory);

    /** Renames or moves an entry; a change of letter case only is supported. */
    Path rename(Path source, Path target);

    /** Whether deletions can go to the system trash on this machine. */
    boolean trashAvailable();

    /**
     * Deletes an entry and everything under it.
     *
     * @param permanently {@code false} to move it to the trash, which fails instead of silently destroying it
     */
    Deletion delete(Path target, boolean permanently);

    /**
     * Calls {@code onChange} after files change anywhere under {@code root}, coalescing bursts such as a build.
     * Closing the returned handle stops watching.
     */
    AutoCloseable watch(Path root, Runnable onChange);
}
