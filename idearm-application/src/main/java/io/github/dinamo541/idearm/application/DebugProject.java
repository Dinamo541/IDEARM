package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.BuildPlanner;
import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.DebugEvent;
import io.github.dinamo541.idearm.domain.debug.DebugLaunchSpec;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.execution.DosBoxDialects;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.RunSettings;
import io.github.dinamo541.idearm.domain.execution.SessionState;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.model.TargetProfileCatalog;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.domain.port.BreakpointStore;
import io.github.dinamo541.idearm.domain.port.BuildWorkspace;
import io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider;
import io.github.dinamo541.idearm.domain.port.DebugSession;
import io.github.dinamo541.idearm.domain.port.ProjectRepository;
import io.github.dinamo541.idearm.domain.port.ToolRegistry;
import io.github.dinamo541.idearm.domain.port.ToolchainProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Orchestrates launching an interactive or emulated debugging session for a project.
 */
public final class DebugProject {

    private final ProjectRepository projects;
    private final ToolRegistry tools;
    private final List<DebugEnvironmentProvider> environments;
    private final List<ToolchainProvider> toolchains;
    private final BuildWorkspace workspace;
    private final BuildProject builder;
    private final BreakpointStore breakpointStore;
    private final Path stagingRoot;

    public DebugProject(ProjectRepository projects,
                        ToolRegistry tools,
                        List<DebugEnvironmentProvider> environments,
                        List<ToolchainProvider> toolchains,
                        BuildWorkspace workspace,
                        BuildProject builder,
                        BreakpointStore breakpointStore,
                        Path stagingRoot) {
        this.projects = Objects.requireNonNull(projects, "projects cannot be null");
        this.tools = Objects.requireNonNull(tools, "tools cannot be null");
        this.environments = List.copyOf(environments);
        this.toolchains = List.copyOf(toolchains);
        this.workspace = Objects.requireNonNull(workspace, "workspace cannot be null");
        this.builder = Objects.requireNonNull(builder, "builder cannot be null");
        this.breakpointStore = Objects.requireNonNull(breakpointStore, "breakpointStore cannot be null");
        this.stagingRoot = Objects.requireNonNull(stagingRoot, "stagingRoot cannot be null");
    }

    public DebugResult execute(Path projectRoot) {
        return execute(projectRoot, CancellationToken.NONE, event -> {});
    }

