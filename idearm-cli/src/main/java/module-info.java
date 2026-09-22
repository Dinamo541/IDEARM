module io.github.dinamo541.idearm.cli {
    requires io.github.dinamo541.idearm.domain;
    requires io.github.dinamo541.idearm.language;
    requires io.github.dinamo541.idearm.application;
    requires io.github.dinamo541.idearm.infrastructure;
    requires io.github.dinamo541.idearm.toolchain.dos;

    uses io.github.dinamo541.idearm.domain.port.ToolchainProvider;
    uses io.github.dinamo541.idearm.domain.port.ExecutionEnvironmentProvider;
    uses io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider;
    uses io.github.dinamo541.idearm.domain.port.DistPackager;

    exports io.github.dinamo541.idearm.cli;
}
