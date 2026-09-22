package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.build.BuildPhase;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.*;
import io.github.dinamo541.idearm.domain.dist.DistResult;
import io.github.dinamo541.idearm.domain.model.*;
import io.github.dinamo541.idearm.domain.port.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PackageProjectTest {

    @TempDir
    Path tempDir;

    private Project createSampleProject() {
        return new Project(
                1,
                new ProjectInfo("SAMPLE", "1.0.0"),
                new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection("borland-tasm", ">=3.2"),
                new Sources("src/main.asm", List.of(), List.of(), List.of()),
                new Resources(List.of()),
                Map.of("release", BuildConfiguration.release()),
                new RunConfiguration("dosbox", "required", true, "auto", 16, List.of()),
                new DebugConfiguration("external"),
                new DistConfiguration(true, false)
        );
    }

    private ToolchainProvider createFakeProvider() {
        return new ToolchainProvider() {
            @Override public String id() { return "borland-tasm"; }
            @Override public Set<TargetSupport> supports() { return Set.of(new TargetSupport(16, "OMF", "MZ", "DOS")); }
            @Override public AssemblerAdapter assembler() {
                return new AssemblerAdapter() {
                    @Override public ToolInvocation assemble(AssembleRequest r) {
                        return new ToolInvocation("tasm", BuildPhase.ASSEMBLE, HostKind.DOS_REAL, List.of(r.source()), null, null, List.of(r.objectFile()));
                    }
                    @Override public DiagnosticParser diagnostics() { return (out, mapper) -> List.of(); }
                    @Override public HostKind host() { return HostKind.DOS_REAL; }
                };
            }
            @Override public LinkerAdapter linker() {
                return new LinkerAdapter() {
                    @Override public ToolInvocation link(LinkRequest r) {
                        return new ToolInvocation("tlink", BuildPhase.LINK, HostKind.DOS_REAL, r.objectFiles(), null, null, List.of(r.executable()));
                    }
                    @Override public DiagnosticParser diagnostics() { return (out, mapper) -> List.of(); }
                    @Override public HostKind host() { return HostKind.DOS_REAL; }
                };
            }
            @Override public ResolvedToolchain resolve(Project project, ToolRegistry registry) {
                return new ResolvedToolchain("borland-tasm", Map.of(),
                        new ToolInstallation("dosbox", "0.74", Path.of("dosbox.exe"), HostKind.WIN32_CONSOLE, Map.of()));
            }
        };
    }

    private BuildWorkspace createFakeWorkspace() {
        return new BuildWorkspace() {
            @Override public WorkspaceLock lock(Path projectRoot) { return () -> {}; }
            @Override public void validateSources(Path projectRoot, Project project) {}
            @Override public void invalidate(Path projectRoot, String configuration) {}
            @Override public void publish(Path projectRoot, String configuration, Path stagedOutput, List<String> outputs) {}
            @Override public void clean(Path projectRoot) {}
        };
    }

    @Test
    void packagesSuccessfullyWithFakePackager() throws IOException {
        Path projectRoot = tempDir.resolve("project-dist");
        Project project = createSampleProject();
        PublishedBuild.fresh(projectRoot, project, createFakeProvider(), "release");
        ProjectRepository repo = new ProjectRepository() {
            @Override public Project load(Path path) { return project; }
            @Override public void save(Path path, Project p) {}
        };
        ToolRegistry tools = id -> Optional.of(new ToolInstallation(id, "1.0", Path.of("fake.exe"), HostKind.WIN64, Map.of()));
        ToolchainProvider toolchain = createFakeProvider();
        BuildWorkspace workspace = createFakeWorkspace();
        ToolRunner runner = (plan, root, tc, cancellation, timeout) ->
                new ToolRunResult(BuildStatus.SUCCEEDED, List.of(), Path.of("temp-out"), "Build OK");
        BuildProject builder = new BuildProject(repo, tools, List.of(toolchain), workspace, runner, Duration.ofSeconds(5));

        AtomicBoolean packaged = new AtomicBoolean(false);
        DistPackager fakePackager = new DistPackager() {
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public DistResult packageProject(Project p, Path releaseExe, Path distDir, List<Path> resources, DistConfiguration config) {
                packaged.set(true);
                return new DistResult(distDir, distDir.resolve("main.exe"), distDir.resolve("run.bat"),
                        List.of(distDir.resolve("main.exe"), distDir.resolve("run.bat")), List.of());
            }
        };

        PackageProject packageProject = new PackageProject(repo, workspace, List.of(fakePackager), List.of(toolchain), builder);
        DistResult result = packageProject.execute(projectRoot);

        assertTrue(packaged.get());
        assertNotNull(result);
        assertEquals(projectRoot.resolve("dist"), result.distDirectory());
        assertEquals(projectRoot.resolve("dist/main.exe"), result.mainExecutable());
        assertEquals(2, result.packagedFiles().size());
    }

    @Test
    void failsWhenNoMatchingPackagerAvailable() throws IOException {
        Path projectRoot = tempDir.resolve("project-no-packager");
        Project project = createSampleProject();
        PublishedBuild.fresh(projectRoot, project, createFakeProvider(), "release");
        ProjectRepository repo = new ProjectRepository() {
            @Override public Project load(Path path) { return project; }
            @Override public void save(Path path, Project p) {}
        };
        ToolRegistry tools = id -> Optional.of(new ToolInstallation(id, "1.0", Path.of("fake.exe"), HostKind.WIN64, Map.of()));
        ToolchainProvider toolchain = createFakeProvider();
        BuildWorkspace workspace = createFakeWorkspace();
        ToolRunner runner = (plan, root, tc, cancellation, timeout) ->
                new ToolRunResult(BuildStatus.SUCCEEDED, List.of(), Path.of("temp-out"), "Build OK");
        BuildProject builder = new BuildProject(repo, tools, List.of(toolchain), workspace, runner, Duration.ofSeconds(5));

        // Packager that rejects all profiles
        DistPackager fakePackager = new DistPackager() {
            @Override public boolean supports(TargetProfile profile) { return false; }
            @Override public DistResult packageProject(Project p, Path releaseExe, Path distDir, List<Path> resources, DistConfiguration config) {
                return null;
            }
        };

        PackageProject packageProject = new PackageProject(repo, workspace, List.of(fakePackager), List.of(toolchain), builder);
        assertThrows(DomainException.class, () -> packageProject.execute(projectRoot));
    }
}
