package io.github.dinamo541.idearm.toolchain.nasm;

import io.github.dinamo541.idearm.domain.model.HostKind;
import java.nio.file.Path;
import java.util.Locale;

/** Facts about the machine the native tools run on. */
final class NativeHost {

    private NativeHost() {
    }

    static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    static HostKind kind() {
        return windows() ? HostKind.WIN64 : HostKind.LINUX_ELF;
    }

    /**
     * The Windows folder holding the system DLLs a program of the given width imports. GNU ld can link straight
     * against a DLL, so a program only needs the linker, not a MinGW import library of the right width.
     */
    static Path systemLibraries(boolean wide) {
        String root = System.getenv("SystemRoot");
        Path windows = Path.of(root == null || root.isBlank() ? "C:\\Windows" : root);
        return windows.resolve(wide ? "System32" : "SysWOW64");
    }
}
