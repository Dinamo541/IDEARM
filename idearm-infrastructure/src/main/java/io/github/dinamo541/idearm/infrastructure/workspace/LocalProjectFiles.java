package io.github.dinamo541.idearm.infrastructure.workspace;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.port.ProjectFiles;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The explorer's view of the local file system.
 *
 * <p>Nothing here follows a link out of the project, nothing overwrites an existing entry, and a deletion goes
 * to the Recycle Bin unless the caller explicitly asked for a permanent one.
 */
public final class LocalProjectFiles implements ProjectFiles {

    @Override
    public List<Entry> list(Path directory) {
        var entries = new ArrayList<Entry>();
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                // A link to a folder is shown as a folder, as VS Code does; operations still never follow it.
                entries.add(new Entry(child, Files.isDirectory(child)));
            }
        } catch (NoSuchFileException gone) {
            return List.of();
        } catch (IOException failure) {
            throw failure("explorer.list.failed", directory, failure);
        }
        return entries;
    }

    @Override
    public boolean exists(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public boolean isDirectory(Path path) {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public boolean isSameEntry(Path first, Path second) {
        if (!exists(first) || !exists(second)) {
            return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize());
        }
        try {
            return Files.isSameFile(first, second);
        } catch (IOException unreadable) {
            return false;
        }
    }

    @Override
    public Path createFile(Path file) {
        try {
            createParents(file);
            return Files.createFile(file);
        } catch (FileAlreadyExistsException exists) {
            throw new DomainException("explorer.name.exists", "Already exists: " + file, file.getFileName());
        } catch (IOException failure) {
            throw failure("explorer.create.failed", file, failure);
        }
    }

    @Override
    public Path createDirectory(Path directory) {
        if (exists(directory)) {
            throw new DomainException("explorer.name.exists", "Already exists: " + directory, directory.getFileName());
        }
        try {
            createParents(directory);
            return Files.createDirectory(directory);
        } catch (FileAlreadyExistsException exists) {
            throw new DomainException("explorer.name.exists", "Already exists: " + directory, directory.getFileName());
        } catch (IOException failure) {
            throw failure("explorer.create.failed", directory, failure);
        }
    }

    @Override
    public Path rename(Path source, Path target) {
        if (!exists(source)) {
            throw new DomainException("explorer.entry.missing", "No longer exists: " + source, source.getFileName());
        }
        boolean caseOnly = exists(target) && isSameEntry(source, target);
        if (exists(target) && !caseOnly) {
            throw new DomainException("explorer.name.exists", "Already exists: " + target, target.getFileName());
        }
        try {
            if (caseOnly) {
                // Windows treats Main.asm and main.asm as the same file, and Files.move would do nothing.
                Path temporary = source.resolveSibling(".idearm-rename-" + UUID.randomUUID());
                Files.move(source, temporary);
                try {
                    return Files.move(temporary, target);
                } catch (IOException failure) {
                    Files.move(temporary, source);
                    throw failure;
                }
            }
            createParents(target);
            return Files.move(source, target);
        } catch (FileAlreadyExistsException exists) {
            throw new DomainException("explorer.name.exists", "Already exists: " + target, target.getFileName());
        } catch (IOException failure) {
            throw failure("explorer.rename.failed", source, failure);
        }
    }

    @Override
    public boolean trashAvailable() {
        return WindowsRecycleBin.available();
    }

    @Override
    public Deletion delete(Path target, boolean permanently) {
        if (!exists(target)) {
            throw new DomainException("explorer.entry.missing", "No longer exists: " + target, target.getFileName());
        }
        try {
            if (!permanently) {
                if (!trashAvailable()) {
                    throw new DomainException("explorer.trash.unavailable", "No recycle bin on this system.");
                }
                WindowsRecycleBin.moveToTrash(target);
                return Deletion.MOVED_TO_TRASH;
            }
            // Files.walk does not follow links, so a link is removed without touching what it points to.
            try (var tree = Files.walk(target)) {
                for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
            return Deletion.DELETED_PERMANENTLY;
        } catch (IOException failure) {
            String code = permanently ? "explorer.delete.failed" : "explorer.trash.failed";
            throw failure(code, target, failure);
        }
    }

    @Override
    public AutoCloseable watch(Path root, Runnable onChange) {
        try {
            return new ProjectTreeWatcher(root, onChange);
        } catch (IOException failure) {
            throw failure("explorer.watch.failed", root, failure);
        }
    }

    /** Creates missing parent folders, refusing a parent that is a file or a link. */
    private static void createParents(Path entry) throws IOException {
        Path parent = entry.getParent();
        if (parent == null) {
            return;
        }
        for (Path current = parent; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("path.link.disallowed: " + current);
            }
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("A file is in the way of the folder " + current);
            }
            if (Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                break;
            }
        }
        Files.createDirectories(parent);
    }

    private static DomainException failure(String code, Path path, IOException cause) {
        String reason = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return new DomainException(code, path.getFileName() + ": " + reason, cause, path.getFileName(), reason);
    }
}
