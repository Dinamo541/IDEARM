package io.github.dinamo541.idearm.domain.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LocationTest {

    @Test
    void carriesTheSpanOfTheProblemWhenOneIsKnown() {
        Location location = new Location("main.asm", 12, 5, 3);

        assertEquals(12, location.line());
        assertEquals(5, location.column());
        assertEquals(3, location.length());
    }

    @Test
    void hasNoSpanWhenOnlyTheLineAndColumnAreKnown() {
        // What every assembler reports: it names the line, never how wide the problem is.
        Location location = new Location("main.asm", 12, 5);

        assertNull(location.length());
    }

    @Test
    void rejectsASpanThatCoversNothing() {
        assertThrows(IllegalArgumentException.class, () -> new Location("main.asm", 1, 1, 0));
    }
}
