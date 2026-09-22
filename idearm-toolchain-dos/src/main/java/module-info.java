module io.github.dinamo541.idearm.toolchain.dos {
    requires io.github.dinamo541.idearm.domain;
    exports io.github.dinamo541.idearm.toolchain.dos;
    exports io.github.dinamo541.idearm.toolchain.dos.borland;
    exports io.github.dinamo541.idearm.toolchain.dos.microsoft;
    exports io.github.dinamo541.idearm.toolchain.dos.execution;
    exports io.github.dinamo541.idearm.toolchain.dos.dist;

    uses io.github.dinamo541.idearm.domain.port.ToolchainProvider;
    uses io.github.dinamo541.idearm.domain.port.ProcessLauncher;

    provides io.github.dinamo541.idearm.domain.port.ToolchainProvider
        with io.github.dinamo541.idearm.toolchain.dos.borland.BorlandToolchainProvider,
             io.github.dinamo541.idearm.toolchain.dos.microsoft.Microsoft16ToolchainProvider;
    provides io.github.dinamo541.idearm.domain.port.ExecutionEnvironmentProvider
        with io.github.dinamo541.idearm.toolchain.dos.execution.DosBoxExecutionEnvironmentProvider;
    provides io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider
        with io.github.dinamo541.idearm.toolchain.dos.execution.DosBoxDebugEnvironmentProvider;
    provides io.github.dinamo541.idearm.domain.port.DistPackager
        with io.github.dinamo541.idearm.toolchain.dos.dist.DosDistPackager;
}
