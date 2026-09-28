package io.github.dinamo541.idearm.language.lexer;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The integer literals the lexer, the operand parser and hover all agree on.
 *
 * <p>MASM/TASM suffix forms ({@code 0Ah}, {@code 1010b}, {@code 17o}, {@code 17q}, {@code 10d}) and NASM prefix forms
 * ({@code 0x1F}, {@code 0h1F}, {@code 0b101}, {@code 0o17}, {@code 0q17}, {@code 0d10}) are accepted. A literal
 * always starts with a digit, so registers such as {@code AH} and labels such as {@code beach} are never numbers.
 * Values are read as unsigned 64-bit, so {@code 0FFFFFFFFFFFFFFFFh} is {@code -1L}.
 */
public final class NumericLiteral {

    private static final Pattern HEX_PREFIX = Pattern.compile("0[xh][0-9a-f]+");
    private static final Pattern HEX_SUFFIX = Pattern.compile("[0-9][0-9a-f]*h");
    private static final Pattern BIN_SUFFIX = Pattern.compile("[01]+b");
    private static final Pattern BIN_PREFIX = Pattern.compile("0b[01]+");
    private static final Pattern OCT_SUFFIX = Pattern.compile("[0-7]+[oq]");
    private static final Pattern OCT_PREFIX = Pattern.compile("0[oq][0-7]+");
    private static final Pattern DEC_SUFFIX = Pattern.compile("[0-9]+d?");
    private static final Pattern DEC_PREFIX = Pattern.compile("0d[0-9]+");

    private NumericLiteral() {
    }

    /** Whether {@code text} has the shape of a numeric literal (its value may still overflow 64 bits). */
    public static boolean isLiteral(String text) {
        return text != null && radix(text.trim().toLowerCase(Locale.ROOT)) != 0;
    }

    /** The unsigned 64-bit value of {@code text}, or {@code null} when it is not a literal or does not fit. */
    public static Long parse(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim().toLowerCase(Locale.ROOT);
        int radix = radix(s);
        if (radix == 0) {
            return null;
        }
        String digits = switch (radix) {
            case 16 -> s.endsWith("h") ? s.substring(0, s.length() - 1) : s.substring(2);
            case 2 -> s.endsWith("b") ? s.substring(0, s.length() - 1) : s.substring(2);
            case 8 -> OCT_SUFFIX.matcher(s).matches() ? s.substring(0, s.length() - 1) : s.substring(2);
            default -> DEC_PREFIX.matcher(s).matches() ? s.substring(2)
                    : s.endsWith("d") ? s.substring(0, s.length() - 1) : s;
        };
        try {
            return Long.parseUnsignedLong(digits, radix);
        } catch (NumberFormatException overflow) {
            return null;
        }
    }

    /** The radix of a lower-case literal, or 0 when it is not one. Suffix forms win, as in MASM ({@code 0bh} = 11). */
    private static int radix(String s) {
        if (s.isEmpty() || !Character.isDigit(s.charAt(0))) {
            return 0;
        }
        if (HEX_SUFFIX.matcher(s).matches() || HEX_PREFIX.matcher(s).matches()) {
            return 16;
        }
        if (BIN_SUFFIX.matcher(s).matches() || BIN_PREFIX.matcher(s).matches()) {
            return 2;
        }
        if (OCT_SUFFIX.matcher(s).matches() || OCT_PREFIX.matcher(s).matches()) {
            return 8;
        }
        if (DEC_SUFFIX.matcher(s).matches() || DEC_PREFIX.matcher(s).matches()) {
            return 10;
        }
        return 0;
    }
}
