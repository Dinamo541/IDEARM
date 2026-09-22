package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.build.BuildPhase;

import io.github.dinamo541.idearm.domain.build.*;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.model.*;
import io.github.dinamo541.idearm.domain.port.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BuildProjectTest {

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
                    @Override public DiagnosticParser diagnostics() {
                        return (out, mapper) -> List.of();
                    }
                    @Override public HostKind host() { return HostKind.DOS_REAL; }
                };
            }
            @Override public LinkerAdapter linker() {
                return new LinkerAdapter() {
                    @Override public ToolInvocation link(LinkRequest r) {
                        return new ToolInvocation("tlink", BuildPhase.LINK, HostKind.DOS_REAL, r.objectFiles(), null, null, List.of(r.executable()));
                    }
                    @Override public DiagnosticParser diagnostics() {
                        return (out, mapper) -> List.of();
                    }
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
    void buildsSuccessfullyWithFakeRunner() {
        Project project = createSampleProject();
        ProjectRepository repo = new ProjectRepository() {
            @Override public Project load(Path path) { return project; }
            @Override public void save(Path path, Project p) {}
        };
        ToolRegistry tools = id -> Optional.of(new ToolInstallation(id, "1.0", Path.of("fake.exe"), HostKind.DOS_REAL, Map.of()));
        ToolchainProvider provider = createFakeProvider();
        BuildWorkspace workspace = createFakeWorkspace();

        ToolRunner runner = (plan, root, toolchain, cancellation, timeout) -> {
            List<ToolResult> steps = plan.steps().stream()
                    .map(step -> new ToolResult(step, 0, "OK"))
                    .toList();
            return new ToolRunResult(BuildStatus.SUCCEEDED, steps, Path.of("temp-out"), "Build OK");
        };

        BuildProject buildProject = new BuildProject(repo, tools, List.of(provider), workspace, runner, Duration.ofSeconds(10));
        BuildResult result = buildProject.execute(Path.of("fake-root"), "release");

        assertTrue(result.succeeded());
        assertEquals(BuildStatus.SUCCEEDED, result.status());
        assertEquals("release", result.configuration());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void handlesDiagnosticErrorsAndFails() {
        Project project = createSampleProject();
        ProjectRepository repo = new ProjectRepository() {
            @Override public Project load(Path path) { return project; }
            @Override public void save(Path path, Project p) {}
        };
        ToolRegistry tools = id -> Optional.empty();

        ToolchainProvider provider = new ToolchainProvider() {
            @Override public String id() { return "borland-tasm"; }
            @Override public Set<TargetSupport> supports() { return Set.of(new TargetSupport(16, "OMF", "MZ", "DOS")); }
            @Override public AssemblerAdapter assembler() {
                return new AssemblerAdapter() {
                    @Override public ToolInvocation assemble(AssembleRequest r) {
                        return new ToolInvocation("tasm", BuildPhase.ASSEMBLE, HostKind.DOS_REAL, List.of(r.source()), null, null, List.of(r.objectFile()));
                    }
                    @Override public DiagnosticParser diagnostics() {
                        return (out, mapper) -> List.of(new Diagnostic(Severity.ERROR, "", "Syntax error", new Location("main.asm", 10, 1), "tasm", out));
                    }
                    @Override public HostKind host() { return HostKind.DOS_REAL; }
                };
            }
            @Override public LinkerAdapter linker() {
                return new LinkerAdapter() {
                    @Override public ToolInvocation link(LinkRequest r) {
                        return new ToolInvocation("tlink", BuildPhase.LINK, HostKind.DOS_REAL, r.objectFiles(), null, null, List.of(r.executable()));
                    }
                    @Override public DiagnosticParser diagnostics() {
                        return (out, mapper) -> List.of();
                    }
                    @Override public HostKind host() { return HostKind.DOS_REAL; }
                };
            }
            @Override public ResolvedToolchain resolve(Project project, ToolRegistry registry) {
                return new ResolvedToolchain("borland-tasm", Map.of(),
                        new ToolInstallation("dosbox", "0.74", Path.of("dosbox.exe"), HostKind.WIN32_CONSOLE, Map.of()));
            }
        };

        ToolRunner runner = (plan, root, toolchain, cancellation, timeout) -> {
            var step = new ToolResult(plan.steps().getFirst(), 1, "Error at line 10");
            return new ToolRunResult(BuildStatus.FAILED, List.of(step), Path.of("temp-out"), "Build Failed");
        };

        BuildProject buildProject = new BuildProject(repo, tools, List.of(provider), createFakeWorkspace(), runner, Duration.ofSeconds(10));
        BuildResult result = buildProject.execute(Path.of("fake-root"), "release");

        assertFalse(result.succeeded());
        assertEquals(BuildStatus.FAILED, result.status());
        assertEquals(1, result.diagnostics().size());
        assertEquals(Severity.ERROR, result.diagnostics().getFirst().severity());
        assertEquals(10, result.diagnostics().getFirst().location().line());
    }

    @Test
    void honorsCancellation() {
        Project project = createSampleProject();
        ProjectRepository repo = new ProjectRepository() {
            @Override public Project load(Path path) { return project; }
            @Override public void save(Path path, Project p) {}
        };
        ToolRegistry tools = id -> Optional.empty();
        ToolchainProvider provider = createFakeProvider();
        BuildWorkspace workspace = createFakeWorkspace();

        AtomicBoolean cancelled = new AtomicBoolean(true);
        CancellationToken cancellationToken = cancelled::get;

        ToolRunner runner = (plan, root, toolchain, ct, timeout) -> {
            return new ToolRunResult(BuildStatus.CANCELLED, List.of(), null, "Cancelled");
        };

        BuildProject buildProject = new BuildProject(repo, tools, List.of(provider), workspace, runner, Duration.ofSeconds(10));
        BuildResult result = buildProject.execute(Path.of("fake-root"), "release", cancellationToken, event -> {});

        assertEquals(BuildStatus.CANCELLED, result.status());
    }
}
