package io.github.dinamo541.idearm.infrastructure.workspace;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * Moves files to the Windows Recycle Bin through {@code SHFileOperationW}, so a deletion from the explorer can be
 * undone, as in VS Code.
 *
 * <p>The shell is told never to destroy silently: if an entry cannot be recycled (a network drive, a file too
 * large for the bin) Windows asks the user instead of deleting it outright.
 */
final class WindowsRecycleBin {

    private static final int FO_DELETE = 3;
    private static final int FOF_SILENT = 0x0004;
    private static final int FOF_NOCONFIRMATION = 0x0010;
    private static final int FOF_ALLOWUNDO = 0x0040;
    private static final int FOF_NOERRORUI = 0x0400;
    private static final int FOF_WANTNUKEWARNING = 0x4000;

    // SHFILEOPSTRUCTW on x64 (8-byte packing): hwnd 0, wFunc 8, pFrom 16, pTo 24, fFlags 32 (WORD),
    // fAnyOperationsAborted 36 (BOOL), hNameMappings 40, lpszProgressTitle 48; 56 bytes in total.
    private static final long STRUCT_SIZE = 56;
    private static final long FUNCTION_OFFSET = 8;
    private static final long FROM_OFFSET = 16;
    private static final long FLAGS_OFFSET = 32;
    private static final long ABORTED_OFFSET = 36;

    private static final MethodHandle SH_FILE_OPERATION = lookup();

    private WindowsRecycleBin() {
    }

    static boolean available() {
        return SH_FILE_OPERATION != null;
    }

    static void moveToTrash(Path entry) throws IOException {
        if (SH_FILE_OPERATION == null) {
            throw new IOException("explorer.trash.unavailable");
        }
        String absolute = entry.toAbsolutePath().normalize().toString();
        try (Arena arena = Arena.ofConfined()) {
            // pFrom is a list of NUL-terminated paths ending in an extra NUL; arena memory starts zeroed.
            MemorySegment from = arena.allocate((absolute.length() + 2) * JAVA_CHAR.byteSize(), JAVA_CHAR.byteAlignment());
            for (int i = 0; i < absolute.length(); i++) {
                from.setAtIndex(JAVA_CHAR, i, absolute.charAt(i));
            }
            MemorySegment operation = arena.allocate(STRUCT_SIZE, ADDRESS.byteAlignment());
            operation.set(JAVA_INT, FUNCTION_OFFSET, FO_DELETE);
            operation.set(ADDRESS, FROM_OFFSET, from);
            operation.set(JAVA_SHORT, FLAGS_OFFSET,
                    (short) (FOF_ALLOWUNDO | FOF_NOCONFIRMATION | FOF_SILENT | FOF_NOERRORUI | FOF_WANTNUKEWARNING));

            int result = (int) SH_FILE_OPERATION.invokeExact(operation);
            boolean aborted = operation.get(JAVA_INT, ABORTED_OFFSET) != 0;
            if (result != 0 || aborted) {
                throw new IOException("explorer.trash.failed: SHFileOperationW returned " + result
                        + (aborted ? " (cancelled)" : ""));
            }
        } catch (IOException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IOException("explorer.trash.failed", failure);
        }
        if (Files.exists(entry, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("explorer.trash.failed: the entry is still there");
        }
    }

    private static MethodHandle lookup() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
        if (!windows || ADDRESS.byteSize() != 8) {
            return null;
        }
        try {
            SymbolLookup shell32 = SymbolLookup.libraryLookup("shell32", Arena.global());
            return shell32.find("SHFileOperationW")
                    .map(symbol -> Linker.nativeLinker().downcallHandle(symbol, FunctionDescriptor.of(JAVA_INT, ADDRESS)))
                    .orElse(null);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }
}
