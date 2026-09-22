package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.BuildPlan;
import io.github.dinamo541.idearm.domain.build.BuildPlanner;
import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.port.ToolchainProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The program Run, Debug and Package work with, rebuilt first whenever it is older than what it is made of.
 *
 * <p>Reusing any executable that merely exists meant that, after the first build, pressing Run showed the old
 * program no matter what the user had changed. A build is up to date only when every planned artifact exists and
 * is newer than the project description, every source and every file in the include folders.
 */
final class FreshBuild {

    /** The plan, and either the executable to use or the build that failed to produce it. */
    record Outcome(BuildPlan plan, Path buildDirectory, Path executable, BuildResult failedBuild) {
        boolean failed() {
            return failedBuild != null;
        }

        /** Each assembled source with the absolute path of its listing, for source-level debugging. */
        Map<String, Path> listings() {
            var listings = new java.util.LinkedHashMap<String, Path>();
            plan.listings().forEach((source, listing) -> listings.put(source, buildDirectory.resolve(listing)));
            return listings;
        }
    }

    private FreshBuild() {
    }

    static ToolchainProvider toolchain(Project project, List<ToolchainProvider> toolchains) {
        return toolchains.stream()
                .filter(provider -> provider.id().equals(project.toolchain().id()))
                .findFirst()
                .orElseThrow(() -> new DomainException("toolchain.provider.unavailable",
                        "No toolchain provider for " + project.toolchain().id(), project.toolchain().id()));
    }

    /**
     * Makes sure the configuration's artifacts match the sources, building them if needed.
     *
     * @param project a project whose source patterns are already expanded
     * @param events  receives the build's progress when a build runs
     */
    static Outcome ensure(Path root, Project project, String configuration, List<ToolchainProvider> toolchains,
                          BuildProject builder, CancellationToken cancellation, Consumer<BuildEvent> events) {
        BuildPlan plan = new BuildPlanner().plan(project, configuration, toolchain(project, toolchains));
        Path buildDirectory = root.resolve("build").resolve(configuration);
        if (!upToDate(root, project, plan, buildDirectory)) {
            BuildResult result = builder.execute(root, configuration, cancellation, events);
            if (result.status() != BuildStatus.SUCCEEDED) {
                return new Outcome(plan, buildDirectory, null, result);
            }
        }
        Path executable = buildDirectory.resolve(plan.executable());
        if (!Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS)) {
            throw new DomainException("build.executable.missing", "The build did not produce " + executable, executable);
        }
        return new Outcome(plan, buildDirectory, executable, null);
    }

    static boolean upToDate(Path root, Project project, BuildPlan plan, Path buildDirectory) {
        try {
            FileTime oldestOutput = null;
            for (String output : plan.outputs()) {
                Path artifact = buildDirectory.resolve(output);
                if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS)) {
                    return false;
                }
                FileTime modified = Files.getLastModifiedTime(artifact, LinkOption.NOFOLLOW_LINKS);
                oldestOutput = oldestOutput == null || modified.compareTo(oldestOutput) < 0 ? modified : oldestOutput;
            }
            if (oldestOutput == null) {
                return false;
            }
            for (Path input : inputs(root, project)) {
                // Equal times rebuild too: a save in the same clock tick as the build must not be missed.
                if (Files.getLastModifiedTime(input, LinkOption.NOFOLLOW_LINKS).compareTo(oldestOutput) >= 0) {
                    return false;
                }
            }
            return true;
        } catch (IOException | UncheckedIOException unreadable) {
            return false;
        }
    }

    private static List<Path> inputs(Path root, Project project) throws IOException {
        var inputs = new ArrayList<Path>();
        inputs.add(root.resolve("idearm.toml"));
        inputs.add(root.resolve(project.sources().entry()));
        for (String module : project.sources().modules()) {
            inputs.add(root.resolve(module));
        }
        for (String include : project.sources().include()) {
            Path folder = root.resolve(include);
            if (Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
                try (var tree = Files.walk(folder)) {
                    tree.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).forEach(inputs::add);
                }
            }
        }
        var existing = new ArrayList<Path>();
        for (Path input : inputs) {
            if (!Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)) {
                // A missing input cannot be judged; the build reports it properly.
                throw new IOException("missing input " + input);
            }
            existing.add(input);
        }
        return existing;
    }
}
