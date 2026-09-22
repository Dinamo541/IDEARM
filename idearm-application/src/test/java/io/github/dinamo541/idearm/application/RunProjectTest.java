package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.build.BuildPhase;

import io.github.dinamo541.idearm.domain.build.*;
import io.github.dinamo541.idearm.domain.execution.*;
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

class RunProjectTest {

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
    void runsSuccessfullyWithFakeEnvironment() throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Project project = createSampleProject();
        PublishedBuild.fresh(projectRoot, project, createFakeProvider(), "debug");
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

        AtomicBoolean launched = new AtomicBoolean(false);
        ExecutionEnvironmentProvider fakeEnv = new ExecutionEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public IsolationLevel isolation() { return IsolationLevel.EMULATED; }
            @Override public Set<EnvCapability> capabilities() { return Set.of(EnvCapability.WINDOW); }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public ExecutionSession launch(LaunchSpec spec) {
                launched.set(true);
                return new ExecutionSession() {
                    private final CompletableFuture<ExitInfo> exit = CompletableFuture.completedFuture(
                            ExitInfo.success(Duration.ofMillis(100)));
                    @Override public SessionState state() { return SessionState.EXITED; }
                    @Override public CompletableFuture<ExitInfo> exit() { return exit; }
                    @Override public void stop() {}
                    @Override public Path stagingDirectory() { return tempDir.resolve("staging"); }
                };
            }
        };

        RunProject runProject = new RunProject(
                repo, tools, List.of(fakeEnv), List.of(toolchain), workspace, builder, tempDir.resolve("staging")
        );

        RunResult result = runProject.execute(projectRoot, "debug");

        assertTrue(launched.get());
        assertEquals(SessionState.EXITED, result.state());
        assertNotNull(result.exitInfo());
        assertEquals(0, result.exitInfo().exitCode());
        assertFalse(result.exitInfo().wasStopped());
    }

    /** A program whose console is the IDE (native, outside Windows) shows its text there and reads the keyboard. */
    @Test
    void anInteractiveProgramIsAttachedAndItsOutputForwarded() throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Project project = createSampleProject();
        PublishedBuild.fresh(projectRoot, project, createFakeProvider(), "debug");
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

        var typed = new StringBuilder();
        ExecutionEnvironmentProvider fakeEnv = new ExecutionEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public IsolationLevel isolation() { return IsolationLevel.EMULATED; }
            @Override public Set<EnvCapability> capabilities() { return Set.of(); }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public ExecutionSession launch(LaunchSpec spec) {
                return new ExecutionSession() {
                    private final CompletableFuture<ExitInfo> exit = new CompletableFuture<>();
                    @Override public SessionState state() { return SessionState.EXITED; }
                    @Override public CompletableFuture<ExitInfo> exit() { return exit; }
                    @Override public void stop() {}
                    @Override public Path stagingDirectory() { return tempDir.resolve("staging"); }
                    @Override public boolean interactive() { return true; }
                    @Override public void sendInput(String text) { typed.append(text); }
                    @Override public void onOutput(java.util.function.Consumer<String> listener) {
                        listener.accept("Name? ");
                        exit.complete(ExitInfo.success(Duration.ofMillis(5)));
                    }
                };
            }
        };
        var events = new ArrayList<RunEvent>();
        RunProject runProject = new RunProject(
                repo, tools, List.of(fakeEnv), List.of(toolchain), workspace, builder, tempDir.resolve("staging"));

        runProject.execute(projectRoot, "debug", CancellationToken.NONE, event -> {
            events.add(event);
            if (event instanceof RunEvent.SessionAttached attached) {
                attached.session().sendInput("Ana\n");
            }
        });

        assertEquals("Ana\n", typed.toString());
        assertTrue(events.contains(new RunEvent.ProgramOutput("Name? ")));
        assertInstanceOf(RunEvent.Exited.class, events.getLast());
    }

    @Test
    void rebuildsBeforeRunningWhenASourceChangedSinceTheLastBuild() throws IOException {
        Path projectRoot = tempDir.resolve("project-stale");
        Project project = createSampleProject();
        PublishedBuild.fresh(projectRoot, project, createFakeProvider(), "debug");
        PublishedBuild.edit(projectRoot.resolve("src/main.asm"));

        ProjectRepository repo = new ProjectRepository() {
            @Override public Project load(Path path) { return project; }
            @Override public void save(Path path, Project p) {}
        };
        ToolRegistry tools = id -> Optional.of(new ToolInstallation(id, "1.0", Path.of("fake.exe"), HostKind.WIN64, Map.of()));
        java.util.concurrent.atomic.AtomicInteger builds = new java.util.concurrent.atomic.AtomicInteger();
        ToolRunner runner = (plan, root, tc, cancellation, timeout) -> {
            builds.incrementAndGet();
            return new ToolRunResult(BuildStatus.SUCCEEDED, List.of(), Path.of("temp-out"), "Assembling main.asm");
        };
        BuildProject builder = new BuildProject(repo, tools, List.of(createFakeProvider()), createFakeWorkspace(),
                runner, Duration.ofSeconds(5));
        ExecutionEnvironmentProvider fakeEnv = new ExecutionEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public IsolationLevel isolation() { return IsolationLevel.EMULATED; }
            @Override public Set<EnvCapability> capabilities() { return Set.of(EnvCapability.WINDOW); }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public ExecutionSession launch(LaunchSpec spec) {
                return new ExecutionSession() {
                    @Override public SessionState state() { return SessionState.EXITED; }
                    @Override public CompletableFuture<ExitInfo> exit() {
                        return CompletableFuture.completedFuture(ExitInfo.success(Duration.ofMillis(1)));
                    }
                    @Override public void stop() {}
                    @Override public Path stagingDirectory() { return tempDir.resolve("staging"); }
                };
            }
        };
        var output = new java.util.ArrayList<String>();

        new RunProject(repo, tools, List.of(fakeEnv), List.of(createFakeProvider()), createFakeWorkspace(), builder,
                tempDir.resolve("staging")).execute(projectRoot, "debug", CancellationToken.NONE, event -> {
                    if (event instanceof RunEvent.Output line) {
                        output.add(line.text());
                    }
                });

        assertEquals(1, builds.get(), "An edited source must be rebuilt before it runs");
        assertTrue(output.contains("Assembling main.asm"), "The build log reaches the run output: " + output);
    }

    @Test
    void runsAnUpToDateProgramWithoutRebuilding() throws IOException {
        Path projectRoot = tempDir.resolve("project-fresh");
        Project project = createSampleProject();
        PublishedBuild.fresh(projectRoot, project, createFakeProvider(), "debug");
        ProjectRepository repo = new ProjectRepository() {
            @Override public Project load(Path path) { return project; }
            @Override public void save(Path path, Project p) {}
        };
        ToolRegistry tools = id -> Optional.of(new ToolInstallation(id, "1.0", Path.of("fake.exe"), HostKind.WIN64, Map.of()));
        ToolRunner runner = (plan, root, tc, cancellation, timeout) -> {
            throw new AssertionError("An up-to-date program must not be rebuilt");
        };
        BuildProject builder = new BuildProject(repo, tools, List.of(createFakeProvider()), createFakeWorkspace(),
                runner, Duration.ofSeconds(5));
        var launchedExecutable = new java.util.concurrent.atomic.AtomicReference<Path>();
        ExecutionEnvironmentProvider fakeEnv = new ExecutionEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public IsolationLevel isolation() { return IsolationLevel.EMULATED; }
            @Override public Set<EnvCapability> capabilities() { return Set.of(EnvCapability.WINDOW); }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public ExecutionSession launch(LaunchSpec spec) {
                launchedExecutable.set(spec.executable());
                return new ExecutionSession() {
                    @Override public SessionState state() { return SessionState.EXITED; }
                    @Override public CompletableFuture<ExitInfo> exit() {
                        return CompletableFuture.completedFuture(ExitInfo.success(Duration.ofMillis(1)));
                    }
                    @Override public void stop() {}
                    @Override public Path stagingDirectory() { return tempDir.resolve("staging"); }
                };
            }
        };

        RunResult result = new RunProject(repo, tools, List.of(fakeEnv), List.of(createFakeProvider()),
                createFakeWorkspace(), builder, tempDir.resolve("staging")).execute(projectRoot, "debug");

        assertEquals(SessionState.EXITED, result.state());
        assertEquals(projectRoot.resolve("build/debug/bin/main.exe"), launchedExecutable.get());
    }

    private Project projectWithEnvironment(String environment) {
        return new Project(
                1,
                new ProjectInfo("SAMPLE", "1.0.0"),
                new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection("borland-tasm", ">=3.2"),
                new Sources("src/main.asm", List.of(), List.of(), List.of()),
                new Resources(List.of()),
                Map.of("debug", BuildConfiguration.debug()),
                new RunConfiguration(environment, "required", true, "auto", 16, List.of()),
                new DebugConfiguration("external"),
                new DistConfiguration(true, false)
        );
    }

    /** A DOSBox environment whose launch records which tool installation it was handed, then exits at once. */
    private ExecutionEnvironmentProvider dosboxEnv(java.util.function.Consumer<LaunchSpec> onLaunch) {
        return new ExecutionEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public IsolationLevel isolation() { return IsolationLevel.EMULATED; }
            @Override public Set<EnvCapability> capabilities() { return Set.of(EnvCapability.WINDOW); }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public ExecutionSession launch(LaunchSpec spec) {
                onLaunch.accept(spec);
                return new ExecutionSession() {
                    @Override public SessionState state() { return SessionState.EXITED; }
                    @Override public CompletableFuture<ExitInfo> exit() {
                        return CompletableFuture.completedFuture(ExitInfo.success(Duration.ofMillis(1)));
                    }
                    @Override public void stop() {}
                    @Override public Path stagingDirectory() { return tempDir.resolve("staging"); }
                };
            }
        };
    }

    private RunResult runWith(Path projectRoot, Project project, ToolRegistry tools,
                              java.util.function.Consumer<LaunchSpec> onLaunch) throws IOException {
        PublishedBuild.fresh(projectRoot, project, createFakeProvider(), "debug");
        ProjectRepository repo = new ProjectRepository() {
            @Override public Project load(Path path) { return project; }
            @Override public void save(Path path, Project p) {}
        };
        ToolRunner runner = (plan, root, tc, cancellation, timeout) ->
                new ToolRunResult(BuildStatus.SUCCEEDED, List.of(), Path.of("temp-out"), "Build OK");
        BuildProject builder = new BuildProject(repo, tools, List.of(createFakeProvider()), createFakeWorkspace(),
                runner, Duration.ofSeconds(5));
        return new RunProject(repo, tools, List.of(dosboxEnv(onLaunch)), List.of(createFakeProvider()),
                createFakeWorkspace(), builder, tempDir.resolve("staging")).execute(projectRoot, "debug");
    }

    @Test
    void honoursASpecificDialectPreferenceWhenInstalled() throws IOException {
        // The registry echoes back whichever id is asked for, so the resolved toolId shows what was picked.
        ToolRegistry tools = id -> Optional.of(new ToolInstallation(id, "1.0", Path.of(id + ".exe"), HostKind.WIN64, Map.of()));
        var resolved = new java.util.concurrent.atomic.AtomicReference<String>();
        runWith(tempDir.resolve("project-pref"), projectWithEnvironment("dosbox-0.74"), tools,
                spec -> resolved.set(spec.environmentInstallation().toolId()));
        assertEquals("dosbox-0.74", resolved.get(), "Run must use the DOSBox dialect the project selected");
    }

    @Test
    void fallsBackToAutoWhenThePreferredDialectIsNotInstalled() throws IOException {
        // Only the generic "dosbox" auto-pick is registered (here backed by a dosbox-x install); 0.74 is absent.
        ToolRegistry tools = id -> "dosbox".equalsIgnoreCase(id)
                ? Optional.of(new ToolInstallation("dosbox-x", "1.0", Path.of("dosbox-x.exe"), HostKind.WIN64, Map.of()))
                : Optional.empty();
        var resolved = new java.util.concurrent.atomic.AtomicReference<String>();
        runWith(tempDir.resolve("project-fallback"), projectWithEnvironment("dosbox-0.74"), tools,
                spec -> resolved.set(spec.environmentInstallation().toolId()));
        assertEquals("dosbox-x", resolved.get(), "A missing preferred dialect falls back to the auto-pick");
    }

    @Test
    void aCorruptedEnvironmentValueNeverBorrowsAnUnrelatedToolsExecutable() throws IOException {
        // A hand-edited or stale environment such as "tasm" must not resolve the TASM assembler as the emulator.
        ToolRegistry tools = id -> switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "tasm" -> Optional.of(new ToolInstallation("tasm", "4.1", Path.of("tasm.exe"), HostKind.DOS_REAL, Map.of()));
            case "dosbox" -> Optional.of(new ToolInstallation("dosbox", "0.74", Path.of("dosbox.exe"), HostKind.WIN64, Map.of()));
            default -> Optional.empty();
        };
        var resolved = new java.util.concurrent.atomic.AtomicReference<String>();
        runWith(tempDir.resolve("project-corrupt"), projectWithEnvironment("tasm"), tools,
                spec -> resolved.set(spec.environmentInstallation().toolId()));
        assertEquals("dosbox", resolved.get(), "A non-DOSBox environment value falls back to the DOSBox auto-pick");
    }

    @Test
    void valuesThatWouldInjectDosBoxCommandsNeverReachTheEmulator() throws IOException {
        Project base = projectWithEnvironment("dosbox");
        Project project = new Project(base.schema(), base.info(), base.target(), base.toolchain(), base.sources(),
                base.resources(), base.build(),
                new RunConfiguration("dosbox", "required", true, "auto\r\n[autoexec]\r\nmount d c:\\", 16, List.of()),
                base.debug(), base.dist());
        ToolRegistry tools = id -> Optional.of(new ToolInstallation(id, "1.0", Path.of(id + ".exe"), HostKind.WIN64, Map.of()));
        var launched = new AtomicBoolean(false);

        RunResult result = runWith(tempDir.resolve("project-injection"), project, tools, spec -> launched.set(true));

        assertFalse(launched.get(), "Nothing may be written or launched with such a value");
        assertEquals(SessionState.FAILED, result.state());
        assertEquals("run.cycles.invalid", result.diagnostics().getFirst().code());
    }

    @Test
    void aLongResourceNameSuggestsDosBoxXWithOtherDialects() throws IOException {
        Path projectRoot = tempDir.resolve("project-lfn");
        Files.createDirectories(projectRoot.resolve("assets"));
        Files.writeString(projectRoot.resolve("assets/inv_bottom.spr"), "sprite");
        Project base = projectWithEnvironment("dosbox");
        Project project = new Project(base.schema(), base.info(), base.target(), base.toolchain(), base.sources(),
                new Resources(List.of("assets/inv_bottom.spr")), base.build(), base.run(), base.debug(), base.dist());
        ToolRegistry classic = id -> Optional.of(new ToolInstallation("dosbox-0.74", "0.74-3", Path.of("dosbox.exe"),
                HostKind.WIN32_CONSOLE, Map.of()));

        RunResult result = runWith(projectRoot, project, classic, spec -> {});

        assertEquals(SessionState.EXITED, result.state());
        assertEquals("run.resource.lfn", result.diagnostics().getFirst().code());
        assertEquals(io.github.dinamo541.idearm.domain.diagnostic.Severity.INFO, result.diagnostics().getFirst().severity());
    }

    @Test
    void stopsRunningSessionOnCancellation() throws IOException {
        Path projectRoot = tempDir.resolve("project-cancel");
        Project project = createSampleProject();
        PublishedBuild.fresh(projectRoot, project, createFakeProvider(), "debug");
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

        AtomicBoolean cancelRequested = new AtomicBoolean(false);
        CancellationToken cancellation = cancelRequested::get;
        AtomicBoolean stopped = new AtomicBoolean(false);
        ExecutionEnvironmentProvider fakeEnv = new ExecutionEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public IsolationLevel isolation() { return IsolationLevel.EMULATED; }
            @Override public Set<EnvCapability> capabilities() { return Set.of(EnvCapability.WINDOW); }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public ExecutionSession launch(LaunchSpec spec) {
                cancelRequested.set(true);
                return new ExecutionSession() {
                    private final CompletableFuture<ExitInfo> exit = new CompletableFuture<>();
                    @Override public SessionState state() { return stopped.get() ? SessionState.STOPPED : SessionState.RUNNING; }
                    @Override public CompletableFuture<ExitInfo> exit() { return exit; }
                    @Override public void stop() {
                        stopped.set(true);
                        exit.complete(ExitInfo.stopped(Duration.ofMillis(50)));
                    }
                    @Override public Path stagingDirectory() { return tempDir.resolve("staging"); }
                };
            }
        };

        RunProject runProject = new RunProject(
                repo, tools, List.of(fakeEnv), List.of(toolchain), workspace, builder, tempDir.resolve("staging")
        );

        RunResult result = runProject.execute(projectRoot, "debug", cancellation, event -> {});

        assertTrue(stopped.get());
        assertEquals(SessionState.STOPPED, result.state());
        assertTrue(result.exitInfo().wasStopped());
    }
}
