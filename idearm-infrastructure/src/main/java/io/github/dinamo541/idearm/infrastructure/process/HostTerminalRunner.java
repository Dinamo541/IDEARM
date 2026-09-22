package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.port.TerminalRunner;
import io.github.dinamo541.idearm.domain.port.TerminalSession;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Launches an interactive host terminal (PowerShell/CMD on Windows, bash on Unix/macOS)
 * and manages its lifecycle under a Windows Job Object where available.
 */
public final class HostTerminalRunner implements TerminalRunner {

    private static final boolean WINDOWS =
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");

    @Override
    public TerminalSession startSession(Path workingDirectory, Consumer<String> outputListener) {
        List<String> command = resolveShellCommand();
        WindowsJob job = null;
        try {
            if (WINDOWS) {
                job = WindowsJob.create();
            }
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            if (workingDirectory != null && workingDirectory.toFile().isDirectory()) {
                builder.directory(workingDirectory.toFile());
            }
            Process process = builder.start();
            if (job != null) {
                job.assign(process);
            }
            return new HostTerminalSession(process, job, outputListener);
        } catch (IOException e) {
            if (job != null) {
                job.close();
            }
            throw new DomainException("terminal.launch.failed", "Failed to start terminal shell: " + e.getMessage(), e, e.getMessage());
        }
    }

    private static List<String> resolveShellCommand() {
        if (WINDOWS) {
            return List.of("powershell.exe", "-NoLogo");
        }
        String shell = System.getenv("SHELL");
        if (shell != null && !shell.isBlank()) {
            return List.of(shell);
        }
        return List.of("/bin/bash");
    }

    private static final class HostTerminalSession implements TerminalSession {
        private final Process process;
        private final WindowsJob job;
        private final BufferedWriter writer;
        private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
        private final CompletableFuture<Integer> exitFuture = new CompletableFuture<>();

        private HostTerminalSession(Process process, WindowsJob job, Consumer<String> initialListener) {
            this.process = process;
            this.job = job;
            if (initialListener != null) {
                this.listeners.add(initialListener);
            }

            Charset charset = WINDOWS ? Charset.defaultCharset() : StandardCharsets.UTF_8;
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), charset));

            Thread.ofVirtual().name("idearm-terminal-reader").start(() -> {
                try (InputStream in = process.getInputStream();
                     InputStreamReader reader = new InputStreamReader(in, charset)) {
                    char[] buffer = new char[1024];
                    int read;
                    while ((read = reader.read(buffer)) != -1) {
                        String text = new String(buffer, 0, read);
                        for (Consumer<String> listener : listeners) {
                            try {
                                listener.accept(text);
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (IOException ignored) {
                } finally {
                    process.onExit().thenAccept(p -> exitFuture.complete(p.exitValue()));
                }
            });

            process.onExit().thenAccept(p -> exitFuture.complete(p.exitValue()));
        }

        @Override
        public void sendInput(String input) {
            if (!isAlive()) return;
            try {
                writer.write(input);
                writer.newLine();
                writer.flush();
            } catch (IOException ignored) {}
        }

        @Override
        public void onOutput(Consumer<String> consumer) {
            if (consumer != null) {
                listeners.add(consumer);
            }
        }

        @Override
        public boolean isAlive() {
            return process.isAlive();
        }

        @Override
        public void terminate() {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroy);
                process.destroy();
                try {
                    if (!process.waitFor(300, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
            if (job != null) {
                job.close();
            }
        }

        @Override
        public CompletableFuture<Integer> onExit() {
            return exitFuture;
        }

        @Override
        public void close() {
            terminate();
        }
    }
}
