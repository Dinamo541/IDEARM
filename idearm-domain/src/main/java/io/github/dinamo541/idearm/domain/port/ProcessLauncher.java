package io.github.dinamo541.idearm.domain.port;

import io.github.dinamo541.idearm.domain.build.ProcessRequest;
import java.util.concurrent.CompletableFuture;

/**
 * Starts a long-lived process that outlives the call, which is what running the user's program needs.
 *
 * <p>{@link ProcessExecutor} runs a tool to completion and returns its output; this port instead hands back a
 * live handle. Implementations own the process tree: when the handle is closed, or when the owning application
 * dies, every process started through it must terminate. Phase 0 proved that without such ownership DOSBox
 * survives an IDE crash (ADR-002).
 */
public interface ProcessLauncher {

    LaunchedProcess start(ProcessRequest request);

    /** A running process whose whole tree dies with it. */
    interface LaunchedProcess extends AutoCloseable {

        long pid();

        boolean alive();

        /** Completes with the process exit code. */
        CompletableFuture<Integer> onExit();

        /** Terminates the process and everything it started. */
        void stop();

        /** The program's standard output and error, merged, for an interactive request; empty otherwise. */
        default java.util.Optional<java.io.InputStream> output() {
            return java.util.Optional.empty();
        }

        /** The program's standard input for an interactive request; empty otherwise. */
        default java.util.Optional<java.io.OutputStream> input() {
            return java.util.Optional.empty();
        }

        @Override
        void close();
    }
}
