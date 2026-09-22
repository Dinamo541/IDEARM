package io.github.dinamo541.idearm.infrastructure.tools;

import io.github.dinamo541.idearm.domain.model.HostKind;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspects executable binaries to detect their host kind and version string without running them.
 * This is crucial because 16-bit DOS binaries cannot execute directly on 64-bit Windows hosts.
 */
public final class BinaryScanner {

    private static final int MAX_SCAN_BYTES = 512 * 1024;
    private static final int MAX_RESOURCE_SCAN_BYTES = 4 * 1024 * 1024;
    private static final Pattern PRODUCT_VERSION = Pattern.compile("\\d+(?:[.,]\\d+)+");

    private static final Pattern TASM_VERSION = Pattern.compile("(?i)Turbo\\s+Assembler\\s+Version\\s+([0-9.]+)");
    private static final Pattern TLINK_VERSION = Pattern.compile("(?i)Turbo\\s+Link\\s+Version\\s+([0-9.]+)");
    private static final Pattern ML_VERSION = Pattern.compile("(?i)Macro\\s+Assembler\\s+Version\\s+([0-9.]+)");
    private static final Pattern LINK_VERSION = Pattern.compile("(?i)Linker\\s+Version\\s+([0-9.]+)");
    private static final Pattern DOSBOX_VERSION = Pattern.compile("(?i)DOSBox(?:-X|-staging)?(?:\\s+version)?\\s+([0-9.]+)");

    private BinaryScanner() {}

    /**
     * Inspects the binary headers to determine what environment is required to host the tool.
     */
    public static HostKind detectHostKind(Path executable) throws IOException {
        long size = Files.size(executable);
        if (size < 64) {
            return HostKind.DOS_REAL;
        }

        try (var raf = new RandomAccessFile(executable.toFile(), "r")) {
            byte[] magic = new byte[2];
            raf.readFully(magic);

            // Check for MZ header ('M', 'Z')
            if (magic[0] == 0x4D && magic[1] == 0x5A) {
                // Offset 0x3C points to the PE / NE / LE header
                raf.seek(0x3C);
                int lfanew = Integer.reverseBytes(raf.readInt());
                if (lfanew >= 64 && lfanew + 4 <= size) {
                    raf.seek(lfanew);
                    byte[] subMagic = new byte[4];
                    raf.readFully(subMagic);

                    // PE00
                    if (subMagic[0] == 'P' && subMagic[1] == 'E' && subMagic[2] == 0 && subMagic[3] == 0) {
                        raf.seek(lfanew + 4);
                        int machine = Short.reverseBytes(raf.readShort()) & 0xFFFF;
                        if (machine == 0x8664 || machine == 0xAA64) {
                            return HostKind.WIN64;
                        }
                        return HostKind.WIN32_CONSOLE;
                    }

                    // NE (New Executable) or LE / LX -> 16-bit / 32-bit DOS DPMI
                    if ((subMagic[0] == 'N' && subMagic[1] == 'E') ||
                        (subMagic[0] == 'L' && (subMagic[1] == 'E' || subMagic[1] == 'X'))) {
                        return HostKind.DOS_DPMI;
                    }
                }
                return HostKind.DOS_REAL;
            }

            // ELF signature: 0x7F 'E' 'L' 'F'
            if (magic[0] == 0x7F && magic[1] == 'E') {
                return HostKind.LINUX_ELF;
            }

            return HostKind.DOS_REAL;
        }
    }

    /**
     * Extracts the version string from the binary by regex matching within printable ASCII sequences.
     */
    public static Optional<String> scanVersion(Path executable, String toolHint) throws IOException {
        String content = readAsciiSample(executable);
        Pattern pattern = switch (toolHint.toLowerCase()) {
            case "tasm" -> TASM_VERSION;
            case "tlink" -> TLINK_VERSION;
            case "ml", "masm" -> ML_VERSION;
            case "link" -> LINK_VERSION;
            case "dosbox", "dosbox-x", "dosbox-staging", "dosbox-0.74" -> DOSBOX_VERSION;
            default -> null;
        };

        if (pattern != null) {
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }

        // Try generic scan across all known tool patterns
        for (Pattern fallback : new Pattern[]{TASM_VERSION, TLINK_VERSION, ML_VERSION, LINK_VERSION, DOSBOX_VERSION}) {
            Matcher matcher = fallback.matcher(content);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }

        return Optional.empty();
    }

