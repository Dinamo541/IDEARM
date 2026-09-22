package io.github.dinamo541.idearm.domain.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BreakpointTest {

    @Test
    void normalizesPathSeparatorsAndTrims() {
        Breakpoint bp = new Breakpoint("src\\MAIN.ASM", 10, true);
        assertEquals("src/MAIN.ASM", bp.path());
        assertEquals(10, bp.line());
        assertTrue(bp.enabled());

        Breakpoint bp2 = new Breakpoint("/src/lib/UTILS.ASM ", 25);
        assertEquals("src/lib/UTILS.ASM", bp2.path());
        assertEquals(25, bp2.line());
        assertTrue(bp2.enabled());
    }

    @Test
    void rejectsLineZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Breakpoint("src/MAIN.ASM", 0));
        assertThrows(IllegalArgumentException.class, () -> new Breakpoint("src/MAIN.ASM", -5));
    }

    @Test
    void withEnabledCreatesUpdatedCopy() {
        Breakpoint bp = new Breakpoint("src/MAIN.ASM", 42, true);
        Breakpoint disabled = bp.withEnabled(false);
        assertFalse(disabled.enabled());
        assertEquals(bp.path(), disabled.path());
        assertEquals(bp.line(), disabled.line());
    }
}
