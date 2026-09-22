package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.build.ProcessRequest;
import io.github.dinamo541.idearm.domain.build.ProcessResult;
import io.github.dinamo541.idearm.domain.port.ProcessExecutor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Runs argument vectors directly, drains output and owns process lifetime. No shell interpolation. */
public final class ProcessService implements ProcessExecutor {
    private static final int OUTPUT_LIMIT = 1024 * 1024;
    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");

    @Override public ProcessResult run(ProcessRequest request, CancellationToken cancellation) {
        if (cancellation.cancelled()) return new ProcessResult(-1, "", true, false);
        Process process = null;
        WindowsJob job = null;
        Thread drainer = null;
        BoundedOutput output = new BoundedOutput();
        boolean cancelled = false;
        boolean timedOut = false;
        try {
            // Create before starting the child; assignment immediately after start is the F0-proven approach.
            if (WINDOWS) job = WindowsJob.create();
            ProcessBuilder builder = new ProcessBuilder(request.command()).redirectErrorStream(true);
            if (request.workingDirectory() != null) builder.directory(request.workingDirectory().toFile());
            ProcessEnvironment.apply(builder, request);
            process = builder.start();
            process.getOutputStream().close();
            if (job != null) job.assign(process);
            InputStream stream = process.getInputStream();
            drainer = Thread.ofVirtual().name("idearm-process-output").start(() -> output.drain(stream));
            long started = System.nanoTime();
            long timeout = request.timeout().toNanos();
            while (process.isAlive()) {
                cancelled = cancellation.cancelled();
                timedOut = !cancelled && System.nanoTime() - started >= timeout;
                if (cancelled || timedOut) { terminate(process); break; }
                process.waitFor(25, TimeUnit.MILLISECONDS);
            }
            // Closing a job also terminates children left behind by an already-exited parent.
            if (job != null) { job.close(); job = null; }
            drainer.join(2000);
            if (drainer.isAlive()) process.getInputStream().close();
            return new ProcessResult(process.isAlive() ? -1 : process.exitValue(), output.text(), cancelled, timedOut);
        } catch (InterruptedException interrupted) {
            if (process != null) terminateUninterruptibly(process);
            Thread.currentThread().interrupt();
            return new ProcessResult(-1, output.text(), true, false);
        } catch (IOException failure) {
            throw new UncheckedIOException("process.execution.failed", failure);
        } finally {
            if (process != null && process.isAlive()) terminateUninterruptibly(process);
            if (job != null) job.close();
            if (process != null) {
                try { process.getInputStream().close(); } catch (IOException ignored) { }
            }
        }
    }


    private static void terminate(Process process) throws InterruptedException {
        List<ProcessHandle> children = process.descendants().toList();
        for (ProcessHandle child : children.reversed()) child.destroy();
        process.destroy();
        if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly();
        for (ProcessHandle child : children.reversed()) if (child.isAlive()) child.destroyForcibly();
        process.waitFor(2, TimeUnit.SECONDS);
    }

    private static void terminateUninterruptibly(Process process) {
        boolean interrupted = Thread.interrupted();
        try { terminate(process); }
        catch (InterruptedException failure) {
            interrupted = true;
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    private static final class BoundedOutput {
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private boolean truncated;
        void drain(InputStream stream) {
            byte[] chunk = new byte[8192];
            try {
                for (int count; (count = stream.read(chunk)) != -1; ) {
                    synchronized (this) {
                        int keep = Math.min(count, OUTPUT_LIMIT - bytes.size());
                        if (keep > 0) bytes.write(chunk, 0, keep);
                        if (keep < count) truncated = true;
                    }
                }
            } catch (IOException ignored) {
                // Closing a terminated process pipe is expected; execution status comes from the process.
            }
        }
        synchronized String text() {
            return bytes.toString(StandardCharsets.UTF_8) + (truncated ? "\n[process.output.truncated]\n" : "");
        }
    }
}
