package io.github.dinamo541.idearm.domain.port;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Port for launching interactive host terminal sessions.
 */
public interface TerminalRunner {

    /**
     * Starts an interactive terminal process in the specified working directory.
     *
     * @param workingDirectory the directory where the shell should start
     * @param outputListener callback to receive streamed text from the shell
     * @return the active terminal session
     */
    TerminalSession startSession(Path workingDirectory, Consumer<String> outputListener);
}
