package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.BuildPlanner;
import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.dist.DistResult;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.port.BuildWorkspace;
import io.github.dinamo541.idearm.domain.port.DistPackager;
import io.github.dinamo541.idearm.domain.port.ProjectRepository;
import io.github.dinamo541.idearm.domain.port.ToolchainProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates creating a standalone, portable distribution in dist/.
 * Enforces release build generation, gathers runtime assets, and delegates
 * packaging to a target-specific DistPackager.
 */
public final class PackageProject {

    private final ProjectRepository projects;
    private final BuildWorkspace workspace;
    private final List<DistPackager> packagers;
    private final List<ToolchainProvider> toolchains;
    private final BuildProject builder;

    public PackageProject(ProjectRepository projects,
                          BuildWorkspace workspace,
                          List<DistPackager> packagers,
                          List<ToolchainProvider> toolchains,
                          BuildProject builder) {
        this.projects = Objects.requireNonNull(projects, "projects cannot be null");
        this.workspace = Objects.requireNonNull(workspace, "workspace cannot be null");
        this.packagers = List.copyOf(packagers);
        this.toolchains = List.copyOf(toolchains);
        this.builder = Objects.requireNonNull(builder, "builder cannot be null");
    }

    public DistResult execute(Path projectRoot) {
        return execute(projectRoot, CancellationToken.NONE);
    }

    public DistResult execute(Path projectRoot, CancellationToken cancellation) {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        Objects.requireNonNull(cancellation, "cancellation cannot be null");

        Path root = projectRoot.toAbsolutePath().normalize();

        try (var lock = workspace.lock(root)) {
            cancellation.throwIfCancelled();
            Project project = SourceSet.expand(projects.load(root), root);

            FreshBuild.Outcome build = FreshBuild.ensure(root, project, "release", toolchains, builder,
                    cancellation, event -> { });
            if (build.failed()) {
                throw new DomainException("dist.build.failed",
                        "Release build failed with " + build.failedBuild().diagnostics().size() + " diagnostic(s).", build.failedBuild().diagnostics().size());
            }
            var plan = build.plan();
            Path releaseExecutable = build.executable();

            var matchingPackagers = packagers.stream()
                    .filter(p -> p.supports(plan.target()))
                    .toList();
            if (matchingPackagers.isEmpty()) {
                throw new DomainException("dist.packager.unavailable",
                        "No packager found for profile: " + plan.target().id(), plan.target().id());
            }
            DistPackager packager = matchingPackagers.getFirst();

            // Collect resources
            List<Path> resources = new ArrayList<>();
            for (String relPath : project.resources().files()) {
                Path res = root.resolve(relPath).normalize();
                if (!res.startsWith(root)) {
                    throw new DomainException("dist.resource.traversal",
                            "Resource attempts directory traversal: " + relPath, relPath);
                }
                if (!Files.exists(res)) {
                    throw new DomainException("dist.resource.missing",
                            "Runtime resource does not exist: " + relPath, relPath);
                }
                resources.add(res);
            }

            Path distDir = root.resolve("dist");
            return packager.packageProject(project, releaseExecutable, distDir, resources, project.dist());
        }
    }
}
