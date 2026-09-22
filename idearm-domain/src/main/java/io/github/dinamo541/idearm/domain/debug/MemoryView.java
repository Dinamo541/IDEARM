package io.github.dinamo541.idearm.domain.debug;

import java.util.HexFormat;
import java.util.Objects;

/**
 * A slice of the debugged program's memory: at a real-mode {@code segment:offset}, or, for a 32/64-bit program, at
 * a flat {@code address} ({@link #SEGMENTED} marks the first kind).
 */
public record MemoryView(int segment, int offset, byte[] bytes, long address) {

    /** The {@code address} of a segmented view. */
    public static final long SEGMENTED = -1L;

    public MemoryView {
        segment &= 0xFFFF;
        offset &= 0xFFFF;
        Objects.requireNonNull(bytes, "bytes cannot be null");
        bytes = bytes.clone();
    }

    public MemoryView(int segment, int offset, byte[] bytes) {
        this(segment, offset, bytes, SEGMENTED);
    }

    /** Memory of a 32/64-bit program, which has one flat address space. */
    public static MemoryView flat(long address, byte[] bytes) {
        return new MemoryView(0, 0, bytes, address);
    }

    public boolean isFlat() {
        return address != SEGMENTED;
    }

    /** 20-bit physical/linear address calculated as (segment * 16) + offset. */
    public int linearAddress() {
        return ((segment & 0xFFFF) << 4) + (offset & 0xFFFF);
    }

    public int length() {
        return bytes.length;
    }

    public byte getByte(int index) {
        return bytes[index];
    }

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    /** Formats a 16-byte row starting at relative rowIndex * 16 into hexadecimal string. */
    public String formatHexRow(int rowIndex) {
        int start = rowIndex * 16;
        if (start >= bytes.length) return "";
        int end = Math.min(start + 16, bytes.length);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + 16; i++) {
            if (i < end) {
                sb.append(HEX.toHighHexDigit(bytes[i]))
                  .append(HEX.toLowHexDigit(bytes[i]));
            } else {
                sb.append("  ");
            }
            if (i < start + 15) {
                sb.append(i % 8 == 7 ? "  " : " ");
            }
        }
        return sb.toString();
    }

    /** Formats a 16-byte row into printable ASCII string (dots for non-printable chars). */
    public String formatAsciiRow(int rowIndex) {
        int start = rowIndex * 16;
        if (start >= bytes.length) return "";
        int end = Math.min(start + 16, bytes.length);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            byte b = bytes[i];
            if (b >= 32 && b <= 126) {
                sb.append((char) b);
            } else {
                sb.append('.');
            }
        }
        return sb.toString();
    }

    public String toHexDump() {
        int rows = (bytes.length + 15) / 16;
        StringBuilder sb = new StringBuilder();
        // 32-bit programs read better with 8 digits; a 64-bit address needs all 16.
        String flatFormat = (address + bytes.length) > 0xFFFF_FFFFL ? "%016X  %-48s  |%s|\n" : "%08X  %-48s  |%s|\n";
        for (int r = 0; r < rows; r++) {
            if (isFlat()) {
                sb.append(String.format(flatFormat, address + r * 16L, formatHexRow(r), formatAsciiRow(r)));
            } else {
                int rowOffset = (offset + r * 16) & 0xFFFF;
                sb.append(String.format("%04X:%04X  %-48s  |%s|\n",
                        segment, rowOffset, formatHexRow(r), formatAsciiRow(r)));
            }
        }
        return sb.toString();
    }
}
