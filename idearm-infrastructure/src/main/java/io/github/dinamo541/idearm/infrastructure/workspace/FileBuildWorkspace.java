package io.github.dinamo541.idearm.infrastructure.workspace;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.port.BuildWorkspace;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Owns generated build/dist folders; unmarked directories are always treated as user data. */
public final class FileBuildWorkspace implements BuildWorkspace {
    public static final String MARKER_NAME = BuildWorkspace.GENERATED_MARKER;
    public static final String MARKER_CONTENT = BuildWorkspace.GENERATED_MARKER_CONTENT;

    /**
     * Locks the project for one operation, and lets that operation nest.
     *
     * <p>The file lock keeps other processes out. Inside one process it has to be re-entrant, because Run and
     * Package hold the project while they delegate to a build: a second {@code tryLock} on the same file would
     * otherwise report the caller's own lock as a busy project. Re-entry is granted to the owning thread only,
     * so two tasks still cannot build the same project at once.
     */
    @Override public WorkspaceLock lock(Path projectRoot) {
        try {
            Path root = SafePaths.root(projectRoot);
            synchronized (HELD_LOCKS) {
                HeldLock held = HELD_LOCKS.get(root);
                if (held != null) {
                    if (held.owner != Thread.currentThread()) {
                        throw new DomainException("project.busy", "Another operation owns this project.");
                    }
                    held.depth++;
                    return releaseOnce(root);
                }
                Path local = root.resolve(".idearm");
                SafePaths.inspectAncestors(root, local);
                Files.createDirectories(local);
                SafePaths.inspect(local);
                Path file = local.resolve("operation.lock");
                SafePaths.inspectAncestors(root, file);
                FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                FileLock lock;
                try { lock = channel.tryLock(); }
                catch (OverlappingFileLockException busy) { channel.close(); throw new DomainException("project.busy", "Another operation owns this project."); }
                if (lock == null) { channel.close(); throw new DomainException("project.busy", "Another operation owns this project."); }
                HELD_LOCKS.put(root, new HeldLock(Thread.currentThread(), channel, lock));
                return releaseOnce(root);
            }
        } catch (IOException failure) { throw error("project.lock.failed", failure); }
    }

    /** Each handle releases its own level once, so a duplicated close cannot free someone else's lock. */
    private static WorkspaceLock releaseOnce(Path root) {
        var released = new java.util.concurrent.atomic.AtomicBoolean(false);
        return () -> {
            if (!released.compareAndSet(false, true)) {
                return;
            }
            synchronized (HELD_LOCKS) {
                HeldLock held = HELD_LOCKS.get(root);
                if (held == null) {
                    return;
                }
                if (--held.depth > 0) {
                    return;
                }
                HELD_LOCKS.remove(root);
                try { held.lock.release(); }
                catch (IOException failure) {
                    throw new DomainException("project.lock.release", "Cannot release the project lock.", failure);
                }
                finally { try { held.channel.close(); } catch (IOException ignored) { } }
            }
        };
    }

    private static final Map<Path, HeldLock> HELD_LOCKS = new HashMap<>();

    private static final class HeldLock {
        private final Thread owner;
        private final FileChannel channel;
        private final FileLock lock;
        private int depth = 1;

        private HeldLock(Thread owner, FileChannel channel, FileLock lock) {
            this.owner = owner;
            this.channel = channel;
            this.lock = lock;
        }
    }

