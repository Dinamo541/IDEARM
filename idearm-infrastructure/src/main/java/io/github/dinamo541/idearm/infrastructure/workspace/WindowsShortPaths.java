package io.github.dinamo541.idearm.infrastructure.workspace;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * The 8.3 short name Windows keeps for an existing path ({@code C:\Users\JUANPE~1\...}), through
 * {@code GetShortPathNameW}. Short names are ASCII without spaces, which DOSBox can mount; a volume may have them
 * turned off, in which case the long path comes back unchanged.
 */
final class WindowsShortPaths {

    private static final MethodHandle GET_SHORT_PATH_NAME = lookup();

    private WindowsShortPaths() {
    }

    static Optional<String> shortName(Path existing) {
        if (GET_SHORT_PATH_NAME == null) {
            return Optional.empty();
        }
        String path = existing.toAbsolutePath().normalize().toString();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment longPath = wide(arena, path);
            int capacity = 1024;
            MemorySegment buffer = arena.allocate(capacity * JAVA_CHAR.byteSize(), JAVA_CHAR.byteAlignment());
            int length = (int) GET_SHORT_PATH_NAME.invokeExact(longPath, buffer, capacity);
            if (length <= 0 || length >= capacity) {
                return Optional.empty();
            }
            var result = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                result.append(buffer.getAtIndex(JAVA_CHAR, i));
            }
            return Optional.of(result.toString());
        } catch (Throwable unavailable) {
            return Optional.empty();
        }
    }

    private static MemorySegment wide(Arena arena, String text) {
        // A NUL-terminated UTF-16 string; arena memory starts zeroed.
        MemorySegment segment = arena.allocate((text.length() + 1) * JAVA_CHAR.byteSize(), JAVA_CHAR.byteAlignment());
        for (int i = 0; i < text.length(); i++) {
            segment.setAtIndex(JAVA_CHAR, i, text.charAt(i));
        }
        return segment;
    }

    private static MethodHandle lookup() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
        if (!windows) {
            return null;
        }
        try {
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
            return kernel32.find("GetShortPathNameW")
                    .map(symbol -> Linker.nativeLinker().downcallHandle(symbol,
                            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT)))
                    .orElse(null);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }
}
