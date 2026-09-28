package io.github.dinamo541.idearm.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.build.AssembleRequest;
import io.github.dinamo541.idearm.domain.build.BuildPhase;
import io.github.dinamo541.idearm.domain.build.BuildPlan;
import io.github.dinamo541.idearm.domain.build.BuildPlanner;
import io.github.dinamo541.idearm.domain.build.LinkRequest;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.model.BuildConfiguration;
import io.github.dinamo541.idearm.domain.model.DebugConfiguration;
import io.github.dinamo541.idearm.domain.model.DistConfiguration;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.ProjectInfo;
import io.github.dinamo541.idearm.domain.model.ResolvedToolchain;
import io.github.dinamo541.idearm.domain.model.Resources;
import io.github.dinamo541.idearm.domain.model.RunConfiguration;
import io.github.dinamo541.idearm.domain.model.Sources;
import io.github.dinamo541.idearm.domain.model.TargetSelection;
import io.github.dinamo541.idearm.domain.model.TargetSupport;
import io.github.dinamo541.idearm.domain.model.ToolchainSelection;
import io.github.dinamo541.idearm.domain.port.AssemblerAdapter;
import io.github.dinamo541.idearm.domain.port.DiagnosticParser;
import io.github.dinamo541.idearm.domain.port.LinkerAdapter;
import io.github.dinamo541.idearm.domain.port.ToolRegistry;
import io.github.dinamo541.idearm.domain.port.ToolchainProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FreshBuildTest {

    @TempDir
    Path root;

    /**
     * Reusing a program that is older than a file it includes meant that, after editing macros.inc, Run showed the
     * previous build. An included file is a build input just as much as the source that includes it.
     */
    @Test
    void editingAnIncludedFileMakesTheBuildStale() throws IOException {
        Project project = project();
        PublishedBuild.fresh(root, project, provider, "debug");
        Path included = stale(root.resolve("src/manzana.inc"), root.resolve("src/main.asm"));
        BuildPlan plan = new BuildPlanner().plan(project, "debug", provider,
                List.of("src/manzana.inc"), List.of("src"));
        Path buildDirectory = root.resolve("build").resolve("debug");

        assertTrue(FreshBuild.upToDate(root, project, plan, buildDirectory),
                "nothing changed since the build");

        PublishedBuild.edit(included);

        assertFalse(FreshBuild.upToDate(root, project, plan, buildDirectory),
                "the included file is newer than the program");
    }

    @Test
    void anIncludedFileThatDisappearedForcesARebuildSoTheBuildCanReportIt() throws IOException {
        Project project = project();
        PublishedBuild.fresh(root, project, provider, "debug");
        Path included = stale(root.resolve("src/manzana.inc"), root.resolve("src/main.asm"));
        BuildPlan plan = new BuildPlanner().plan(project, "debug", provider,
                List.of("src/manzana.inc"), List.of("src"));

        Files.delete(included);

        assertFalse(FreshBuild.upToDate(root, project, plan, root.resolve("build").resolve("debug")));
    }

    /** A file written as it was before the last build, so only a later edit can make the build stale. */
    private static Path stale(Path file, Path asOldAs) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "manzana DB 1\n");
        Files.setLastModifiedTime(file, Files.getLastModifiedTime(asOldAs));
        return file;
    }

    private static Project project() {
        return new Project(1, new ProjectInfo("SAMPLE", "1.0.0"), new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection("borland-tasm", ">=3.2"),
                new Sources("src/main.asm", List.of(), List.of(), List.of()), new Resources(List.of()),
                Map.of("debug", BuildConfiguration.debug()), RunConfiguration.defaults(),
                new DebugConfiguration(DebugConfiguration.EMULATOR), new DistConfiguration(true, false));
    }

    private final ToolchainProvider provider = new ToolchainProvider() {
        @Override public String id() { return "borland-tasm"; }
        @Override public Set<TargetSupport> supports() { return Set.of(new TargetSupport(16, "OMF", "MZ", "DOS")); }
        @Override public AssemblerAdapter assembler() {
            return new AssemblerAdapter() {
                @Override public ToolInvocation assemble(AssembleRequest r) {
                    return new ToolInvocation("tasm", BuildPhase.ASSEMBLE, HostKind.DOS_REAL, List.of(r.source()),
                            null, null, List.of(r.objectFile()));
                }
                @Override public DiagnosticParser diagnostics() { return (out, mapper) -> List.of(); }
                @Override public HostKind host() { return HostKind.DOS_REAL; }
            };
        }
        @Override public LinkerAdapter linker() {
            return new LinkerAdapter() {
                @Override public ToolInvocation link(LinkRequest r) {
                    return new ToolInvocation("tlink", BuildPhase.LINK, HostKind.DOS_REAL, r.objectFiles(), null,
                            null, List.of(r.executable()));
                }
                @Override public DiagnosticParser diagnostics() { return (out, mapper) -> List.of(); }
                @Override public HostKind host() { return HostKind.DOS_REAL; }
            };
        }
        @Override public ResolvedToolchain resolve(Project project, ToolRegistry registry) { return null; }
    };
}
