package io.github.dinamo541.idearm.domain.model;

import java.util.Objects;

/**
 * Which debugger a project uses, as stored in {@code [debug] backend}.
 */
public record DebugConfiguration(String backend) {

    /**
     * The built-in 8086 emulator. The default for DOS projects: it needs no proprietary tool, and stepping,
     * registers, flags, memory and watches all work inside the IDE.
     */
    public static final String EMULATOR = "emu8086";

    /** The name the emulator was also accepted under before it became the default. */
    public static final String EMULATOR_ALIAS = "internal";

    /**
     * Turbo Debugger or CodeView inside a DOSBox window. The debugging happens in that window, so the IDE cannot
     * step, read registers or apply its own breakpoints.
     */
    public static final String EXTERNAL = "external";

    /** GDB, used for 32-bit and 64-bit native targets. */
    public static final String GDB = "gdb";

    public DebugConfiguration {
        Objects.requireNonNull(backend);
    }

    /** Whether this project asks for the built-in emulator, under either of its names. */
    public boolean usesEmulator() {
        return EMULATOR.equalsIgnoreCase(backend) || EMULATOR_ALIAS.equalsIgnoreCase(backend);
    }
}
