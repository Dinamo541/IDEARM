package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.build.BuildPlan;
import io.github.dinamo541.idearm.domain.build.BuildPlanner;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.port.ToolchainProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** A project on disk whose build is already published and newer than its sources, as after a real build. */
final class PublishedBuild {

    private static final FileTime SOURCES = FileTime.from(Instant.now().minus(2, ChronoUnit.HOURS));
    private static final FileTime OUTPUTS = FileTime.from(Instant.now().minus(1, ChronoUnit.HOURS));

    private PublishedBuild() {
    }

    static BuildPlan fresh(Path root, Project project, ToolchainProvider provider, String configuration)
            throws IOException {
        write(root.resolve("idearm.toml"), "schema = 1\n", SOURCES);
        write(root.resolve(project.sources().entry()), "; entry\n", SOURCES);
        for (String module : project.sources().modules()) {
            write(root.resolve(module), "; module\n", SOURCES);
        }
        BuildPlan plan = new BuildPlanner().plan(project, configuration, provider);
        for (String output : plan.outputs()) {
            write(root.resolve("build").resolve(configuration).resolve(output), "MZ", OUTPUTS);
        }
        return plan;
    }

    /** Marks a file as edited after the last build. */
    static void edit(Path file) throws IOException {
        Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
    }

    private static void write(Path file, String content, FileTime time) throws IOException {
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            Files.writeString(file, content);
        }
        Files.setLastModifiedTime(file, time);
    }
}
