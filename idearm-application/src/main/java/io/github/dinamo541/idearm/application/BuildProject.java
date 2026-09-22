package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.*;
import io.github.dinamo541.idearm.domain.diagnostic.*;
import io.github.dinamo541.idearm.domain.port.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Orchestrates a build without filesystem, process, DOS or JavaFX knowledge. */
public final class BuildProject {
    private final ProjectRepository projects;
    private final ToolRegistry tools;
    private final List<ToolchainProvider> providers;
    private final BuildWorkspace workspace;
    private final ToolRunner runner;
    private final Duration timeout;

    public BuildProject(ProjectRepository projects, ToolRegistry tools, List<ToolchainProvider> providers,
                        BuildWorkspace workspace, ToolRunner runner, Duration timeout) {
        this.projects = Objects.requireNonNull(projects);
        this.tools = Objects.requireNonNull(tools);
        this.providers = List.copyOf(providers);
        this.workspace = Objects.requireNonNull(workspace);
        this.runner = Objects.requireNonNull(runner);
        this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Timeout must be positive.");
    }

    public BuildResult execute(Path root, String configuration) {
        return execute(root, configuration, CancellationToken.NONE, event -> {});
    }

    public BuildResult execute(Path root, String configuration, CancellationToken cancellation,
                               Consumer<BuildEvent> events) {
        Objects.requireNonNull(root); Objects.requireNonNull(configuration);
        Objects.requireNonNull(cancellation); Objects.requireNonNull(events);
        events.accept(new BuildEvent.Started(root, configuration));
        BuildResult result;
        var diagnostics = new ArrayList<Diagnostic>();
        String output = "";
        try (var lock = workspace.lock(root)) {
            cancellation.throwIfCancelled();
            var project = SourceSet.expand(projects.load(root), root);
            var matching = providers.stream().filter(p -> p.id().equals(project.toolchain().id())).toList();
            if (matching.size() != 1) {
                throw new DomainException("toolchain.provider.unavailable",
                        "Expected one provider for toolchain: " + project.toolchain().id(), project.toolchain().id());
            }
            var provider = matching.getFirst();
            BuildPlan plan = new BuildPlanner().plan(project, configuration, provider);
            workspace.invalidate(root, configuration);
            workspace.validateSources(root, project);
            var resolved = provider.resolve(project, tools);
            cancellation.throwIfCancelled();
            var execution = runner.run(plan, root, resolved, cancellation, timeout);
            try {
                output = execution.output();
                events.accept(new BuildEvent.Output(output));
                for (int i = 0; i < execution.steps().size(); i++) {
                    var step = execution.steps().get(i);
                    if (i >= plan.steps().size() || !step.invocation().equals(plan.steps().get(i))) {
                        throw new DomainException("build.protocol.invalid", "Runner returned an unexpected build step.");
                    }
                    var parser = step.invocation().phase() == BuildPhase.LINK
                            ? provider.linker().diagnostics() : provider.assembler().diagnostics();
                    diagnostics.addAll(parser.parse(step.output(), execution.sourcePaths()));
                }
                BuildStatus status = cancellation.cancelled() ? BuildStatus.CANCELLED : execution.status();
                boolean complete = execution.steps().size() == plan.steps().size()
                        && execution.steps().stream().allMatch(step -> Integer.valueOf(0).equals(step.exitCode()));
                boolean errors = diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR || d.severity() == Severity.FATAL);
                if (status == BuildStatus.SUCCEEDED && (!complete || errors)) status = BuildStatus.FAILED;
                List<Path> artifacts = List.of();
                if (status == BuildStatus.SUCCEEDED) {
                    cancellation.throwIfCancelled();
                    workspace.publish(root, configuration, execution.outputDirectory(), plan.outputs());
                    artifacts = plan.outputs().stream()
                            .map(path -> root.toAbsolutePath().normalize().resolve("build").resolve(configuration).resolve(path))
                            .toList();
                } else if (status == BuildStatus.CANCELLED || status == BuildStatus.TIMED_OUT || !errors) {
                    String code = switch (status) {
                        case CANCELLED -> "task.cancelled";
                        case TIMED_OUT -> "build.timedOut";
                        default -> "build.failed";
                    };
                    diagnostics.add(problem(code, status == BuildStatus.TIMED_OUT
                            ? "The build exceeded its timeout. A tool may be waiting for input."
                            : "Build did not complete successfully.", List.of()));
                }
                result = new BuildResult(status, configuration, diagnostics, artifacts, output);
            } finally {
                runner.release(execution);
            }
        } catch (DomainException ex) {
            diagnostics.add(problem(ex.code(), ex.getMessage(), ex.arguments()));
            result = new BuildResult(ex.code().equals("task.cancelled") ? BuildStatus.CANCELLED : BuildStatus.FAILED,
                    configuration, diagnostics, List.of(), output);
        }
        events.accept(new BuildEvent.DiagnosticsPublished(result.diagnostics()));
        events.accept(new BuildEvent.Finished(result));
        return result;
    }

    private static Diagnostic problem(String code, String detail, List<String> arguments) {
        return new Diagnostic(code.equals("task.cancelled") ? Severity.INFO : Severity.ERROR, code, detail,
                null, "idearm", "", arguments);
    }
}
