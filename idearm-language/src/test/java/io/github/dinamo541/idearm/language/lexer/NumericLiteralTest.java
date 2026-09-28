package io.github.dinamo541.idearm.language.lexer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class NumericLiteralTest {

    @ParameterizedTest
    @CsvSource({
            "0Ah, 10", "21h, 33", "0FFFFh, 65535", "0bh, 11", "0b1h, 177", "0h, 0",
            "0x1F, 31", "0X1f, 31", "0h1F, 31",
            "1010b, 10", "0b101, 5", "0b, 0",
            "17o, 15", "17q, 15", "0o17, 15", "0q17, 15",
            "42, 42", "10d, 10", "0d10, 10", "0, 0"
    })
    void readsEveryRadixForm(String literal, long expected) {
        assertTrue(NumericLiteral.isLiteral(literal), literal);
        assertEquals(expected, NumericLiteral.parse(literal), literal);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "AH", "beach", "fah", "each", "1e", "0x", "0xg", "12a", "19o", "102b", "$0F"})
    void rejectsWordsThatAreNotNumbers(String word) {
        assertFalse(NumericLiteral.isLiteral(word), word);
        assertNull(NumericLiteral.parse(word), word);
    }

    @Test
    void readsFullSixtyFourBitValuesAsUnsigned() {
        assertEquals(-1L, NumericLiteral.parse("0FFFFFFFFFFFFFFFFh"));
        assertEquals(Long.MIN_VALUE, NumericLiteral.parse("8000000000000000h"));
    }

    @Test
    void anOverflowingLiteralHasTheShapeButNoValue() {
        assertTrue(NumericLiteral.isLiteral("1FFFFFFFFFFFFFFFFh"));
        assertNull(NumericLiteral.parse("1FFFFFFFFFFFFFFFFh"));
    }

    @Test
    void nullIsNotALiteral() {
        assertFalse(NumericLiteral.isLiteral(null));
        assertNull(NumericLiteral.parse(null));
    }
}
