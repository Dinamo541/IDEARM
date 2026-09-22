package io.github.dinamo541.idearm.domain.port;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * An interactive host terminal session (e.g. running PowerShell, CMD, or bash).
 */
public interface TerminalSession extends AutoCloseable {

    /**
     * Sends a line of text or command into the terminal's standard input stream.
     *
     * @param input the input string to send
     */
    void sendInput(String input);

    /**
     * Registers a listener to receive streamed output chunks from the terminal.
     *
     * @param consumer the consumer receiving text chunks
     */
    void onOutput(Consumer<String> consumer);

    /**
     * Checks if the underlying process is currently running.
     */
    boolean isAlive();

    /**
     * Terminates the terminal process and all descendant processes.
     */
    void terminate();

    /**
     * Completes when the terminal process exits.
     */
    CompletableFuture<Integer> onExit();

    @Override
    void close();
}
