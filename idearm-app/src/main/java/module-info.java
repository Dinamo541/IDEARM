module io.github.dinamo541.idearm.app {
    requires io.github.dinamo541.idearm.domain;
    requires io.github.dinamo541.idearm.language;
    requires io.github.dinamo541.idearm.application;
    requires io.github.dinamo541.idearm.infrastructure;
    requires io.github.dinamo541.idearm.toolchain.dos;
    requires io.github.dinamo541.idearm.emu8086;
    requires io.github.dinamo541.idearm.toolchain.nasm;
    requires javafx.controls;
    requires atlantafx.base;
    requires org.fxmisc.richtext;
    requires org.fxmisc.flowless;
    requires reactfx;
    requires org.fxmisc.undo;
    requires wellbehavedfx;

    uses io.github.dinamo541.idearm.domain.port.ToolchainProvider;
    uses io.github.dinamo541.idearm.domain.port.ExecutionEnvironmentProvider;
    uses io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider;
    uses io.github.dinamo541.idearm.domain.port.BreakpointStore;
    uses io.github.dinamo541.idearm.domain.port.DistPackager;
    uses io.github.dinamo541.idearm.domain.port.ProcessLauncher;
    uses io.github.dinamo541.idearm.domain.port.TerminalRunner;

    exports io.github.dinamo541.idearm.app;
    exports io.github.dinamo541.idearm.app.bootstrap;
    exports io.github.dinamo541.idearm.app.editor;
    exports io.github.dinamo541.idearm.app.viewmodel;
    exports io.github.dinamo541.idearm.app.view;
    exports io.github.dinamo541.idearm.app.i18n;
}
