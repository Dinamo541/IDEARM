module io.github.dinamo541.idearm.infrastructure {
    requires io.github.dinamo541.idearm.domain;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.toml;
    // com.sun.nio.file.ExtendedWatchEventModifier: one recursive watch per project on Windows.
    requires jdk.unsupported;

    exports io.github.dinamo541.idearm.infrastructure.persistence;
    exports io.github.dinamo541.idearm.infrastructure.process;
    exports io.github.dinamo541.idearm.infrastructure.workspace;
    exports io.github.dinamo541.idearm.infrastructure.tools;

    // Program runs pick the registered launcher, so the Job Object can be swapped in tests.
    uses io.github.dinamo541.idearm.domain.port.ProcessLauncher;

    provides io.github.dinamo541.idearm.domain.port.ProcessLauncher
        with io.github.dinamo541.idearm.infrastructure.process.JobProcessLauncher;
    provides io.github.dinamo541.idearm.domain.port.BreakpointStore
        with io.github.dinamo541.idearm.infrastructure.persistence.FileBreakpointStore;
    provides io.github.dinamo541.idearm.domain.port.TerminalRunner
        with io.github.dinamo541.idearm.infrastructure.process.HostTerminalRunner;
    provides io.github.dinamo541.idearm.domain.port.ExecutionEnvironmentProvider
        with io.github.dinamo541.idearm.infrastructure.process.HostExecutionEnvironmentProvider;
    provides io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider
        with io.github.dinamo541.idearm.infrastructure.process.GdbDebugEnvironmentProvider;
    provides io.github.dinamo541.idearm.domain.port.DistPackager
        with io.github.dinamo541.idearm.infrastructure.workspace.NativeDistPackager;
}
