package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.build.*;
import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.DebugEvent;
import io.github.dinamo541.idearm.domain.debug.DebugLaunchSpec;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;
import io.github.dinamo541.idearm.domain.model.*;
import io.github.dinamo541.idearm.domain.port.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DebugProjectTest {

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
                Map.of("debug", BuildConfiguration.debug()),
                new RunConfiguration("dosbox", "required", true, "auto", 16, List.of()),
                new DebugConfiguration("external"),
                new DistConfiguration(true, false)
        );
    }

    private ToolchainProvider createFakeProvider() {
        return new ToolchainProvider() {
            @Override public String id() { return "borland-tasm"; }
            @Override public Set<TargetSupport> supports() {
                return Set.of(new TargetSupport(16, "OMF", "MZ", "DOS"), new TargetSupport(64, "COFF", "PE32+", "Windows"));
            }
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
    void launchesDebugSessionSuccessfully() throws IOException {
        Path projectRoot = tempDir.resolve("proj");
        PublishedBuild.fresh(projectRoot, createSampleProject(), createFakeProvider(), "debug");

        ProjectRepository repo = new ProjectRepository() {
            @Override public Project load(Path root) { return createSampleProject(); }
            @Override public void save(Path root, Project project) {}
            @Override public boolean exists(Path root) { return true; }
        };

        ToolRegistry tools = new ToolRegistry() {
            @Override public Optional<ToolInstallation> find(String toolId) {
                if (toolId.equals("td") || toolId.equals("turbo-debugger")) {
                    return Optional.of(new ToolInstallation("td", "3.1", tempDir.resolve("TD.EXE"), HostKind.DOS_REAL, Map.of()));
                }
                if (toolId.contains("dosbox")) {
                    return Optional.of(new ToolInstallation("dosbox", "0.74", tempDir.resolve("dosbox.exe"), HostKind.WIN32_CONSOLE, Map.of()));
                }
                return Optional.empty();
            }
        };

        ToolRunner runner = (plan, root, resolved, cancellation, timeout) ->
                new ToolRunResult(BuildStatus.SUCCEEDED, List.of(), Path.of("temp-out"), "Build OK");

        ToolchainProvider toolchain = createFakeProvider();
        BuildProject builder = new BuildProject(repo, tools, List.of(toolchain), createFakeWorkspace(), runner, Duration.ofSeconds(5));

        BreakpointStore bpStore = new BreakpointStore() {
            @Override public List<Breakpoint> loadBreakpoints(Path root) {
                return List.of(new Breakpoint("src/main.asm", 1));
            }
            @Override public void saveBreakpoints(Path root, List<Breakpoint> breakpoints) {}
        };

        AtomicBoolean launched = new AtomicBoolean(false);
        DebugEnvironmentProvider debugEnv = new DebugEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec) {
                launched.set(true);
                return new DebugSession() {
                    @Override public CompletableFuture<ExitInfo> exit() {
                        return CompletableFuture.completedFuture(new ExitInfo(0, Duration.ofMillis(50), false, "OK"));
                    }
                    @Override public SessionState state() { return SessionState.EXITED; }
                    @Override public void stop() {}
                    @Override public void close() {}
                };
            }
        };

        DebugProject debugProject = new DebugProject(
                repo, tools, List.of(debugEnv), List.of(toolchain),
                createFakeWorkspace(), builder, bpStore, tempDir.resolve("staging")
        );

        List<DebugEvent> events = new ArrayList<>();
        DebugResult result = debugProject.execute(projectRoot, CancellationToken.NONE, events::add);

        assertTrue(launched.get());
        assertEquals(SessionState.EXITED, result.state());
        assertNotNull(result.exitInfo());
        assertEquals(0, result.exitInfo().exitCode());
        assertFalse(events.isEmpty());
        assertTrue(events.getFirst() instanceof DebugEvent.Started);
    }

    @Test
    void launchesGdbDebugSessionFor64BitTarget() throws IOException {
        Path projectRoot = tempDir.resolve("proj64");
        Project project64 = new Project(
                1,
                new ProjectInfo("SAMPLE64", "1.0.0"),
                new TargetSelection("win-pe64-console", "x86-64"),
                new ToolchainSelection("borland-tasm", ">=3.2"),
                new Sources("src/main.asm", List.of(), List.of(), List.of()),
                new Resources(List.of()),
                Map.of("debug", BuildConfiguration.debug()),
                new RunConfiguration("host", "required", true, "auto", 16, List.of()),
                new DebugConfiguration("gdb"),
                new DistConfiguration(true, false)
        );

        PublishedBuild.fresh(projectRoot, project64, createFakeProvider(), "debug");

        ProjectRepository repo = new ProjectRepository() {
            @Override public Project load(Path root) { return project64; }
            @Override public void save(Path root, Project project) {}
            @Override public boolean exists(Path root) { return true; }
        };

        ToolRegistry tools = new ToolRegistry() {
            @Override public Optional<ToolInstallation> find(String toolId) {
                if (toolId.equals("gdb")) {
                    return Optional.of(new ToolInstallation("gdb", "15.0", tempDir.resolve("gdb.exe"), HostKind.WIN64, Map.of()));
                }
                return Optional.empty();
            }
        };

        ToolRunner runner = (plan, root, resolved, cancellation, timeout) ->
                new ToolRunResult(BuildStatus.SUCCEEDED, List.of(), Path.of("temp-out"), "Build OK");

        ToolchainProvider toolchain = createFakeProvider();
        BuildProject builder = new BuildProject(repo, tools, List.of(toolchain), createFakeWorkspace(), runner, Duration.ofSeconds(5));

        BreakpointStore bpStore = new BreakpointStore() {
            @Override public List<Breakpoint> loadBreakpoints(Path root) { return List.of(); }
            @Override public void saveBreakpoints(Path root, List<Breakpoint> breakpoints) {}
        };

        AtomicBoolean gdbLaunched = new AtomicBoolean(false);
        DebugEnvironmentProvider gdbEnv = new DebugEnvironmentProvider() {
            @Override public String id() { return "gdb"; }
            @Override public boolean supports(TargetProfile profile) { return profile.codeMode() == 64; }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec) {
                gdbLaunched.set(true);
                assertEquals("gdb", spec.debuggerKind());
                return new DebugSession() {
                    @Override public CompletableFuture<ExitInfo> exit() {
                        return CompletableFuture.completedFuture(new ExitInfo(0, Duration.ofMillis(50), false, "OK"));
                    }
                    @Override public SessionState state() { return SessionState.EXITED; }
                    @Override public void stop() {}
                    @Override public void close() {}
                };
            }
        };

        DebugProject debugProject = new DebugProject(
                repo, tools, List.of(gdbEnv), List.of(toolchain),
                createFakeWorkspace(), builder, bpStore, tempDir.resolve("staging")
        );

        List<DebugEvent> events = new ArrayList<>();
        DebugResult result = debugProject.execute(projectRoot, CancellationToken.NONE, events::add);

        assertTrue(gdbLaunched.get());
        assertEquals(SessionState.EXITED, result.state());
    }
}
