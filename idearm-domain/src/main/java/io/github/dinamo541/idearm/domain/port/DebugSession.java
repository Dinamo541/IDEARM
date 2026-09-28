package io.github.dinamo541.idearm.domain.port;

import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.CallFrame;
import io.github.dinamo541.idearm.domain.debug.DebugCapability;
import io.github.dinamo541.idearm.domain.debug.DisasmLine;
import io.github.dinamo541.idearm.domain.debug.MemoryView;
import io.github.dinamo541.idearm.domain.debug.RegisterState;
import io.github.dinamo541.idearm.domain.debug.StackFrame;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Represents an active debugging session (external in DOSBox or emulated).
 */
public interface DebugSession extends AutoCloseable {

    CompletableFuture<ExitInfo> exit();

    SessionState state();

    void stop();

    /**
     * What this session supports. Everything below {@link #stop()} has a default that does nothing, so a session
     * that declares nothing is a launch-only one: the workbench then offers no stepping and no panels instead of
     * buttons that would silently do nothing.
     */
    default Set<DebugCapability> capabilities() {
        return Set.of();
    }

    default void resume() {}

    default void stepOver() {}

    default void stepInto() {}

    default void stepOut() {}

    /** Interrupts a freely running program, so an endless loop can be inspected instead of only killed. */
    default void pause() {}

    /**
     * Replaces the breakpoints of a running session, so one set or cleared during a pause takes effect on the
     * next resume instead of only on the next launch.
     */
    default void setBreakpoints(List<Breakpoint> breakpoints) {}

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

    /**
     * The next {@code count} machine instructions from where the program currently stands, the first one marked as
     * current. The session knows how its target addresses memory, so no address is passed in.
     */
    default List<DisasmLine> disassemble(int count) {
        return List.of();
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
