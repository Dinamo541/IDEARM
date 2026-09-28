package io.github.dinamo541.idearm.domain.debug;

/**
 * What a debug session can actually do, so the workbench only offers what the chosen debugger supports.
 *
 * <p>Every operation on {@code DebugSession} beyond starting and stopping has a default that does nothing, which
 * a launch-only backend (Turbo Debugger or CodeView in a DOSBox window) inherits. Without this declaration the
 * user interface cannot tell a working step from a silent no-op, and shows buttons that do nothing.
 */
public enum DebugCapability {

    /** Step over, into and out of instructions, and continue to the next breakpoint. */
    STEP,

    /** Interrupt a freely running program and report where it stopped. */
    PAUSE,

    /** Stop on breakpoints, including ones set or cleared while the session runs. */
    BREAKPOINTS,

    /** Report register and flag values at every stop. */
    REGISTERS,

    /** Read the program's memory. */
    MEMORY,

    /** Evaluate watch expressions. */
    WATCHES,

    /** Report the call stack, either as frames or as the words on the stack. */
    CALL_STACK,

    /** Accept keystrokes typed for the program inside the IDE. */
    PROGRAM_INPUT,

    /** Disassemble the instructions around the current position. */
    DISASSEMBLY
}