    public DebugResult execute(Path projectRoot,
                               CancellationToken cancellation,
                               Consumer<DebugEvent> events) {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        Objects.requireNonNull(cancellation, "cancellation cannot be null");
        Objects.requireNonNull(events, "events cannot be null");

        Path root = projectRoot.toAbsolutePath().normalize();

        try (var lock = workspace.lock(root)) {
            cancellation.throwIfCancelled();
            Project project = SourceSet.expand(projects.load(root), root);

            FreshBuild.Outcome build = FreshBuild.ensure(root, project, "debug", toolchains, builder, cancellation,
                    event -> {
                        if (event instanceof BuildEvent.Output output && !output.text().isBlank()) {
                            events.accept(new DebugEvent.Output(output.text()));
                        }
                    });
            if (build.failed()) {
                return DebugResult.failed(build.failedBuild().diagnostics(), build.failedBuild().output());
            }
            Path executable = build.executable();

            // Resolve execution environment (gdb, emu8086, or DOSBox)
            TargetProfile profile = TargetProfileCatalog.require(project.target().profile());
            // Arguments reach the program's command tail and the generated batch files, so they are checked first.
            List<Diagnostic> settings = RunSettings.problems(project.run(), profile);
            if (!settings.isEmpty()) {
                return DebugResult.failed(settings, settings.getFirst().message());
            }
            // GDB debugs native programs; a DOS program is debugged in DOSBox or in the built-in emulator.
            boolean useGdb = !profile.isDos();
            boolean useEmu = !useGdb && project.debug().usesEmulator();

            DebugEnvironmentProvider envProvider;
            ToolInstallation envTool;
            ToolInstallation debuggerTool;
            String debuggerKind;

            if (useGdb) {
                envProvider = environments.stream()
                        .filter(env -> env.id().equalsIgnoreCase("gdb") && env.supports(profile))
                        .findFirst()
                        .orElseThrow(() -> new DomainException("debug.environment.unavailable",
                                "No GDB debug execution environment supports the " + profile.id() + " target.", profile.id()));
                debuggerTool = tools.find("gdb")
                        .orElseThrow(() -> new DomainException("debug.gdb.missing",
                                "GDB (the GNU Debugger) was not found. Install it, for example with MSYS2: "
                                        + "pacman -S mingw-w64-ucrt-x86_64-gdb"));
                envTool = debuggerTool;
                debuggerKind = "gdb";
            } else if (useEmu) {
                var emuEnvs = environments.stream()
                        .filter(env -> env.id().equalsIgnoreCase("emu8086"))
                        .toList();
                if (emuEnvs.isEmpty()) {
                    throw new DomainException("debug.environment.unavailable",
                            "No emu8086 debug execution environment found.", profile.id());
                }
                envProvider = emuEnvs.getFirst();
                envTool = new ToolInstallation("emu8086", "1.0", Path.of("emu8086"), HostKind.WIN32_CONSOLE, Map.of());
                debuggerTool = new ToolInstallation("emu8086-debug", "1.0", Path.of("emu8086"), HostKind.WIN32_CONSOLE, Map.of());
                debuggerKind = "emu8086";
            } else {
                var matchingEnvs = environments.stream()
                        .filter(env -> env.id().equalsIgnoreCase("dosbox"))
                        .toList();
                if (matchingEnvs.isEmpty()) {
                    var emuFallback = environments.stream()
                            .filter(env -> env.id().equalsIgnoreCase("emu8086"))
                            .findFirst();
                    if (emuFallback.isPresent()) {
                        envProvider = emuFallback.get();
                        envTool = new ToolInstallation("emu8086", "1.0", Path.of("emu8086"), HostKind.WIN32_CONSOLE, Map.of());
                        debuggerTool = new ToolInstallation("emu8086-debug", "1.0", Path.of("emu8086"), HostKind.WIN32_CONSOLE, Map.of());
                        debuggerKind = "emu8086";
                    } else {
                        throw new DomainException("debug.environment.unavailable",
                                "No DOSBox debug execution environment found.", profile.id());
                    }
                } else {
                    envProvider = matchingEnvs.getFirst();
                    // Debug in the DOSBox dialect the project selected (e.g. "dosbox-x"), falling back to the
                    // automatic order (0.74-3 first) when that specific one is not installed on this machine.
                    String envId = project.run().environment();
                    envTool = (DosBoxDialects.isDosBox(envId) ? tools.find(envId) : Optional.<ToolInstallation>empty())
                            .or(() -> tools.find(DosBoxDialects.AUTO))
                            .orElseThrow(() -> new DomainException("debug.emulator.missing",
                                    "No DOSBox was found. Install DOSBox 0.74-3, DOSBox-X or DOSBox Staging."));

                    // Resolve debugger tool (TD.EXE for TASM, CV.EXE for MASM)
                    String toolchainId = project.toolchain().id().toLowerCase();
                    if (toolchainId.contains("borland") || toolchainId.contains("tasm")) {
                        debuggerKind = "td";
                        debuggerTool = tools.find("td")
                                .or(() -> tools.find("turbo-debugger"))
                                .or(() -> tools.find("cv"))
                                .orElseThrow(() -> new DomainException("debug.tool.missing",
                                        "Turbo Debugger (TD.EXE) was not found in registered tool paths. Register it, "
                                                + "or choose the built-in 8086 emulator in Project Properties, which "
                                                + "needs no extra tool.",
                                        "Turbo Debugger", "TD.EXE", "TASM"));
                    } else {
                        debuggerKind = "cv";
                        debuggerTool = tools.find("cv")
                                .or(() -> tools.find("codeview"))
                                .or(() -> tools.find("td"))
                                .orElseThrow(() -> new DomainException("debug.tool.missing",
                                        "Microsoft CodeView (CV.EXE) was not found in registered tool paths. Register it, "
                                                + "or choose the built-in 8086 emulator in Project Properties, which "
                                                + "needs no extra tool.",
                                        "CodeView", "CV.EXE", "MASM"));
                    }
                }
            }

            // Gather active breakpoints
            List<Breakpoint> breakpoints = breakpointStore.loadBreakpoints(root).stream()
                    .filter(Breakpoint::enabled)
                    .toList();

            // Validate and collect resources
            List<Path> resources = new ArrayList<>();
            for (String relResource : project.resources().files()) {
                Path resPath = root.resolve(relResource).normalize();
                if (!resPath.startsWith(root)) {
                    throw new DomainException("run.resource.traversal",
                            "Resource path attempts directory traversal: " + relResource, relResource);
                }
                if (!Files.exists(resPath)) {
                    throw new DomainException("run.resource.missing",
                            "Declared runtime resource does not exist: " + relResource, relResource);
                }
                resources.add(resPath);
            }

            var spec = new DebugLaunchSpec(
                    executable,
                    root,
                    resources,
                    project.run().args(),
                    envTool,
                    debuggerTool,
                    breakpoints,
                    stagingRoot,
                    debuggerKind,
                    build.listings()
            );

            DebugLifecycleCoordinator coordinator = new DebugLifecycleCoordinator(events);
            coordinator.emit(new DebugEvent.Started(root, debuggerTool.toolId()));

            DebugSession session = null;
            try {
                session = envProvider.launchDebug(spec, coordinator::onSessionEvent);
            } catch (Throwable launchEx) {
                if (cancellation.cancelled()
                        || (launchEx instanceof DomainException dex && "task.cancelled".equals(dex.code()))) {
                    ExitInfo stoppedInfo = ExitInfo.stopped(Duration.ZERO);
                    coordinator.ensureTerminal(true, stoppedInfo);
                    return DebugResult.stopped(stoppedInfo, "Debug was cancelled.");
                }
                String msg = launchEx.getMessage() != null && !launchEx.getMessage().isBlank()
                        ? launchEx.getMessage() : launchEx.getClass().getSimpleName();
                Diagnostic diag = (launchEx instanceof DomainException dex)
                        ? new Diagnostic(Severity.ERROR, dex.code(), dex.getMessage(), null, "debugger", dex.getMessage(), dex.arguments())
                        : new Diagnostic(Severity.ERROR, "debug.launch.failed", "Debug launch failed: " + msg, null, "debugger", msg, List.of(msg));
                ExitInfo errorInfo = ExitInfo.error(1, Duration.ZERO, "Debug launch failed: " + msg);
                coordinator.emit(new DebugEvent.Problem(diag));
                coordinator.ensureTerminal(false, errorInfo);
                return new DebugResult(SessionState.FAILED, errorInfo, List.of(diag), msg);
            }

            coordinator.emit(new DebugEvent.SessionAttached(session));

            boolean cancelledWhileRunning = false;
            boolean interrupted = false;

            try {
                while (!session.exit().isDone()) {
                    if (cancellation.cancelled()) {
                        cancelledWhileRunning = true;
                        session.stop();
                        break;
                    }
                    try {
                        session.exit().get(20, TimeUnit.MILLISECONDS);
                        break;
                    } catch (TimeoutException te) {
                        // Quantum elapsed; re-check cancellation in next iteration
                    } catch (InterruptedException ie) {
                        interrupted = true;
                        session.stop();
                        Thread.currentThread().interrupt();
                        break;
                    } catch (ExecutionException ee) {
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }

            Throwable exitFailure = null;
            ExitInfo info = null;
            if (session.exit().isCompletedExceptionally()) {
                try {
                    session.exit().getNow(null);
                } catch (CompletionException ex) {
                    exitFailure = ex.getCause() != null ? ex.getCause() : ex;
                } catch (Exception ex) {
                    exitFailure = ex;
                }
            } else if (session.exit().isDone()) {
                try {
                    info = session.exit().getNow(null);
                } catch (CompletionException ex) {
                    exitFailure = ex.getCause() != null ? ex.getCause() : ex;
                } catch (Exception ex) {
                    exitFailure = ex;
                }
            }

            Throwable closeFailure = null;
            try {
                session.close();
            } catch (Throwable closeEx) {
                closeFailure = closeEx;
            }

            if (cancelledWhileRunning || interrupted) {
                ExitInfo stoppedInfo = (info != null && info.wasStopped())
                        ? info
                        : ExitInfo.stopped(info != null ? info.duration() : Duration.ZERO);
                if (closeFailure != null) {
                    String closeMsg = closeFailure.getMessage() != null && !closeFailure.getMessage().isBlank()
                            ? closeFailure.getMessage() : closeFailure.getClass().getSimpleName();
                    Diagnostic closeDiag = new Diagnostic(Severity.ERROR, "debug.close.failed",
                            "Debug session cleanup failed: " + closeMsg, null, "debugger", closeMsg, List.of(closeMsg));
                    coordinator.emit(new DebugEvent.Problem(closeDiag));
                }
                coordinator.ensureTerminal(true, stoppedInfo);
                return DebugResult.stopped(stoppedInfo, "Debug session stopped.");
            }

            if (exitFailure != null) {
                String errorMsg = exitFailure.getMessage() != null && !exitFailure.getMessage().isBlank()
                        ? exitFailure.getMessage() : exitFailure.getClass().getSimpleName();
                Diagnostic diag = new Diagnostic(Severity.ERROR, "debug.session.failed",
                        "Debug session failed: " + errorMsg, null, "debugger", errorMsg, List.of(errorMsg));
                List<Diagnostic> diags = new ArrayList<>();
                diags.add(diag);
                if (closeFailure != null) {
                    String closeMsg = closeFailure.getMessage() != null && !closeFailure.getMessage().isBlank()
                            ? closeFailure.getMessage() : closeFailure.getClass().getSimpleName();
                    diags.add(new Diagnostic(Severity.ERROR, "debug.close.failed",
                            "Debug session cleanup failed: " + closeMsg, null, "debugger", closeMsg, List.of(closeMsg)));
                }
                ExitInfo errorInfo = ExitInfo.error(1, Duration.ZERO, "Debug session failed: " + errorMsg);
                coordinator.emit(new DebugEvent.Problem(diag));
                coordinator.ensureTerminal(false, errorInfo);
                return new DebugResult(SessionState.FAILED, errorInfo, diags, errorMsg);
            }

            if (closeFailure != null) {
                String closeMsg = closeFailure.getMessage() != null && !closeFailure.getMessage().isBlank()
                        ? closeFailure.getMessage() : closeFailure.getClass().getSimpleName();
                Diagnostic closeDiag = new Diagnostic(Severity.ERROR, "debug.close.failed",
                        "Debug session cleanup failed: " + closeMsg, null, "debugger", closeMsg, List.of(closeMsg));
                coordinator.emit(new DebugEvent.Problem(closeDiag));
                ExitInfo errorInfo = ExitInfo.error(1, info != null ? info.duration() : Duration.ZERO,
                        "Debug session cleanup failed: " + closeMsg);
                coordinator.ensureTerminal(false, errorInfo);
                return new DebugResult(SessionState.FAILED, errorInfo, List.of(closeDiag), closeMsg);
            }

            boolean wasStopped = session.state() == SessionState.STOPPED
                    || (info != null && info.wasStopped());

            if (wasStopped) {
                ExitInfo stoppedInfo = (info != null && info.wasStopped())
                        ? info
                        : ExitInfo.stopped(info != null ? info.duration() : Duration.ZERO);
                coordinator.ensureTerminal(true, stoppedInfo);
                return DebugResult.stopped(stoppedInfo, "Debug session stopped.");
            }

            ExitInfo completedInfo = info != null ? info : ExitInfo.success(Duration.ZERO);
            coordinator.ensureTerminal(false, completedInfo);
            return DebugResult.completed(completedInfo, "Debug session completed.");
        } catch (DomainException ex) {
            if ("task.cancelled".equals(ex.code())) {
                return DebugResult.stopped(ExitInfo.stopped(Duration.ZERO), "Execution was cancelled.");
            }
            return DebugResult.failed(List.of(new Diagnostic(
                    Severity.ERROR,
                    ex.code(),
                    ex.getMessage(),
                    null,
                    "debugger",
                    ex.getMessage(),
                    ex.arguments()
            )), ex.getMessage());
        }
    }

    /**
     * Enforces the single terminal-event contract for debug sessions.
     * <p>
     * Ensures that:
     * <ul>
     *   <li>Exactly one terminal event ({@link DebugEvent.Exited} or {@link DebugEvent.Stopped}) is delivered downstream.</li>
     *   <li>Duplicate terminal events from backends (e.g. backend emitting {@code Exited} and {@code Stopped}, or
     *       both backend and DebugProject attempting to emit {@code Exited}) are safely suppressed.</li>
     *   <li>Silent backends that complete the session's exit future without emitting events (e.g. external DOSBox)
     *       have their terminal event synthesized and delivered once.</li>
     *   <li>Late backend events arriving after the terminal event has already been established are safely ignored.</li>
     *   <li>Actionable failure diagnostics ({@link DebugEvent.Problem}) are always preserved and delivered.</li>
     *   <li>All event deliveries to the downstream consumer are thread-safe and strictly serialized.</li>
     * </ul>
     */
    static final class DebugLifecycleCoordinator {
        private final Consumer<DebugEvent> downstream;
        private final Object lock = new Object();
        private boolean terminalEmitted;
        private DebugEvent terminalEvent;

        DebugLifecycleCoordinator(Consumer<DebugEvent> downstream) {
            this.downstream = Objects.requireNonNull(downstream, "downstream cannot be null");
        }

        void emit(DebugEvent event) {
            onSessionEvent(event);
        }

        void onSessionEvent(DebugEvent event) {
            if (event == null) {
                return;
            }
            synchronized (lock) {
                if (event instanceof DebugEvent.Problem) {
                    downstream.accept(event);
                    return;
                }
                if (terminalEmitted) {
                    return;
                }
                if (isTerminal(event)) {
                    terminalEmitted = true;
                    terminalEvent = event;
                }
                downstream.accept(event);
            }
        }

        void ensureTerminal(boolean wasStopped, ExitInfo info) {
            synchronized (lock) {
                if (terminalEmitted) {
                    return;
                }
                terminalEmitted = true;
                DebugEvent terminal;
                if (wasStopped) {
                    terminal = new DebugEvent.Stopped();
                } else {
                    terminal = new DebugEvent.Exited(info != null ? info : ExitInfo.success(Duration.ZERO));
                }
                terminalEvent = terminal;
                downstream.accept(terminal);
            }
        }

        boolean isTerminalEmitted() {
            synchronized (lock) {
                return terminalEmitted;
            }
        }

        DebugEvent terminalEvent() {
            synchronized (lock) {
                return terminalEvent;
            }
        }

        private static boolean isTerminal(DebugEvent event) {
            return event instanceof DebugEvent.Exited || event instanceof DebugEvent.Stopped;
        }
    }
}
