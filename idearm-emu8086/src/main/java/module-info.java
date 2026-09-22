module io.github.dinamo541.idearm.emu8086 {
    requires io.github.dinamo541.idearm.domain;

    exports io.github.dinamo541.idearm.emu8086.cpu;
    exports io.github.dinamo541.idearm.emu8086.loader;
    exports io.github.dinamo541.idearm.emu8086.dos;
    exports io.github.dinamo541.idearm.emu8086.debug;

    provides io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider
        with io.github.dinamo541.idearm.emu8086.debug.Emu8086DebugEnvironmentProvider;
}
