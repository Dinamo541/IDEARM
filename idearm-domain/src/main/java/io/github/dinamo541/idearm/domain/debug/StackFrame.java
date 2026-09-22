package io.github.dinamo541.idearm.domain.debug;

/**
 * A word entry in the 8086 stack relative to the current Stack Pointer (SP).
 *
 * @param offsetFromSp 0 for top of stack ([SP]), +2 for [SP+2], etc.
 * @param segment SS segment
 * @param offset SP offset
 * @param value 16-bit word value stored at SS:offset
 */
public record StackFrame(int offsetFromSp, int segment, int offset, int value) {
    public StackFrame {
        segment &= 0xFFFF;
        offset &= 0xFFFF;
        value &= 0xFFFF;
    }
}
