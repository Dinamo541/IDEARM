package io.github.dinamo541.idearm.domain.build;

import io.github.dinamo541.idearm.domain.DomainException;
@FunctionalInterface
public interface CancellationToken {
    CancellationToken NONE = () -> false;
    boolean cancelled();
    default void throwIfCancelled() {
        if (cancelled()) throw new DomainException("task.cancelled", "The task was cancelled.");
    }
}
