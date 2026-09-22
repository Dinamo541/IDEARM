package io.github.dinamo541.idearm.domain.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CallFrameTest {

    @Test
    void formatsDisplayStringWithFullDetails() {
        CallFrame frame = new CallFrame(0, "0x00401000", "main", "src/main.asm", 15);
        assertEquals("#0 0x00401000 in main at src/main.asm:15", frame.toDisplayString());
    }

    @Test
    void handlesDefaultsForEmptyOrNullFields() {
        CallFrame frame = new CallFrame(1, null, null, null, 0);
        assertEquals(1, frame.level());
        assertEquals("", frame.address());
        assertEquals("??", frame.function());
        assertEquals("", frame.file());
        assertEquals(0, frame.line());
        assertEquals("#1 in ??", frame.toDisplayString());
    }

    @Test
    void formatsFrameWithoutLineNumber() {
        CallFrame frame = new CallFrame(2, "0x7FF61234", "printf", "libc.so", 0);
        assertEquals("#2 0x7FF61234 in printf at libc.so", frame.toDisplayString());
    }
}
