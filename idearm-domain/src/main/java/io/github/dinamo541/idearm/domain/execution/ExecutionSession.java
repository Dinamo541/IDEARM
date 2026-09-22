package io.github.dinamo541.idearm.domain.execution;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Handle to an active or completed execution session.
 *
 * <p>Most sessions give the program its own window (DOSBox, a Windows console), where it reads the keyboard and
 * writes its output. An {@linkplain #interactive() interactive} session has no window: its output reaches
 * {@link #onOutput} listeners and {@link #sendInput} feeds its standard input, so the IDE shows it instead.
 */
public interface ExecutionSession extends AutoCloseable {

    SessionState state();

    CompletableFuture<ExitInfo> exit();

    void stop();

    Path stagingDirectory();

    /** Whether the program's input and output go through this session instead of a window of its own. */
    default boolean interactive() {
        return false;
    }

    /** Receives the program's output as it is written; only interactive sessions produce any. */
    default void onOutput(Consumer<String> listener) {
    }

    /** Writes to the program's standard input; ignored by sessions whose program has a window of its own. */
    default void sendInput(String text) {
    }

    @Override
    default void close() {
        stop();
    }
}
