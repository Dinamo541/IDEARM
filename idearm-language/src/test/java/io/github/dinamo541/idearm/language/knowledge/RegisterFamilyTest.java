package io.github.dinamo541.idearm.language.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A student who opens one width of a register must see the others: the 16, 32 and 64-bit names of the same storage.
 */
class RegisterFamilyTest {

    private final Corpus corpus = Corpus.get();

    private List<String> familyNames(String register) {
        return corpus.registerFamily(register).stream().map(RegisterView::name).toList();
    }

    @Test
    void theFamilyIsTheSameWhicheverWidthIsAskedFor() {
        // Widest first, and within one width the high half before the low one, as a register is drawn.
        List<String> expected = List.of("RAX", "EAX", "AX", "AH", "AL");
        assertEquals(expected, familyNames("AX"), "asked from 16 bits");
        assertEquals(expected, familyNames("EAX"), "asked from 32 bits");
        assertEquals(expected, familyNames("RAX"), "asked from 64 bits");
        assertEquals(expected, familyNames("AL"), "asked from 8 bits");
    }

    @Test
    void theFamilyIsOrderedWidestFirstAndCarriesEveryWidth() {
        List<RegisterView> family = corpus.registerFamily("AX");

        assertEquals(64, family.getFirst().sizeBits());
        for (int i = 1; i < family.size(); i++) {
            assertTrue(family.get(i).sizeBits() <= family.get(i - 1).sizeBits(),
                    "widths must not grow along the family: " + family);
        }
        assertTrue(family.stream().anyMatch(v -> v.sizeBits() == 32), "a 32-bit view is missing");
        assertTrue(family.stream().anyMatch(v -> v.sizeBits() == 16), "a 16-bit view is missing");
        assertTrue(family.stream().anyMatch(v -> v.sizeBits() == 8), "an 8-bit view is missing");

        List<RegisterView> bytes = family.stream().filter(v -> v.sizeBits() == 8).toList();
        assertEquals(8, bytes.getFirst().offsetBits(), "the high byte comes before the low one");
    }

    @Test
    void theHighByteKeepsItsBitOffsetSoTheOverlapCanBeExplained() {
        RegisterView ah = corpus.registerFamily("AX").stream()
                .filter(v -> v.name().equals("AH")).findFirst().orElseThrow();
        RegisterView al = corpus.registerFamily("AX").stream()
                .filter(v -> v.name().equals("AL")).findFirst().orElseThrow();

        assertEquals(8, ah.offsetBits(), "AH starts at bit 8");
        assertEquals(0, al.offsetBits(), "AL starts at bit 0");
    }

    @Test
    void theNewRegistersOf64BitModeAlsoHaveTheirFourWidths() {
        assertEquals(List.of("R8", "R8D", "R8W", "R8B"), familyNames("R8D"));
        assertEquals(List.of("RSI", "ESI", "SI", "SIL"), familyNames("SI"));
    }

    @Test
    void theFlagsRegisterReportsItsThreeWidths() {
        assertEquals(List.of("RFLAGS", "EFLAGS", "FLAGS"), familyNames("FLAGS"));
    }

    @Test
    void aRegisterWithNoWiderContainerIsAFamilyOfOne() {
        assertEquals(List.of("DS"), familyNames("DS"));
        assertEquals(List.of("CS"), familyNames("CS"));
    }

    @Test
    void anUnknownRegisterHasNoFamilyInsteadOfFailing() {
        assertTrue(corpus.registerFamily("NOPE").isEmpty());
        assertTrue(corpus.registerFamily((RegisterEntry) null).isEmpty());
        assertTrue(corpus.registerFamily((String) null).isEmpty());
    }

    @Test
    void theWidestOfAnyMemberIsTheSameRegister() {
        RegisterEntry fromAl = corpus.widestOf(corpus.findRegister("AL").orElseThrow());
        RegisterEntry fromRax = corpus.widestOf(corpus.findRegister("RAX").orElseThrow());
        assertSame(fromRax, fromAl);
        assertEquals("RAX", fromAl.name());
    }
}
