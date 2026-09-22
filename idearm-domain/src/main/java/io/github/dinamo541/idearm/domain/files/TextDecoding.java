package io.github.dinamo541.idearm.domain.files;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Reads source text the way it was written, and writes it back the same way.
 *
 * <p>Assembly sources come from many editors: UTF-8 from modern ones, Windows-1252 from Notepad or Notepad++ in
 * "ANSI" mode, with or without a byte order mark, with CRLF or LF line endings. Decoding every file as strict UTF-8
 * made files with accents in their comments impossible to open. The text is decoded as UTF-8 when it is valid
 * UTF-8, and as Windows-1252 otherwise (a decision recorded in docs/action-plan.md, D3); inside the editor every
 * line ends in {@code \n}, and saving restores the original charset, byte order mark and line separator.
 */
public final class TextDecoding {

    /** What a non-UTF-8 source is read as. */
    public static final Charset FALLBACK = Charset.forName("windows-1252");

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF16LE_BOM = {(byte) 0xFF, (byte) 0xFE};
    private static final byte[] UTF16BE_BOM = {(byte) 0xFE, (byte) 0xFF};

    /** How a file was stored: its charset, whether it began with a byte order mark, and its line separator. */
    public record TextFormat(Charset charset, boolean bom, String lineSeparator) {

        /** New files: UTF-8 without a byte order mark, one {@code \n} per line. */
        public static final TextFormat DEFAULT = new TextFormat(StandardCharsets.UTF_8, false, "\n");

        public TextFormat {
            Objects.requireNonNull(charset, "charset");
            Objects.requireNonNull(lineSeparator, "lineSeparator");
        }

        public TextFormat withCharset(Charset other) {
            return new TextFormat(other, bom && isUnicode(other), lineSeparator);
        }

        /** "CRLF", "LF" or "CR", as editors show it. */
        public String lineSeparatorName() {
            return switch (lineSeparator) {
                case "\r\n" -> "CRLF";
                case "\r" -> "CR";
                default -> "LF";
            };
        }
    }

    /** The text with every line ending turned into {@code \n}, and the format needed to write it back. */
    public record DecodedText(String text, TextFormat format) {
        public DecodedText {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(format, "format");
        }
    }

    private TextDecoding() {
    }

    public static DecodedText decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        Charset charset;
        boolean bom = true;
        int skip;
        if (startsWith(bytes, UTF8_BOM)) {
            charset = StandardCharsets.UTF_8;
            skip = UTF8_BOM.length;
        } else if (startsWith(bytes, UTF16LE_BOM)) {
            charset = StandardCharsets.UTF_16LE;
            skip = UTF16LE_BOM.length;
        } else if (startsWith(bytes, UTF16BE_BOM)) {
            charset = StandardCharsets.UTF_16BE;
            skip = UTF16BE_BOM.length;
        } else {
            bom = false;
            skip = 0;
            charset = isValidUtf8(bytes) ? StandardCharsets.UTF_8 : FALLBACK;
        }
        String raw = new String(bytes, skip, bytes.length - skip, charset);
        return new DecodedText(normalizeLineEndings(raw), new TextFormat(charset, bom, lineSeparatorOf(raw)));
    }

    /**
     * The bytes to store {@code text} (whose lines end in {@code \n}) in the given format.
     *
     * @throws CharacterCodingException when a character cannot be represented in the format's charset
     */
    public static byte[] encode(String text, TextFormat format) throws CharacterCodingException {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(format, "format");
        String stored = normalizeLineEndings(text).replace("\n", format.lineSeparator());
        ByteBuffer encoded = format.charset().newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(stored));
        byte[] body = Arrays.copyOfRange(encoded.array(), encoded.position(), encoded.limit());
        if (!format.bom()) {
            return body;
        }
        byte[] mark = bomFor(format.charset());
        byte[] result = Arrays.copyOf(mark, mark.length + body.length);
        System.arraycopy(body, 0, result, mark.length, body.length);
        return result;
    }

    /** Every line ending ({@code \r\n}, {@code \r}) as {@code \n}. */
    public static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String lineSeparatorOf(String text) {
        int newline = text.indexOf('\n');
        int carriage = text.indexOf('\r');
        if (carriage >= 0 && (newline < 0 || carriage < newline)) {
            return carriage + 1 < text.length() && text.charAt(carriage + 1) == '\n' ? "\r\n" : "\r";
        }
        return "\n";
    }

    private static boolean isValidUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException notUtf8) {
            return false;
        }
    }

    private static boolean isUnicode(Charset charset) {
        return charset.equals(StandardCharsets.UTF_8) || charset.equals(StandardCharsets.UTF_16LE)
                || charset.equals(StandardCharsets.UTF_16BE);
    }

    private static byte[] bomFor(Charset charset) {
        if (charset.equals(StandardCharsets.UTF_16LE)) return UTF16LE_BOM.clone();
        if (charset.equals(StandardCharsets.UTF_16BE)) return UTF16BE_BOM.clone();
        if (charset.equals(StandardCharsets.UTF_8)) return UTF8_BOM.clone();
        return new byte[0];
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }
}
