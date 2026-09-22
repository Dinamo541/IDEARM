package io.github.dinamo541.idearm.toolchain.dos.execution;

import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;
import io.github.dinamo541.idearm.domain.port.DebugSession;
import io.github.dinamo541.idearm.domain.port.ProcessLauncher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An active DOSBox debugging session wrapping an external debugger (Turbo Debugger or CodeView).
 */
public final class DosBoxDebugSession implements DebugSession {

    static final String EXIT_SENTINEL = "EXITCODE.TXT";

    private final ProcessLauncher.LaunchedProcess process;
    private final Path sessionDirectory;
    private final Path driveC;
    private final long startNanos;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean released = new AtomicBoolean(false);
    private final CompletableFuture<ExitInfo> exit = new CompletableFuture<>();

    public DosBoxDebugSession(ProcessLauncher.LaunchedProcess process, Path sessionDirectory, Path driveC,
                              long startNanos) {
        this.process = process;
        this.sessionDirectory = sessionDirectory;
        this.driveC = driveC;
        this.startNanos = startNanos;

        process.onExit().thenAccept(code -> {
            if (stopped.get()) {
                return;
            }
            Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
            int exitCode = readExitSentinel(code);
            exit.complete(new ExitInfo(exitCode, duration, false, "Debug session terminated with exit code " + exitCode));
        });
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
    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            process.stop();
            exit.complete(ExitInfo.stopped(Duration.ofNanos(System.nanoTime() - startNanos)));
        }
    }

    @Override
    public void close() {
        stop();
        if (released.compareAndSet(false, true)) {
            deleteQuietly(sessionDirectory);
        }
    }

    private int readExitSentinel(int fallback) {
        Path sentinel = driveC.resolve(EXIT_SENTINEL);
        if (!Files.isRegularFile(sentinel, LinkOption.NOFOLLOW_LINKS)) {
            return fallback;
        }
        try {
            String text = Files.readString(sentinel, StandardCharsets.US_ASCII).strip();
            if (text.startsWith("RC=")) {
                return Integer.parseInt(text.substring(3).strip());
            }
            return fallback;
        } catch (IOException | NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
