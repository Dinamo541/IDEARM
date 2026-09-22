package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.execution.ExecutionSession;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;
import io.github.dinamo541.idearm.domain.port.ProcessLauncher;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A native program run: it reports how the program ended and removes the staging copy afterwards.
 *
 * <p>On Windows the program has a console window of its own. Elsewhere the launcher keeps its pipes, and this
 * session passes the program's output to the IDE and the IDE's keyboard to the program ({@link #interactive()}).
 */
public final class HostExecutionSession implements ExecutionSession {

    private final ProcessLauncher.LaunchedProcess process;
    private final Path session;
    private final long startNanos;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean released = new AtomicBoolean(false);
    private final CompletableFuture<ExitInfo> exit = new CompletableFuture<>();
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    /** Output written before anyone listened, delivered to the first listener. */
    private final StringBuilder early = new StringBuilder();
    private final Thread pump;

    public HostExecutionSession(ProcessLauncher.LaunchedProcess process, Path session, long startNanos) {
        this.process = process;
        this.session = session;
        this.startNanos = startNanos;
        this.pump = process.output()
                .map(stream -> Thread.ofVirtual().name("idearm-program-output").start(() -> pump(stream)))
                .orElse(null);

        process.onExit().thenAccept(code -> {
            if (stopped.get()) {
                return;
            }
            // Everything the program printed is delivered before its end is reported.
            awaitPump();
            Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
            int status = recordedExitCode().orElse(code);
            exit.complete(new ExitInfo(status, duration, false, "Program terminated with exit code " + status));
        });
    }

    @Override
    public boolean interactive() {
        return pump != null;
    }

    @Override
    public void onOutput(Consumer<String> listener) {
        if (listener == null) {
            return;
        }
        // Under the same lock as deliver(), so the early text reaches the listener before anything newer.
        synchronized (early) {
            listeners.add(listener);
            if (!early.isEmpty()) {
                listener.accept(early.toString());
                early.setLength(0);
            }
        }
    }

    @Override
    public void sendInput(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        process.input().ifPresent(input -> {
            try {
                input.write(text.getBytes(StandardCharsets.UTF_8));
                input.flush();
            } catch (IOException programGone) {
                // The program ended or closed its input; there is nobody to type to.
            }
        });
    }

    private void pump(InputStream stream) {
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[1024];
            for (int read; (read = reader.read(buffer)) != -1; ) {
                deliver(new String(buffer, 0, read));
            }
        } catch (IOException closed) {
            // The pipe closes when the program ends or is stopped.
        }
    }

    private void deliver(String text) {
        synchronized (early) {
            if (listeners.isEmpty()) {
                early.append(text);
                return;
            }
            for (Consumer<String> listener : listeners) {
                try {
                    listener.accept(text);
                } catch (RuntimeException listenerFailure) {
                    // One failing listener must not stop the program's output.
                }
            }
        }
    }

    private void awaitPump() {
        if (pump == null) {
            return;
        }
        try {
            pump.join(Duration.ofSeconds(2));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public SessionState state() {
        if (stopped.get()) {
            return SessionState.STOPPED;
        }
        return process.alive() ? SessionState.RUNNING : SessionState.EXITED;
    }

    @Override
    public CompletableFuture<ExitInfo> exit() {
        return exit;
    }

    @Override
    public Path stagingDirectory() {
        return session;
    }

    @Override
    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            process.stop();
            exit.complete(ExitInfo.stopped(Duration.ofNanos(System.nanoTime() - startNanos)));
        }
    }

    /** Ends the run: a program still running is stopped, and the staging copy is removed. */
    @Override
    public void close() {
        if (process.alive()) {
            stop();
        } else {
            process.close();
        }
        if (released.compareAndSet(false, true)) {
            deleteSession(session);
        }
    }

    /** The code the generated batch recorded; a program started directly has none. */
    private java.util.OptionalInt recordedExitCode() {
        if (session == null) {
            return java.util.OptionalInt.empty();
        }
        Path sentinel = session.resolve(HostExecutionEnvironmentProvider.EXIT_SENTINEL);
        try {
            if (Files.isRegularFile(sentinel, LinkOption.NOFOLLOW_LINKS)) {
                return java.util.OptionalInt.of(Integer.parseInt(Files.readString(sentinel).strip()));
            }
        } catch (IOException | NumberFormatException unreadable) {
            // Fall back to the exit code of the process itself.
        }
        return java.util.OptionalInt.empty();
    }

    /** Deletes a run folder this provider created, identified by its marker. */
    static void deleteSession(Path session) {
        if (session == null
                || !Files.isRegularFile(session.resolve(HostExecutionEnvironmentProvider.MARKER), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var tree = Files.walk(session)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Windows may still hold a handle; the next run leaves it harmless in the staging area.
                }
            }
        } catch (IOException ignored) {
            // Nothing to clean.
        }
    }
}
