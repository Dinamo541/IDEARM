package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.ProcessRequest;
import io.github.dinamo541.idearm.domain.port.ProcessLauncher;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Starts interactive processes (DOSBox sessions) owned by a Windows Job Object, so the emulator terminates with
 * the IDE even when the IDE dies abruptly.
 *
 * <p>Output is discarded on purpose: an inherited handle would keep the parent pipes open, and the program's own
 * console lives inside the emulator window.
 */
public final class JobProcessLauncher implements ProcessLauncher {

    private static final boolean WINDOWS =
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");

    @Override
    public LaunchedProcess start(ProcessRequest request) {
        WindowsJob job = null;
        try {
            if (WINDOWS) {
                job = WindowsJob.create();
            }
            var builder = new ProcessBuilder(request.command());
            if (request.interactive()) {
                // The IDE shows the program's console itself: output and errors arrive merged, input is written.
                builder.redirectErrorStream(true);
            } else {
                builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
            }
            if (request.workingDirectory() != null) {
                builder.directory(request.workingDirectory().toFile());
            }
            ProcessEnvironment.apply(builder, request);
            Process process = builder.start();
            if (!request.interactive()) {
                process.getOutputStream().close();
            }
            if (job != null) {
                job.assign(process);
            }
            return new JobOwnedProcess(process, job, request.interactive());
        } catch (IOException failure) {
            if (job != null) {
                job.close();
            }
            throw new DomainException("process.launch.failed",
                    "Cannot start " + request.command().getFirst() + ": " + failure.getMessage(), failure, request.command().getFirst(), failure.getMessage());
        }
    }


    private static final class JobOwnedProcess implements LaunchedProcess {

        private final Process process;
        private final WindowsJob job;
        private final boolean interactive;
        private final CompletableFuture<Integer> exit = new CompletableFuture<>();

        private JobOwnedProcess(Process process, WindowsJob job, boolean interactive) {
            this.process = process;
            this.job = job;
            this.interactive = interactive;
            process.onExit().thenAccept(finished -> exit.complete(finished.exitValue()));
        }

        @Override
        public java.util.Optional<java.io.InputStream> output() {
            return interactive ? java.util.Optional.of(process.getInputStream()) : java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<java.io.OutputStream> input() {
            return interactive ? java.util.Optional.of(process.getOutputStream()) : java.util.Optional.empty();
        }

        @Override
        public long pid() {
            return process.pid();
        }

        @Override
        public boolean alive() {
            return process.isAlive();
        }

        @Override
        public CompletableFuture<Integer> onExit() {
            return exit;
        }

        @Override
        public void stop() {
            terminateTree();
            close();
        }

        /**
         * Releases the session. Closing the job terminates whatever the emulator left behind; where there is no
         * job (a host other than Windows) the tree is terminated explicitly, so the guarantee is the same.
         */
        @Override
        public void close() {
            if (job != null) {
                job.close();
            } else if (process.isAlive()) {
                terminateTree();
            }
            try {
                process.getInputStream().close();
            } catch (IOException ignored) {
                // The stream is discarded; nothing depends on closing it cleanly.
            } catch (UncheckedIOException ignored) {
                // Same as above.
            }
        }

        private void terminateTree() {
            List<ProcessHandle> children = process.descendants().toList();
            for (ProcessHandle child : children.reversed()) {
                child.destroy();
            }
            process.destroy();
            try {
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
                for (ProcessHandle child : children.reversed()) {
                    if (child.isAlive()) {
                        child.destroyForcibly();
                    }
                }
                process.waitFor(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }
}
