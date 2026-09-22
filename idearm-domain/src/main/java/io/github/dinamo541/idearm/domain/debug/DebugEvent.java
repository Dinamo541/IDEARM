package io.github.dinamo541.idearm.domain.debug;

import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Lifecycle and execution events emitted during a debug session.
 */
public sealed interface DebugEvent {

    record Started(Path projectRoot, String debugger) implements DebugEvent {
        public Started {
            Objects.requireNonNull(projectRoot);
            Objects.requireNonNull(debugger);
        }
    }

    record Paused(String file, int line, RegisterState registers) implements DebugEvent {
        public Paused {
            Objects.requireNonNull(file);
            Objects.requireNonNull(registers);
        }
    }

    record Resumed() implements DebugEvent {}

    record Output(String text) implements DebugEvent {
        public Output {
            Objects.requireNonNull(text);
        }
    }

    record Stopped() implements DebugEvent {}

    record Exited(ExitInfo exitInfo) implements DebugEvent {
        public Exited {
            Objects.requireNonNull(exitInfo);
        }
    }

    record SessionAttached(io.github.dinamo541.idearm.domain.port.DebugSession session) implements DebugEvent {
        public SessionAttached {
            Objects.requireNonNull(session);
        }
    }

    /** The program is reading the keyboard and nothing is typed yet; see {@code DebugSession.sendInput}. */
    record WaitingForInput() implements DebugEvent {}

    /**
     * Something the program did that the debugger explains, such as a division by zero or an instruction the
     * emulator does not implement. The code and arguments let the user interface show it in its language.
     */
    record Problem(io.github.dinamo541.idearm.domain.diagnostic.Diagnostic diagnostic) implements DebugEvent {
        public Problem {
            Objects.requireNonNull(diagnostic);
        }
    }
}
