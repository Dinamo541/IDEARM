package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.BuildPlanner;
import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.DebugEvent;
import io.github.dinamo541.idearm.domain.debug.DebugLaunchSpec;
import io.github.dinamo541.idearm.domain.execution.DosBoxDialects;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.RunSettings;
import io.github.dinamo541.idearm.domain.execution.SessionState;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.Project;
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
            String backend = project.debug().backend();
            io.github.dinamo541.idearm.domain.model.TargetProfile profile =
                    io.github.dinamo541.idearm.domain.model.TargetProfileCatalog.require(project.target().profile());
            // Arguments reach the program's command tail and the generated batch files, so they are checked first.
            List<io.github.dinamo541.idearm.domain.diagnostic.Diagnostic> settings =
                    RunSettings.problems(project.run(), profile);
            if (!settings.isEmpty()) {
                return DebugResult.failed(settings, settings.getFirst().message());
            }
            // GDB debugs native programs; a DOS program is debugged in DOSBox or in the built-in emulator.
            boolean useGdb = !profile.isDos();
            boolean useEmu = !useGdb && ("emu8086".equalsIgnoreCase(backend) || "internal".equalsIgnoreCase(backend));

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
                                        "Turbo Debugger (TD.EXE) was not found in registered tool paths. Please place TD.EXE in your TASM directory.",
                                        "Turbo Debugger", "TD.EXE", "TASM"));
                    } else {
                        debuggerKind = "cv";
                        debuggerTool = tools.find("cv")
                                .or(() -> tools.find("codeview"))
                                .or(() -> tools.find("td"))
                                .orElseThrow(() -> new DomainException("debug.tool.missing",
                                        "Microsoft CodeView (CV.EXE) was not found in registered tool paths. Please place CV.EXE in your MASM directory.",
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

            events.accept(new DebugEvent.Started(root, debuggerTool.toolId()));
            try (DebugSession session = envProvider.launchDebug(spec, events)) {
                events.accept(new DebugEvent.SessionAttached(session));
                while (!session.exit().isDone()) {
                    if (cancellation.cancelled()) {
                        session.stop();
                        break;
                    }
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException ie) {
                        session.stop();
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                ExitInfo info = session.exit().getNow(ExitInfo.stopped(Duration.ZERO));
                events.accept(new DebugEvent.Exited(info));
                if (session.state() == SessionState.STOPPED) {
                    return DebugResult.stopped(info, "Debug session stopped.");
                }
                return DebugResult.completed(info, "Debug session completed.");
            }
        }
    }

}
