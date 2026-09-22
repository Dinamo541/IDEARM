module io.github.dinamo541.idearm.toolchain.nasm {
    requires io.github.dinamo541.idearm.domain;

    exports io.github.dinamo541.idearm.toolchain.nasm;

    uses io.github.dinamo541.idearm.domain.port.ToolchainProvider;

    provides io.github.dinamo541.idearm.domain.port.ToolchainProvider
        with io.github.dinamo541.idearm.toolchain.nasm.NasmToolchainProvider;
    provides io.github.dinamo541.idearm.domain.port.DiagnosticParser
        with io.github.dinamo541.idearm.toolchain.nasm.NasmDiagnosticParser,
             io.github.dinamo541.idearm.toolchain.nasm.GnuLinkerDiagnosticParser;
}
