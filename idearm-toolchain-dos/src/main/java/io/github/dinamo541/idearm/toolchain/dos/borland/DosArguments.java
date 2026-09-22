package io.github.dinamo541.idearm.toolchain.dos.borland;

/** Delegates to shared DosArguments. */
final class DosArguments {
    private DosArguments() {}

    static String path(char drive, String logical) {
        return io.github.dinamo541.idearm.toolchain.dos.DosArguments.path(drive, logical);
    }
}
