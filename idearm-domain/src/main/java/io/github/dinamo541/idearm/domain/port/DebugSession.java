package io.github.dinamo541.idearm.domain.port;

import io.github.dinamo541.idearm.domain.debug.CallFrame;
import io.github.dinamo541.idearm.domain.debug.MemoryView;
import io.github.dinamo541.idearm.domain.debug.RegisterState;
import io.github.dinamo541.idearm.domain.debug.StackFrame;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Represents an active debugging session (external in DOSBox or emulated).
 */
public interface DebugSession extends AutoCloseable {

    CompletableFuture<ExitInfo> exit();

    SessionState state();

    void stop();

    default void resume() {}

    default void stepOver() {}

    default void stepInto() {}

    default void stepOut() {}

    /**
     * Types into the debugged program's keyboard: each character is one key, {@code \r} is Enter. Sessions whose
     * program reads its keyboard elsewhere (a DOSBox window, a console) ignore it.
     */
    default void sendInput(String text) {}

    default long instructionsExecuted() {
        return 0L;
    }

    default RegisterState registers() {
        return RegisterState.empty();
    }

    default MemoryView readMemory(int segment, int offset, int length) {
        return new MemoryView(segment, offset, new byte[Math.max(0, length)]);
    }

    /** Memory of a 32/64-bit program at a flat address. */
    default MemoryView readMemory(long address, int length) {
        return MemoryView.flat(address, new byte[Math.max(0, length)]);
    }

    default List<StackFrame> stack(int depth) {
        return List.of();
    }

    default List<CallFrame> callStack(int depth) {
        return List.of();
    }

    default Optional<Long> evaluateVariable(String filePath, int line, int byteSize) {
        return Optional.empty();
    }

    default Optional<String> evaluateExpression(String expr) {
        return Optional.empty();
    }

    @Override
    void close();
}
