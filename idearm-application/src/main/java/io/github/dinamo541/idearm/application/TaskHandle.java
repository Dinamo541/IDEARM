package io.github.dinamo541.idearm.application;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TaskHandle<T> {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final CompletableFuture<T> completion = new CompletableFuture<>();

    /** Requests cancellation; completion resolves only after process and workspace cleanup. */
    public void cancel() { cancelled.set(true); }
    public boolean cancellationRequested() { return cancelled.get(); }
    public CompletableFuture<T> completion() { return completion; }
}
