package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.BuildPlanner;
import io.github.dinamo541.idearm.domain.build.FileNameRules;
import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.execution.*;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.domain.port.BuildWorkspace;
import io.github.dinamo541.idearm.domain.port.ExecutionEnvironmentProvider;
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
 * Orchestrates running a project within an isolated execution environment.
 * Ensures the target executable is up-to-date, stages runtime resources without
 * mounting host source trees, and monitors the session until termination.
 */
public final class RunProject {

    private final ProjectRepository projects;
    private final ToolRegistry tools;
    private final List<ExecutionEnvironmentProvider> environments;
    private final List<ToolchainProvider> toolchains;
    private final BuildWorkspace workspace;
    private final BuildProject builder;
    private final Path stagingRoot;

    public RunProject(ProjectRepository projects,
                      ToolRegistry tools,
                      List<ExecutionEnvironmentProvider> environments,
                      List<ToolchainProvider> toolchains,
                      BuildWorkspace workspace,
                      BuildProject builder,
                      Path stagingRoot) {
        this.projects = Objects.requireNonNull(projects, "projects cannot be null");
        this.tools = Objects.requireNonNull(tools, "tools cannot be null");
        this.environments = List.copyOf(environments);
        this.toolchains = List.copyOf(toolchains);
        this.workspace = Objects.requireNonNull(workspace, "workspace cannot be null");
        this.builder = Objects.requireNonNull(builder, "builder cannot be null");
        this.stagingRoot = Objects.requireNonNull(stagingRoot, "stagingRoot cannot be null");
    }

    public RunResult execute(Path projectRoot, String configuration) {
        return execute(projectRoot, configuration, CancellationToken.NONE, event -> {});
    }

    public RunResult execute(Path projectRoot,
                             String configuration,
                             CancellationToken cancellation,
                             Consumer<RunEvent> events) {
        return execute(projectRoot, configuration, null, cancellation, events);
    }

