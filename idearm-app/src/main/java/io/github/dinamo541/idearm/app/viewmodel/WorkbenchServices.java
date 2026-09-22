package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.domain.port.BreakpointStore;
import io.github.dinamo541.idearm.domain.port.BuildWorkspace;
import io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider;
import io.github.dinamo541.idearm.domain.port.DistPackager;
import io.github.dinamo541.idearm.domain.port.ExecutionEnvironmentProvider;
import io.github.dinamo541.idearm.domain.port.ProjectFiles;
import io.github.dinamo541.idearm.domain.port.ProjectRepository;
import io.github.dinamo541.idearm.domain.port.ToolRegistry;
import io.github.dinamo541.idearm.domain.port.ToolRunner;
import io.github.dinamo541.idearm.domain.port.ToolchainProvider;
import io.github.dinamo541.idearm.domain.port.TerminalRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Every port the workbench needs, resolved once by the composition root.
 *
 * <p>Presentation code only ever sees these domain ports: which adapter implements each one is decided in
 * {@code io.github.dinamo541.idearm.app.bootstrap}, so a view model can be exercised with fakes and the IDE can
 * change toolchains without touching the UI (ADR-007).
 */
public record WorkbenchServices(
        ProjectRepository projectRepository,
        ToolRegistry toolRegistry,
        BuildWorkspace workspace,
        ToolRunner toolRunner,
        Path stagingRoot,
        List<ToolchainProvider> toolchainProviders,
        List<ExecutionEnvironmentProvider> executionProviders,
        List<DebugEnvironmentProvider> debugProviders,
        List<DistPackager> distPackagers,
        BreakpointStore breakpointStore,
        Duration buildTimeout,
        ProjectFiles projectFiles,
        TerminalRunner terminalRunner,
        io.github.dinamo541.idearm.domain.port.RecentItemsStore recentItemsStore) {

    public WorkbenchServices(
            ProjectRepository projectRepository,
            ToolRegistry toolRegistry,
            BuildWorkspace workspace,
            ToolRunner toolRunner,
            Path stagingRoot,
            List<ToolchainProvider> toolchainProviders,
            List<ExecutionEnvironmentProvider> executionProviders,
            List<DebugEnvironmentProvider> debugProviders,
            List<DistPackager> distPackagers,
            BreakpointStore breakpointStore,
            Duration buildTimeout,
            ProjectFiles projectFiles) {
        this(projectRepository, toolRegistry, workspace, toolRunner, stagingRoot,
                toolchainProviders, executionProviders, debugProviders, distPackagers,
                breakpointStore, buildTimeout, projectFiles, null, null);
    }

    public WorkbenchServices(ProjectRepository projectRepository, ToolRegistry toolRegistry,
            BuildWorkspace workspace, ToolRunner toolRunner, Path stagingRoot,
            List<ToolchainProvider> toolchainProviders, List<ExecutionEnvironmentProvider> executionProviders,
            List<DebugEnvironmentProvider> debugProviders, List<DistPackager> distPackagers,
            BreakpointStore breakpointStore, Duration buildTimeout, ProjectFiles projectFiles,
            TerminalRunner terminalRunner) {
        this(projectRepository, toolRegistry, workspace, toolRunner, stagingRoot, toolchainProviders,
                executionProviders, debugProviders, distPackagers, breakpointStore, buildTimeout,
                projectFiles, terminalRunner, null);
    }

    public WorkbenchServices {
        Objects.requireNonNull(projectRepository, "projectRepository");
        Objects.requireNonNull(toolRegistry, "toolRegistry");
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(toolRunner, "toolRunner");
        Objects.requireNonNull(stagingRoot, "stagingRoot");
        toolchainProviders = List.copyOf(toolchainProviders);
        executionProviders = List.copyOf(executionProviders);
        debugProviders = List.copyOf(debugProviders);
        distPackagers = List.copyOf(distPackagers);
        Objects.requireNonNull(breakpointStore, "breakpointStore");
        Objects.requireNonNull(projectFiles, "projectFiles");
        Objects.requireNonNull(buildTimeout, "buildTimeout");
        if (buildTimeout.isZero() || buildTimeout.isNegative()) {
            throw new IllegalArgumentException("The build timeout must be positive.");
        }
    }
}