    @Override public void validateSources(Path projectRoot, Project project) {
        try {
            Path root = SafePaths.root(projectRoot);
            Path entry = SafePaths.relative(root, project.sources().entry());
            SafePaths.inspectAncestors(root, entry);
            if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) throw new DomainException("project.entry.missing", "The entry source does not exist: " + project.sources().entry(), project.sources().entry());
            for (String module : project.sources().modules()) {
                Path modulePath = SafePaths.relative(root, module);
                SafePaths.inspectAncestors(root, modulePath);
                if (!Files.isRegularFile(modulePath, LinkOption.NOFOLLOW_LINKS)) throw new DomainException("project.module.missing", "The module source does not exist: " + module, module);
            }
            for (String include : project.sources().include()) {
                Path directory = SafePaths.relative(root, include);
                SafePaths.inspectAncestors(root, directory);
                if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new DomainException("project.include.missing", "The include directory does not exist: " + include, include);
            }
        } catch (IOException failure) { throw error("project.source.unsafe", failure); }
    }

    @Override public void invalidate(Path projectRoot, String configuration) {
        try {
            Path root = SafePaths.root(projectRoot);
            Path build = ensureGenerated(root, "build");
            Path output = configuration(build, configuration);
            if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) deleteValidatedTree(output);
            Files.createDirectory(output);
        } catch (IOException failure) { throw error("workspace.invalidate.failed", failure); }
    }

    @Override public void publish(Path projectRoot, String configuration, Path stagedOutput, List<String> outputs) {
        try {
            Path root = SafePaths.root(projectRoot);
            Path build = ensureGenerated(root, "build");
            Path destination = configuration(build, configuration);
            Path staged = SafePaths.root(stagedOutput);
            List<Path> sources = new ArrayList<>();
            for (String output : outputs) {
                Path source = SafePaths.relative(staged, output);
                SafePaths.inspectAncestors(staged, source);
                if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw new IOException("workspace.output.missing: " + output);
                sources.add(source);
            }
            // Prepare the entire result first so a missing artifact cannot publish half a build.
            Path pending = Files.createTempDirectory(build, ".publish-");
            try {
                for (int i = 0; i < outputs.size(); i++) {
                    Path target = SafePaths.relative(pending, outputs.get(i));
                    Files.createDirectories(target.getParent());
                    SafePaths.inspectAncestors(staged, sources.get(i));
                    Files.copy(sources.get(i), target);
                }
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) deleteValidatedTree(destination);
                try { Files.move(pending, destination, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(pending, destination); }
            } finally {
                if (Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) deleteValidatedTree(pending);
            }
        } catch (IOException failure) { throw error("workspace.publish.failed", failure); }
    }

    @Override public void clean(Path projectRoot) {
        try {
            Path root = SafePaths.root(projectRoot);
            List<Path> owned = new ArrayList<>();
            // Validate all targets before removing anything, including markers and every descendant.
            for (String name : List.of("build", "dist")) {
                Path directory = root.resolve(name);
                if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) continue;
                verifyGenerated(root, directory);
                tree(directory);
                owned.add(directory);
            }
            for (Path directory : owned) {
                verifyGenerated(root, directory);
                deleteValidatedTree(directory);
            }
        } catch (IOException failure) { throw error("workspace.clean.refused", failure); }
    }

    private static Path ensureGenerated(Path root, String name) throws IOException {
        Path directory = root.resolve(name);
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) verifyGenerated(root, directory);
        else {
            Files.createDirectory(directory);
            Files.writeString(directory.resolve(MARKER_NAME), MARKER_CONTENT, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
        return directory;
    }

    private static void verifyGenerated(Path root, Path directory) throws IOException {
        if (!directory.getParent().equals(root)
                || !(directory.getFileName().toString().equals("build") || directory.getFileName().toString().equals("dist"))) {
            throw new IOException("workspace.generated.invalidBoundary: " + directory);
        }
        SafePaths.inspectAncestors(root, directory);
        Path marker = directory.resolve(MARKER_NAME);
        SafePaths.inspect(marker);
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                || Files.size(marker) != MARKER_CONTENT.getBytes(StandardCharsets.UTF_8).length
                || !Files.readString(marker, StandardCharsets.UTF_8).equals(MARKER_CONTENT)) {
            throw new IOException("workspace.generated.invalidMarker: " + directory);
        }
    }

    private static Path configuration(Path build, String configuration) throws IOException {
        if (configuration == null || !configuration.matches("[A-Za-z][A-Za-z0-9_-]*")) throw new IOException("workspace.configuration.invalid");
        Path path = build.resolve(configuration);
        SafePaths.inspectAncestors(build, path);
        return path;
    }

    private static List<Path> tree(Path directory) throws IOException {
        SafePaths.inspect(directory);
        try (var paths = Files.walk(directory)) {
            List<Path> result = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path path : result) {
                if (!path.startsWith(directory)) throw new IOException("workspace.path.outsideBoundary: " + path);
                SafePaths.inspectAncestors(directory, path);
            }
            return result;
        }
    }

    private static void deleteValidatedTree(Path directory) throws IOException {
        List<Path> paths = tree(directory);
        for (Path path : paths) {
            SafePaths.inspectAncestors(directory, path);
            Files.delete(path);
        }
    }

    private static DomainException error(String code, IOException cause) {
        return new DomainException(code, cause.getMessage(), cause, cause.getMessage());
    }
}