    /**
     * The ProductVersion a Windows program declares in its version resource, read without running it. The
     * resource section usually closes the file, so only its tail is searched.
     */
    public static Optional<String> productVersion(Path executable) throws IOException {
        long size = Files.size(executable);
        int length = (int) Math.min(size, MAX_RESOURCE_SCAN_BYTES);
        byte[] tail = new byte[length];
        try (var raf = new RandomAccessFile(executable.toFile(), "r")) {
            raf.seek(size - length);
            raf.readFully(tail);
        }
        byte[] key = "ProductVersion".getBytes(StandardCharsets.UTF_16LE);
        for (int at = lastIndexOf(tail, key); at >= 0; at = at > 0 ? lastIndexOf(tail, key, at - 1) : -1) {
            // The value follows the key's terminating null and padding, as UTF-16LE text ending in a null.
            int i = at + key.length;
            while (i + 1 < tail.length && tail[i] == 0 && tail[i + 1] == 0) {
                i += 2;
            }
            var value = new StringBuilder();
            while (i + 1 < tail.length && (tail[i] != 0 || tail[i + 1] != 0) && value.length() < 40) {
                value.append((char) ((tail[i] & 0xFF) | (tail[i + 1] & 0xFF) << 8));
                i += 2;
            }
            Matcher version = PRODUCT_VERSION.matcher(value);
            if (version.lookingAt()) {
                return Optional.of(version.group().replace(',', '.'));
            }
        }
        return Optional.empty();
    }

    private static int lastIndexOf(byte[] data, byte[] key) {
        return lastIndexOf(data, key, data.length - key.length);
    }

    private static int lastIndexOf(byte[] data, byte[] key, int from) {
        outer:
        for (int i = Math.min(from, data.length - key.length); i >= 0; i--) {
            for (int j = 0; j < key.length; j++) {
                if (data[i + j] != key[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /** Which DOSBox a program named {@code dosbox} is, and its version, from the text inside the binary. */
    public record DosBoxIdentity(String dialect, String version) {
    }

    private static final int MAX_IDENTITY_SCAN_BYTES = 64 * 1024 * 1024;
    private static final Pattern STAGING_TEXT = Pattern.compile("dosbox-staging (\\d+(?:\\.\\d+)+)");
    private static final Pattern CLASSIC_TEXT = Pattern.compile("DOSBox (0\\.7\\d(?:-\\d+)?)");

    /**
     * DOSBox 0.74-3 and DOSBox Staging both ship as {@code dosbox}/{@code dosbox.exe}. Each binary names itself
     * ("DOSBox 0.74-3", "dosbox-staging 0.82.2"), but beyond the part {@link #scanVersion} reads (S7), so the whole
     * file is searched here.
     */
    public static Optional<DosBoxIdentity> identifyDosBox(Path executable) throws IOException {
        String content = readAsciiSample(executable, MAX_IDENTITY_SCAN_BYTES);
        Matcher staging = STAGING_TEXT.matcher(content);
        if (staging.find()) {
            return Optional.of(new DosBoxIdentity("dosbox-staging", staging.group(1)));
        }
        Matcher classic = CLASSIC_TEXT.matcher(content);
        if (classic.find()) {
            return Optional.of(new DosBoxIdentity("dosbox-0.74", classic.group(1)));
        }
        return Optional.empty();
    }

    /**
     * Calculates the SHA-256 checksum of an executable file.
     */
    public static String computeSha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file);
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String readAsciiSample(Path file) throws IOException {
        return readAsciiSample(file, MAX_SCAN_BYTES);
    }

    private static String readAsciiSample(Path file, int maxBytes) throws IOException {
        long size = Math.min(Files.size(file), maxBytes);
        try (var channel = Files.newByteChannel(file)) {
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate((int) size);
            // One read may return less than asked for; a large binary is read until the sample is full.
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                // keep reading
            }
            byte[] array = buffer.array();
            // Convert to US-ASCII printable strings
            char[] chars = new char[array.length];
            for (int i = 0; i < array.length; i++) {
                int b = array[i] & 0xFF;
                chars[i] = (b >= 32 && b <= 126) ? (char) b : ' ';
            }
            return new String(chars);
        }
    }
}