    /**
     * Runs the project, optionally overriding whether the window waits for a key press before closing.
     *
     * @param keepOpenOverride {@code null} to honour the project setting, which is the normal case; a value when
     *                         the user picks "Run (pause on exit)" for this run only.
     */
    public RunResult execute(Path projectRoot,
                             String configuration,
                             Boolean keepOpenOverride,
                             CancellationToken cancellation,
                             Consumer<RunEvent> events) {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        Objects.requireNonNull(cancellation, "cancellation cannot be null");
        Objects.requireNonNull(events, "events cannot be null");

        String config = (configuration == null || configuration.isBlank()) ? "debug" : configuration;
        Path root = projectRoot.toAbsolutePath().normalize();

        try (var lock = workspace.lock(root)) {
            cancellation.throwIfCancelled();
            Project project = SourceSet.expand(projects.load(root), root);

            FreshBuild.Outcome build = FreshBuild.ensure(root, project, config, toolchains, builder, cancellation,
                    event -> forwardBuildOutput(event, events));
            if (build.failed()) {
                return RunResult.failed(build.failedBuild().diagnostics(), build.failedBuild().output());
            }
            Path executable = build.executable();

            // The environment must be able to run this target: a DOS program needs DOSBox, a Windows program
            // runs natively. The configured one is preferred when it can; otherwise any that can is used.
            String envId = project.run().environment();
            TargetProfile target = build.plan().target();
            var capable = environments.stream().filter(env -> env.supports(target)).toList();
            if (capable.isEmpty()) {
                throw new DomainException("environment.unavailable",
                        "No execution environment can run " + target.id() + " programs on this machine.", target.id());
            }
            var envProvider = capable.stream()
                    .filter(env -> env.id().equalsIgnoreCase(envId)
                            || (DosBoxDialects.isDosBox(envId) && DosBoxDialects.isDosBox(env.id())))
                    .findFirst()
                    .orElse(capable.getFirst());

            // The values written into the emulator's configuration and batch file are checked first: a line break
            // in them would add commands of their own.
            List<Diagnostic> settings = RunSettings.problems(project.run(), target);
            if (!settings.isEmpty()) {
                return RunResult.failed(settings, settings.getFirst().message());
            }

            // A native program needs no emulator; an emulated one needs the installed emulator, preferring the
            // dialect the project asked for (e.g. "dosbox-x") and otherwise the automatic order, 0.74-3 first.
            ToolInstallation tool = envProvider.isolation() == IsolationLevel.NATIVE
                    ? new ToolInstallation(envProvider.id(), "native", executable,
                            io.github.dinamo541.idearm.domain.model.HostKind.WIN64, Map.of(), null, "system")
                    : (DosBoxDialects.isDosBox(envId) ? tools.find(envId) : Optional.<ToolInstallation>empty())
                            .or(() -> tools.find(envProvider.id()))
                            .or(() -> tools.find(DosBoxDialects.AUTO))
                            .orElseThrow(() -> new DomainException("environment.tool.missing",
                                    "No " + envProvider.id() + " installation is registered; install DOSBox 0.74-3, DOSBox-X or DOSBox Staging.", envProvider.id()));

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
            List<Diagnostic> notes = longNameNotes(project, target, tool);

            var spec = new LaunchSpec(
                    executable,
                    root,
                    resources,
                    project.run().args(),
                    keepOpenOverride != null ? keepOpenOverride : project.run().keepOpen(),
                    project.run().cycles(),
                    project.run().memsize(),
                    stagingRoot,
                    tool
            );

            events.accept(new RunEvent.Started(root, tool.toolId()));
            try (ExecutionSession session = envProvider.launch(spec)) {
                if (session.interactive()) {
                    events.accept(new RunEvent.SessionAttached(session));
                    session.onOutput(text -> events.accept(new RunEvent.ProgramOutput(text)));
                }
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

                ExitInfo exitInfo = session.exit().join();
                events.accept(new RunEvent.Exited(exitInfo));
                return new RunResult(session.state(), exitInfo, notes, exitInfo.message());
            }
        } catch (DomainException ex) {
            if ("task.cancelled".equals(ex.code())) {
                return RunResult.stopped(ExitInfo.stopped(Duration.ZERO), "Execution was cancelled.");
            }
            return RunResult.failed(List.of(new Diagnostic(
                    Severity.ERROR,
                    ex.code(),
                    ex.getMessage(),
                    null,
                    "runner",
                    ex.getMessage(),
                    ex.arguments()
            )), ex.getMessage());
        }
    }

    /**
     * Only DOSBox-X lets a DOS program open a long file name (Phase 0, S4). With another dialect, a resource such as
     * {@code inv_bottom.spr} is copied but the program cannot open it, so the user is told which setting helps.
     */
    private static List<Diagnostic> longNameNotes(Project project, TargetProfile target, ToolInstallation tool) {
        if (!target.isDos() || tool.toolId().equalsIgnoreCase(DosBoxDialects.X)) {
            return List.of();
        }
        var notes = new ArrayList<Diagnostic>();
        for (String resource : project.resources().files()) {
            for (String segment : resource.replace('\\', '/').split("/")) {
                if (!segment.isEmpty() && !FileNameRules.isDosName(segment)) {
                    notes.add(problem("run.resource.lfn", Severity.INFO,
                            "DOS programs can only open " + resource + " (not an 8.3 name) in DOSBox-X; choose it in"
                                    + " Project Properties.", resource));
                    break;
                }
            }
        }
        return List.copyOf(notes);
    }

    private static Diagnostic problem(String code, Severity severity, String message, String... arguments) {
        return new Diagnostic(severity, code, message, null, "runner", "", List.of(arguments));
    }

    /** A build started by Run shows its log where the run's output goes, so the user sees why Run took longer. */
    private static void forwardBuildOutput(BuildEvent event, Consumer<RunEvent> events) {
        if (event instanceof BuildEvent.Output output && !output.text().isBlank()) {
            events.accept(new RunEvent.Output(output.text()));
        }
    }
}
