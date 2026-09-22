package io.github.dinamo541.idearm.infrastructure.workspace;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Reports changes anywhere under a project folder, coalescing bursts such as a build into one notification.
 *
 * <p>On Windows a single recursive watch on the project folder is used. Watching each subfolder separately would
 * hold a handle on it, and a folder deleted while watched stays "pending delete" until the handle closes: the
 * build, which deletes and immediately recreates {@code build/debug}, would then fail. Other systems do not have
 * that problem and get one watch per folder.
 */
final class ProjectTreeWatcher implements AutoCloseable {

    /** Changes closer together than this are reported once. */
    private static final long QUIET_MILLIS = 250;
    private static final int MAX_FOLDERS = 4096;
    /**
     * Git's object store changes on every commit and fetch and holds nothing a person browses; everything else,
     * hidden folders included, is shown by the explorer and therefore watched.
     */
    private static final Path IGNORED = Path.of(".git", "objects");

    private final Path root;
    private final Runnable onChange;
    private final WatchService service;
    private final boolean recursive;
    private final Map<WatchKey, Path> folders = new ConcurrentHashMap<>();
    private final Thread thread;
    private volatile boolean closed;

    ProjectTreeWatcher(Path root, Runnable onChange) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.onChange = onChange;
        this.service = FileSystems.getDefault().newWatchService();
        this.recursive = registerRecursively();
        if (!recursive) {
            registerTree(this.root);
        }
        this.thread = Thread.ofPlatform().daemon().name("idearm-project-watcher").start(this::loop);
    }

    @Override
    public void close() {
        closed = true;
        thread.interrupt();
        try {
            service.close();
        } catch (IOException ignored) {
            // The watcher is gone either way.
        }
    }

    private boolean registerRecursively() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows");
        if (!windows) {
            return false;
        }
        try {
            WatchKey key = root.register(service, kinds(), com.sun.nio.file.ExtendedWatchEventModifier.FILE_TREE);
            folders.put(key, root);
            return true;
        } catch (IOException | UnsupportedOperationException notSupported) {
            return false;
        }
    }

    private void registerTree(Path folder) {
        try (var tree = Files.walk(folder)) {
            for (Path candidate : (Iterable<Path>) tree::iterator) {
                if (folders.size() >= MAX_FOLDERS) {
                    return;
                }
                if (Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS) && !ignored(candidate)) {
                    folders.put(candidate.register(service, kinds()), candidate);
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            // A folder that vanished while it was being registered simply is not watched.
        }
    }

    private void loop() {
        try {
            while (!closed) {
                boolean changed = drain(service.take());
                WatchKey next;
                while ((next = service.poll(QUIET_MILLIS, TimeUnit.MILLISECONDS)) != null) {
                    changed |= drain(next);
                }
                if (changed && !closed) {
                    notifyListener();
                }
            }
        } catch (InterruptedException | ClosedWatchServiceException stopped) {
            // close() ends the loop.
        }
    }

    private boolean drain(WatchKey key) {
        Path folder = folders.get(key);
        boolean changed = false;
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW || folder == null) {
                changed = true;
                continue;
            }
            Path child = folder.resolve((Path) event.context());
            if (ignored(child)) {
                continue;
            }
            changed = true;
            if (!recursive && event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                    && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                registerTree(child);
            }
        }
        if (!key.reset()) {
            folders.remove(key);
        }
        return changed;
    }

    private boolean ignored(Path path) {
        if (!path.startsWith(root)) {
            return true;
        }
        return root.relativize(path).startsWith(IGNORED);
    }

    private void notifyListener() {
        try {
            onChange.run();
        } catch (RuntimeException listenerFailure) {
            // A failing listener must not stop the watcher.
        }
    }

    @SuppressWarnings("unchecked")
    private static WatchEvent.Kind<Path>[] kinds() {
        return new WatchEvent.Kind[] {
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
        };
    }
}
