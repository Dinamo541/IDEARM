package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.domain.debug.CallFrame;
import io.github.dinamo541.idearm.domain.debug.StackFrame;

import java.util.Objects;

/**
 * One row of the call stack panel.
 *
 * <p>A backend that reports frames (GDB) gives a row that knows its source file and line, so double-clicking it
 * can open that place. The built-in emulator reports the words on the stack instead, which belong to no source
 * line: those rows carry text only.
 */
public final class CallFrameItemViewModel {

    private final String text;
    private final String file;
    private final int line;

    private CallFrameItemViewModel(String text, String file, int line) {
        this.text = Objects.requireNonNull(text, "text cannot be null");
        this.file = file;
        this.line = line;
    }

    /** A frame from a backend that knows the program's functions. */
    public static CallFrameItemViewModel of(CallFrame frame) {
        boolean located = frame.file() != null && !frame.file().isBlank() && frame.line() > 0;
        return new CallFrameItemViewModel(frame.toDisplayString(), located ? frame.file() : null,
                located ? frame.line() : 0);
    }

    /** A word on the stack, as the built-in emulator reports it. */
    public static CallFrameItemViewModel of(StackFrame word) {
        return new CallFrameItemViewModel(
                "[SP+%02X] %04X:%04X = %04X".formatted(word.offsetFromSp(), word.segment(), word.offset(),
                        word.value()),
                null, 0);
    }

    public String getText() {
        return text;
    }

    /** Whether this row points at a place in the source that can be opened. */
    public boolean isNavigable() {
        return file != null;
    }

    public String getFile() {
        return file;
    }

    public int getLine() {
        return line;
    }

    @Override
    public String toString() {
        return text;
    }
}
