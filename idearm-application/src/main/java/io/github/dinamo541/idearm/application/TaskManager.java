package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.CancellationToken;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/** One active operation per project; filesystem locks additionally protect separate IDE processes. */
public final class TaskManager implements AutoCloseable {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<Path, TaskHandle<?>> tasks = new ConcurrentHashMap<>();
    private boolean closed;

    public synchronized <T> TaskHandle<T> submit(Path project, Function<CancellationToken, T> action) {
        if (closed) throw new IllegalStateException("Task manager is closed.");
        Path key = project.toAbsolutePath().normalize();
        var handle = new TaskHandle<T>();
        if (tasks.putIfAbsent(key, handle) != null) {
            throw new DomainException("project.busy", "Another task is active for this project.");
        }
        try {
            executor.submit(() -> {
                T result = null;
                Throwable failure = null;
                try {
                    result = action.apply(() -> handle.cancellationRequested() || Thread.currentThread().isInterrupted());
                } catch (Throwable ex) {
                    failure = ex;
                } finally {
                    tasks.remove(key, handle);
                }
                if (failure == null) handle.completion().complete(result);
                else handle.completion().completeExceptionally(failure);
            });
        } catch (RuntimeException ex) {
            tasks.remove(key, handle);
            throw ex;
        }
        return handle;
    }

    @Override public void close() {
        synchronized (this) {
            closed = true;
            tasks.values().forEach(TaskHandle::cancel);
            executor.shutdown();
        }
        executor.close();
    }
}
