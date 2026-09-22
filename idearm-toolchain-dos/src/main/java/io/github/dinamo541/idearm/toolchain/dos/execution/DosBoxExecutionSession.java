package io.github.dinamo541.idearm.toolchain.dos.execution;

import io.github.dinamo541.idearm.domain.execution.ExecutionSession;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;
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
 * An active DOSBox run: it watches the emulator, reads the exit sentinel the generated batch leaves behind, and
 * removes the disposable staging folder afterwards.
 */
public final class DosBoxExecutionSession implements ExecutionSession {

    /** Written by the generated batch as {@code RC=<code>}; DOSBox itself never reports the program status. */
    static final String EXIT_SENTINEL = "EXITCODE.TXT";

    private final ProcessLauncher.LaunchedProcess process;
    private final Path sessionDirectory;
    private final Path driveC;
    private final long startNanos;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean released = new AtomicBoolean(false);
    private final CompletableFuture<ExitInfo> exit = new CompletableFuture<>();

    public DosBoxExecutionSession(ProcessLauncher.LaunchedProcess process, Path sessionDirectory, Path driveC,
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
            exit.complete(new ExitInfo(exitCode, duration, false, "Program terminated with exit code " + exitCode));
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
    public Path stagingDirectory() {
        return driveC;
    }

    /**
     * Ends the session. A program that already finished keeps its exit status: only a session still running is
     * stopped, and the staging copy is always removed.
     */
    @Override
    public void close() {
        if (process.alive()) {
            stop();
        } else {
            process.close();
        }
        if (released.compareAndSet(false, true)) {
            cleanupStaging();
        }
    }

    private int readExitSentinel(int processExitCode) {
        Path sentinel = driveC.resolve(EXIT_SENTINEL);
        if (Files.isRegularFile(sentinel, LinkOption.NOFOLLOW_LINKS)) {
            try {
                String content = Files.readString(sentinel, StandardCharsets.US_ASCII).strip();
                if (content.startsWith("RC=")) {
                    content = content.substring(3);
                }
                int parsed = Integer.parseInt(content.strip());
                if (parsed >= 0 && parsed <= 255) {
                    return parsed;
                }
            } catch (IOException | NumberFormatException ignored) {
                // A missing or malformed sentinel falls back to the emulator's own status below.
            }
        }
        return processExitCode;
    }

    private void cleanupStaging() {
        try (var tree = Files.walk(sessionDirectory)) {
            tree.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Windows may still hold a handle; the next run sweeps what is left.
                }
            });
        } catch (IOException ignored) {
            // Nothing to clean.
        }
    }
}
