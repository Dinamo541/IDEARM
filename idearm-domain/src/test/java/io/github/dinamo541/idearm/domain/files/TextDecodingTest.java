package io.github.dinamo541.idearm.domain.files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.files.TextDecoding.TextFormat;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TextDecodingTest {

    @Test
    void anAnsiFileWithAccentsOpensAsWindows1252() {
        byte[] ansi = "; Cálculo del año\r\nmov ax, 1\r\n".getBytes(TextDecoding.FALLBACK);

        var decoded = TextDecoding.decode(ansi);

        assertEquals("; Cálculo del año\nmov ax, 1\n", decoded.text());
        assertEquals(TextDecoding.FALLBACK, decoded.format().charset());
        assertEquals("CRLF", decoded.format().lineSeparatorName());
        assertFalse(decoded.format().bom());
    }

    @Test
    void validUtf8StaysUtf8() {
        var decoded = TextDecoding.decode("; año\nmov ax, 1\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(StandardCharsets.UTF_8, decoded.format().charset());
        assertEquals("LF", decoded.format().lineSeparatorName());
        assertEquals("; año\nmov ax, 1\n", decoded.text());
    }

    @Test
    void anUnchangedFileIsWrittenBackByteForByte() throws CharacterCodingException {
        byte[][] files = {
                "; Cálculo\r\nmov ax, 1\r\n".getBytes(TextDecoding.FALLBACK),
                "; año\nmov ax, 1".getBytes(StandardCharsets.UTF_8),
                withUtf8Bom("mov ax, 1\r\nint 21h\r\n"),
                "mov ax, 1\rint 21h\r".getBytes(StandardCharsets.US_ASCII),
                new byte[0]
        };
        for (byte[] file : files) {
            var decoded = TextDecoding.decode(file);
            assertArrayEquals(file, TextDecoding.encode(decoded.text(), decoded.format()));
        }
    }

    @Test
    void aByteOrderMarkIsRememberedAndNotShown() {
        var decoded = TextDecoding.decode(withUtf8Bom("mov ax, 1\r\n"));

        assertTrue(decoded.format().bom());
        assertEquals("mov ax, 1\n", decoded.text());
    }

    @Test
    void malformedBytesNeverFailToOpen() {
        byte[] garbage = {(byte) 0xC3, (byte) 0x28, (byte) 0xFF, 'a'};

        var decoded = TextDecoding.decode(garbage);

        assertEquals(TextDecoding.FALLBACK, decoded.format().charset());
        assertEquals(4, decoded.text().length());
    }

    @Test
    void aCharacterTheCharsetCannotHoldIsReportedInsteadOfLost() {
        var ansi = new TextFormat(TextDecoding.FALLBACK, false, "\r\n");

        assertThrows(CharacterCodingException.class, () -> TextDecoding.encode("π = 3.14", ansi));
    }

    @Test
    void editedTextKeepsTheFileLineSeparator() throws CharacterCodingException {
        var crlf = new TextFormat(StandardCharsets.UTF_8, false, "\r\n");

        assertArrayEquals("a\r\nb\r\n".getBytes(StandardCharsets.UTF_8), TextDecoding.encode("a\nb\n", crlf));
    }

    private static byte[] withUtf8Bom(String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[body.length + 3];
        result[0] = (byte) 0xEF;
        result[1] = (byte) 0xBB;
        result[2] = (byte) 0xBF;
        System.arraycopy(body, 0, result, 3, body.length);
        return result;
    }
}
