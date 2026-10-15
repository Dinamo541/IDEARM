package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.*;
import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.DebugEvent;
import io.github.dinamo541.idearm.domain.debug.DebugLaunchSpec;
import io.github.dinamo541.idearm.domain.debug.RegisterState;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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

    private Project createEmuProject() {
        return new Project(
                1,
                new ProjectInfo("SAMPLE", "1.0.0"),
                new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection("borland-tasm", ">=3.2"),
                new Sources("src/main.asm", List.of(), List.of(), List.of()),
                new Resources(List.of()),
                Map.of("debug", BuildConfiguration.debug()),
                new RunConfiguration("dosbox", "required", true, "auto", 16, List.of()),
                new DebugConfiguration("emu8086"),
                new DistConfiguration(true, false)
        );
    }

    private DebugProject createDebugProject(Path projectRoot, Project project, DebugEnvironmentProvider debugEnv) throws IOException {
        PublishedBuild.fresh(projectRoot, project, createFakeProvider(), "debug");

        ProjectRepository repo = new ProjectRepository() {
            @Override public Project load(Path root) { return project; }
            @Override public void save(Path root, Project p) {}
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
                if (toolId.equals("emu8086")) {
                    return Optional.of(new ToolInstallation("emu8086", "1.0", tempDir.resolve("emu8086"), HostKind.WIN32_CONSOLE, Map.of()));
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

        return new DebugProject(
                repo, tools, List.of(debugEnv), List.of(toolchain),
                createFakeWorkspace(), builder, bpStore, tempDir.resolve("staging")
        );
    }

    private DebugProject createDebugProject(Path projectRoot, DebugEnvironmentProvider debugEnv) throws IOException {
        return createDebugProject(projectRoot, createSampleProject(), debugEnv);
    }

    @Test
    void reproducesAndResolvesCodexHelloInputDuplicateExitBug() throws IOException {
        Path projectRoot = tempDir.resolve("proj-hello-input");
        AtomicBoolean launched = new AtomicBoolean(false);

        DebugEnvironmentProvider emuEnv = new DebugEnvironmentProvider() {
            @Override public String id() { return "emu8086"; }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec, Consumer<DebugEvent> events) {
                launched.set(true);
                return new DebugSession() {
                    private final CompletableFuture<ExitInfo> exitFuture = new CompletableFuture<>();
                    private SessionState state = SessionState.RUNNING;

                    {
                        // Simulate hello-input execution sequence:
                        events.accept(new DebugEvent.Paused("src/main.asm", 1, RegisterState.initialDosState()));
                        events.accept(new DebugEvent.Resumed());
                        events.accept(new DebugEvent.Output("What is your name? "));
                        events.accept(new DebugEvent.WaitingForInput());
                    }

                    @Override
                    public void sendInput(String text) {
                        events.accept(new DebugEvent.Output(text + "\n"));
                        events.accept(new DebugEvent.Output("Hello, " + text.trim() + "!\n"));
                        state = SessionState.EXITED;
                        ExitInfo exitInfo = ExitInfo.success(Duration.ofMillis(35));
                        // The exact sequence in Emu8086DebugSession.checkTermination:
                        exitFuture.complete(exitInfo);
                        events.accept(new DebugEvent.Exited(exitInfo));
                    }

                    @Override public CompletableFuture<ExitInfo> exit() { return exitFuture; }
                    @Override public SessionState state() { return state; }
                    @Override public void stop() {}
                    @Override public void close() {}
                };
            }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec) {
                return launchDebug(spec, e -> {});
            }
        };

        DebugProject debugProject = createDebugProject(projectRoot, createEmuProject(), emuEnv);
        List<DebugEvent> events = new CopyOnWriteArrayList<>();

        Consumer<DebugEvent> eventConsumer = event -> {
            events.add(event);
            if (event instanceof DebugEvent.SessionAttached attached) {
                CompletableFuture.runAsync(() -> attached.session().sendInput("Alex"));
            }
        };

        DebugResult result = debugProject.execute(projectRoot, CancellationToken.NONE, eventConsumer);

        assertTrue(launched.get());
        assertEquals(SessionState.EXITED, result.state());
        assertNotNull(result.exitInfo());
        assertEquals(0, result.exitInfo().exitCode());

        // Single terminal-event contract: exactly ONE DebugEvent.Exited delivered downstream
        long exitEventCount = events.stream().filter(DebugEvent.Exited.class::isInstance).count();
        assertEquals(1, exitEventCount, "Single terminal-event contract violated: duplicate exit events detected");

        long stoppedEventCount = events.stream().filter(DebugEvent.Stopped.class::isInstance).count();
        assertEquals(0, stoppedEventCount, "Normal exit must not emit Stopped event");

        assertTrue(events.getFirst() instanceof DebugEvent.Started);
        // A backend may emit its initial pause/output during launch, before it returns the session.
        assertEquals(1, events.stream().filter(DebugEvent.SessionAttached.class::isInstance).count());
        assertTrue(events.stream().anyMatch(event -> event instanceof DebugEvent.Output output
                && output.text().contains("Hello, Alex!")));
        assertTrue(events.getLast() instanceof DebugEvent.Exited);
    }

    @Test
    void silentBackendEmitsTerminalExitedEventOnce() throws IOException {
        Path projectRoot = tempDir.resolve("proj-silent");
        AtomicBoolean launched = new AtomicBoolean(false);

        DebugEnvironmentProvider silentEnv = new DebugEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec) {
                launched.set(true);
                return new DebugSession() {
                    @Override public CompletableFuture<ExitInfo> exit() {
                        return CompletableFuture.completedFuture(new ExitInfo(0, Duration.ofMillis(80), false, "OK"));
                    }
                    @Override public SessionState state() { return SessionState.EXITED; }
                    @Override public void stop() {}
                    @Override public void close() {}
                };
            }
        };

        DebugProject debugProject = createDebugProject(projectRoot, silentEnv);
        List<DebugEvent> events = new CopyOnWriteArrayList<>();

        DebugResult result = debugProject.execute(projectRoot, CancellationToken.NONE, events::add);

        assertTrue(launched.get());
        assertEquals(SessionState.EXITED, result.state());
        assertEquals(0, result.exitInfo().exitCode());

        long exitCount = events.stream().filter(DebugEvent.Exited.class::isInstance).count();
        assertEquals(1, exitCount, "Silent backend must receive exactly one synthesized Exited event");

        long stopCount = events.stream().filter(DebugEvent.Stopped.class::isInstance).count();
        assertEquals(0, stopCount);
    }

    @Test
    void lateBackendEventsAfterFutureCompletionAreSafelyHandledWithoutSleeps() throws Exception {
        Path projectRoot = tempDir.resolve("proj-late-events");
        ExitInfo exitInfo = ExitInfo.success(Duration.ofMillis(50));
        CompletableFuture<ExitInfo> exitFuture = new CompletableFuture<>();
        List<DebugEvent> events = new CopyOnWriteArrayList<>();

        AtomicReference<Consumer<DebugEvent>> capturedSink = new AtomicReference<>();
        DebugEnvironmentProvider capturingEnv = new DebugEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec, Consumer<DebugEvent> sessionEvents) {
                capturedSink.set(sessionEvents);
                return new DebugSession() {
                    @Override public CompletableFuture<ExitInfo> exit() { return exitFuture; }
                    @Override public SessionState state() { return SessionState.EXITED; }
                    @Override public void stop() {}
                    @Override public void close() {}
                };
            }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec) {
                return launchDebug(spec, e -> {});
            }
        };

        DebugProject debugProject = createDebugProject(projectRoot, capturingEnv);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<DebugResult> executeFuture = pool.submit(() ->
                    debugProject.execute(projectRoot, CancellationToken.NONE, events::add));

            // Complete the exit future on backend side
            exitFuture.complete(exitInfo);

            // Wait until executeFuture completes DebugProject.execute
            DebugResult result = executeFuture.get(5, TimeUnit.SECONDS);
            assertEquals(SessionState.EXITED, result.state());

            // Simulate late events arriving from a delayed backend thread after future completion and execute return
            Consumer<DebugEvent> sink = capturedSink.get();
            assertNotNull(sink);
            sink.accept(new DebugEvent.Output("Delayed stdout from background\n"));
            sink.accept(new DebugEvent.Exited(exitInfo));
            sink.accept(new DebugEvent.Stopped());

            // Single terminal event contract: exactly ONE Exited event, zero Stopped, late events safely dropped
            long exitCount = events.stream().filter(DebugEvent.Exited.class::isInstance).count();
            assertEquals(1, exitCount, "Late backend event must not duplicate Exited");

            long stoppedCount = events.stream().filter(DebugEvent.Stopped.class::isInstance).count();
            assertEquals(0, stoppedCount, "Late backend event must not introduce spurious Stopped");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void emittingBackendStopEmitsSingleStoppedEventAndNoExited() throws IOException {
        Path projectRoot = tempDir.resolve("proj-emitting-stop");
        AtomicBoolean stopCalled = new AtomicBoolean(false);

        DebugEnvironmentProvider emuEnv = new DebugEnvironmentProvider() {
            @Override public String id() { return "emu8086"; }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec, Consumer<DebugEvent> events) {
                return new DebugSession() {
                    private final CompletableFuture<ExitInfo> exitFuture = new CompletableFuture<>();
                    private SessionState state = SessionState.RUNNING;

                    @Override
                    public void stop() {
                        if (stopCalled.compareAndSet(false, true)) {
                            state = SessionState.STOPPED;
                            ExitInfo stoppedInfo = ExitInfo.stopped(Duration.ofMillis(20));
                            exitFuture.complete(stoppedInfo);
                            events.accept(new DebugEvent.Stopped());
                        }
                    }

                    @Override public CompletableFuture<ExitInfo> exit() { return exitFuture; }
                    @Override public SessionState state() { return state; }
                    @Override public void close() { stop(); }
                };
            }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec) {
                return launchDebug(spec, e -> {});
            }
        };

        DebugProject debugProject = createDebugProject(projectRoot, createEmuProject(), emuEnv);
        List<DebugEvent> events = new CopyOnWriteArrayList<>();

        AtomicBoolean cancelToken = new AtomicBoolean(false);
        CancellationToken cancellation = cancelToken::get;

        Consumer<DebugEvent> eventConsumer = event -> {
            events.add(event);
            if (event instanceof DebugEvent.SessionAttached) {
                cancelToken.set(true);
            }
        };

        DebugResult result = debugProject.execute(projectRoot, cancellation, eventConsumer);

        assertTrue(stopCalled.get(), "Session stop must have been called");
        assertEquals(SessionState.STOPPED, result.state());
        assertTrue(result.exitInfo().wasStopped());

        long stoppedCount = events.stream().filter(DebugEvent.Stopped.class::isInstance).count();
        assertEquals(1, stoppedCount, "Stopped session must receive exactly one Stopped event");

        long exitCount = events.stream().filter(DebugEvent.Exited.class::isInstance).count();
        assertEquals(0, exitCount, "Stopped session must never emit Exited event");
    }

    @Test
    void silentBackendCancellationEmitsSingleStoppedEvent() throws IOException {
        Path projectRoot = tempDir.resolve("proj-silent-cancel");
        AtomicBoolean stopCalled = new AtomicBoolean(false);

        DebugEnvironmentProvider silentEnv = new DebugEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec) {
                return new DebugSession() {
                    private final CompletableFuture<ExitInfo> exitFuture = new CompletableFuture<>();
                    private SessionState state = SessionState.RUNNING;

                    @Override
                    public void stop() {
                        stopCalled.set(true);
                        state = SessionState.STOPPED;
                        exitFuture.complete(ExitInfo.stopped(Duration.ofMillis(15)));
                    }

                    @Override public CompletableFuture<ExitInfo> exit() { return exitFuture; }
                    @Override public SessionState state() { return state; }
                    @Override public void close() { stop(); }
                };
            }
        };

        DebugProject debugProject = createDebugProject(projectRoot, silentEnv);
        List<DebugEvent> events = new CopyOnWriteArrayList<>();

        AtomicBoolean cancelToken = new AtomicBoolean(false);
        CancellationToken cancellation = cancelToken::get;

        Consumer<DebugEvent> eventConsumer = event -> {
            events.add(event);
            if (event instanceof DebugEvent.SessionAttached) {
                cancelToken.set(true);
            }
        };

        DebugResult result = debugProject.execute(projectRoot, cancellation, eventConsumer);

        assertTrue(stopCalled.get());
        assertEquals(SessionState.STOPPED, result.state());
        assertTrue(result.exitInfo().wasStopped());

        long stoppedCount = events.stream().filter(DebugEvent.Stopped.class::isInstance).count();
        assertEquals(1, stoppedCount, "Silent backend stopped must synthesize exactly one Stopped event");

        long exitCount = events.stream().filter(DebugEvent.Exited.class::isInstance).count();
        assertEquals(0, exitCount, "Silent backend stopped must not emit Exited event");
    }

    @Test
    void backendEmittingExitedFollowedByStoppedDeliversSingleTerminalEvent() throws IOException {
        Path projectRoot = tempDir.resolve("proj-gdb-mi");

        DebugEnvironmentProvider gdbStyleEnv = new DebugEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec, Consumer<DebugEvent> events) {
                return new DebugSession() {
                    private final CompletableFuture<ExitInfo> exitFuture = new CompletableFuture<>();

                    {
                        ExitInfo exitInfo = ExitInfo.success(Duration.ofMillis(25));
                        exitFuture.complete(exitInfo);
                        // GDB MI emits Exited followed immediately by Stopped
                        events.accept(new DebugEvent.Exited(exitInfo));
                        events.accept(new DebugEvent.Stopped());
                    }

                    @Override public CompletableFuture<ExitInfo> exit() { return exitFuture; }
                    @Override public SessionState state() { return SessionState.EXITED; }
                    @Override public void stop() {}
                    @Override public void close() {}
                };
            }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec) {
                return launchDebug(spec, e -> {});
            }
        };

        DebugProject debugProject = createDebugProject(projectRoot, gdbStyleEnv);
        List<DebugEvent> events = new CopyOnWriteArrayList<>();

        DebugResult result = debugProject.execute(projectRoot, CancellationToken.NONE, events::add);

        assertEquals(SessionState.EXITED, result.state());

        long exitCount = events.stream().filter(DebugEvent.Exited.class::isInstance).count();
        assertEquals(1, exitCount);

        long stoppedCount = events.stream().filter(DebugEvent.Stopped.class::isInstance).count();
        assertEquals(0, stoppedCount, "Secondary Stopped event after Exited must be suppressed");
    }

    @Test
    void interruptedWaitStopsSessionRestoresInterruptAndEmitsStoppedEvent() throws Exception {
        Path projectRoot = tempDir.resolve("proj-interrupt");
        AtomicBoolean stopCalled = new AtomicBoolean(false);
        CountDownLatch sessionRunningLatch = new CountDownLatch(1);

        DebugEnvironmentProvider hangingEnv = new DebugEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec) {
                return new DebugSession() {
                    private final CompletableFuture<ExitInfo> exitFuture = new CompletableFuture<>();
                    private SessionState state = SessionState.RUNNING;

                    @Override
                    public CompletableFuture<ExitInfo> exit() {
                        sessionRunningLatch.countDown();
                        return exitFuture;
                    }
                    @Override public SessionState state() { return state; }
                    @Override
                    public void stop() {
                        stopCalled.set(true);
                        state = SessionState.STOPPED;
                        exitFuture.complete(ExitInfo.stopped(Duration.ofMillis(10)));
                    }
                    @Override public void close() { stop(); }
                };
            }
        };

        DebugProject debugProject = createDebugProject(projectRoot, hangingEnv);
        List<DebugEvent> events = new CopyOnWriteArrayList<>();
        AtomicBoolean threadInterruptedResult = new AtomicBoolean(false);
        AtomicReference<DebugResult> resultRef = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            DebugResult r = debugProject.execute(projectRoot, CancellationToken.NONE, events::add);
            resultRef.set(r);
            threadInterruptedResult.set(Thread.currentThread().isInterrupted());
            // Clear interrupt status so worker exits cleanly
            Thread.interrupted();
        });

        worker.start();
        assertTrue(sessionRunningLatch.await(5, TimeUnit.SECONDS));

        // Interrupt worker while waiting on session exit
        worker.interrupt();
        worker.join(5000);

        assertTrue(stopCalled.get(), "Session stop must have been called on thread interrupt");
        assertTrue(threadInterruptedResult.get(), "Thread interrupt status must be restored");
        assertNotNull(resultRef.get());
        assertEquals(SessionState.STOPPED, resultRef.get().state());

        long stoppedCount = events.stream().filter(DebugEvent.Stopped.class::isInstance).count();
        assertEquals(1, stoppedCount);
    }

    @Test
    void preCancelledTokenReturnsStoppedResultWithoutLaunchingSession() throws IOException {
        Path projectRoot = tempDir.resolve("proj-precancel");
        AtomicBoolean launched = new AtomicBoolean(false);

        DebugEnvironmentProvider env = new DebugEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec) {
                launched.set(true);
                throw new AssertionError("Should not launch when pre-cancelled");
            }
        };

        DebugProject debugProject = createDebugProject(projectRoot, env);
        CancellationToken alreadyCancelled = () -> true;

        List<DebugEvent> events = new ArrayList<>();
        DebugResult result = debugProject.execute(projectRoot, alreadyCancelled, events::add);

        assertFalse(launched.get());
        assertEquals(SessionState.STOPPED, result.state());
        assertTrue(result.exitInfo().wasStopped());
        assertTrue(events.isEmpty(), "No events should be emitted if cancelled before starting");
    }

    @Test
    void buildFailureReturnsFailedResultWithoutLaunchingSession() throws IOException {
        Path projectRoot = tempDir.resolve("proj-build-fail");
        AtomicBoolean launched = new AtomicBoolean(false);

        ProjectRepository repo = new ProjectRepository() {
            @Override public Project load(Path root) { return createSampleProject(); }
            @Override public void save(Path root, Project project) {}
            @Override public boolean exists(Path root) { return true; }
        };
        ToolRegistry tools = id -> Optional.of(new ToolInstallation(id, "1.0", tempDir.resolve("fake.exe"), HostKind.WIN64, Map.of()));
        ToolchainProvider toolchain = createFakeProvider();

        ToolRunner failingRunner = (plan, root, resolved, cancellation, timeout) -> {
            var step = new ToolResult(plan.steps().getFirst(), 1, "Syntax error at line 10");
            return new ToolRunResult(BuildStatus.FAILED, List.of(step), Path.of("temp-out"), "Build Failed");
        };

        BuildProject failingBuilder = new BuildProject(repo, tools, List.of(toolchain), createFakeWorkspace(), failingRunner, Duration.ofSeconds(5));

        DebugEnvironmentProvider env = new DebugEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec) {
                launched.set(true);
                throw new AssertionError("Should not launch when build fails");
            }
        };

        DebugProject debugProject = new DebugProject(
                repo, tools, List.of(env), List.of(toolchain),
                createFakeWorkspace(), failingBuilder,
                new BreakpointStore() {
                    @Override public List<Breakpoint> loadBreakpoints(Path root) { return List.of(); }
                    @Override public void saveBreakpoints(Path root, List<Breakpoint> breakpoints) {}
                },
                tempDir.resolve("staging")
        );

        List<DebugEvent> events = new ArrayList<>();
        DebugResult result = debugProject.execute(projectRoot, CancellationToken.NONE, events::add);

        assertFalse(launched.get());
        assertEquals(SessionState.FAILED, result.state());
        assertFalse(result.diagnostics().isEmpty());
    }

    @Test
    void exceptionalExitFutureReportsFailureRatherThanStop() throws IOException {
        Path projectRoot = tempDir.resolve("proj-exceptional-future");
        CompletableFuture<ExitInfo> exitFuture = new CompletableFuture<>();

        DebugEnvironmentProvider env = new DebugEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec) {
                exitFuture.completeExceptionally(new RuntimeException("DosBox crashed unexpectedly"));
                return new DebugSession() {
                    @Override public CompletableFuture<ExitInfo> exit() { return exitFuture; }
                    @Override public SessionState state() { return SessionState.RUNNING; }
                    @Override public void stop() {}
                    @Override public void close() {}
                };
            }
        };

        DebugProject debugProject = createDebugProject(projectRoot, env);
        List<DebugEvent> events = new CopyOnWriteArrayList<>();
        DebugResult result = debugProject.execute(projectRoot, CancellationToken.NONE, events::add);

        assertEquals(SessionState.FAILED, result.state(), "Exceptional exit future must report FAILED, not STOPPED");
        assertNotNull(result.exitInfo());
        assertEquals(1, result.exitInfo().exitCode());
        assertFalse(result.exitInfo().wasStopped(), "ExitInfo must not indicate user stop");
        assertTrue(result.diagnostics().stream().anyMatch(d -> "debug.session.failed".equals(d.code())
                && d.message().contains("DosBox crashed unexpectedly")), "Actionable diagnostic must be preserved in result");

        assertTrue(events.stream().anyMatch(e -> e instanceof DebugEvent.Problem p
                && "debug.session.failed".equals(p.diagnostic().code())), "DebugEvent.Problem must be emitted");
        assertTrue(events.stream().anyMatch(e -> e instanceof DebugEvent.Exited exited
                && exited.exitInfo().exitCode() == 1), "Terminal event must be DebugEvent.Exited with error code");
        assertEquals(0, events.stream().filter(e -> e instanceof DebugEvent.Stopped).count(),
                "No Stopped events must be emitted for backend crash");
    }

    @Test
    void cancellationAfterCompletionPreservesExitedStatus() throws IOException {
        Path projectRoot = tempDir.resolve("proj-cancel-after-complete");
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CancellationToken cancellation = cancelled::get;

        DebugEnvironmentProvider env = new DebugEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec) {
                return new DebugSession() {
                    @Override public CompletableFuture<ExitInfo> exit() {
                        CompletableFuture<ExitInfo> f = CompletableFuture.completedFuture(
                                ExitInfo.success(Duration.ofMillis(50)));
                        cancelled.set(true);
                        return f;
                    }
                    @Override public SessionState state() { return SessionState.EXITED; }
                    @Override public void stop() {}
                    @Override public void close() {}
                };
            }
        };

        DebugProject debugProject = createDebugProject(projectRoot, env);
        List<DebugEvent> events = new CopyOnWriteArrayList<>();
        DebugResult result = debugProject.execute(projectRoot, cancellation, events::add);

        assertEquals(SessionState.EXITED, result.state(), "Late cancellation must not turn completed session into STOPPED");
        assertNotNull(result.exitInfo());
        assertEquals(0, result.exitInfo().exitCode());
        assertFalse(result.exitInfo().wasStopped());

        assertTrue(events.stream().anyMatch(e -> e instanceof DebugEvent.Exited exited && exited.exitInfo().exitCode() == 0),
                "Terminal event must be Exited(0)");
        assertEquals(0, events.stream().filter(e -> e instanceof DebugEvent.Stopped).count(),
                "Zero Stopped events must be emitted when session already completed before cancellation");
    }

    @Test
    void sessionCloseThrowingReportsFailureAndAgreesWithTerminalEvent() throws IOException {
        Path projectRoot = tempDir.resolve("proj-close-throwing");

        DebugEnvironmentProvider env = new DebugEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec) {
                return new DebugSession() {
                    @Override public CompletableFuture<ExitInfo> exit() {
                        return CompletableFuture.completedFuture(ExitInfo.success(Duration.ofMillis(10)));
                    }
                    @Override public SessionState state() { return SessionState.EXITED; }
                    @Override public void stop() {}
                    @Override public void close() {
                        throw new RuntimeException("Teardown resource lock failed");
                    }
                };
            }
        };

        DebugProject debugProject = createDebugProject(projectRoot, env);
        List<DebugEvent> events = new CopyOnWriteArrayList<>();
        DebugResult result = debugProject.execute(projectRoot, CancellationToken.NONE, events::add);

        assertEquals(SessionState.FAILED, result.state(), "Session must report FAILED when close() throws");
        assertTrue(result.diagnostics().stream().anyMatch(d -> "debug.close.failed".equals(d.code())
                && d.message().contains("Teardown resource lock failed")), "Diagnostic for close failure must be preserved in result");

        assertTrue(events.stream().anyMatch(e -> e instanceof DebugEvent.Problem p
                && "debug.close.failed".equals(p.diagnostic().code())), "DebugEvent.Problem must be emitted for close failure");
        assertTrue(events.stream().anyMatch(e -> e instanceof DebugEvent.Exited),
                "Terminal event must be Exited, agreeing with FAILED state");
        assertEquals(0, events.stream().filter(e -> e instanceof DebugEvent.Stopped).count(),
                "No Stopped events must be emitted on close failure");
    }

    @Test
    void launchFailureReportsFailureAndEmitsMatchingTerminalEvent() throws IOException {
        Path projectRoot = tempDir.resolve("proj-launch-fail");

        DebugEnvironmentProvider env = new DebugEnvironmentProvider() {
            @Override public String id() { return "dosbox"; }
            @Override public boolean supports(TargetProfile profile) { return true; }
            @Override public DebugSession launchDebug(DebugLaunchSpec spec) {
                throw new DomainException("debug.tool.missing", "Debugger binary not executable");
            }
        };

        DebugProject debugProject = createDebugProject(projectRoot, env);
        List<DebugEvent> events = new CopyOnWriteArrayList<>();
        DebugResult result = debugProject.execute(projectRoot, CancellationToken.NONE, events::add);

        assertEquals(SessionState.FAILED, result.state(), "Launch failure must result in FAILED state");
        assertTrue(result.diagnostics().stream().anyMatch(d -> "debug.tool.missing".equals(d.code())
                && d.message().contains("Debugger binary not executable")), "DomainException diagnostic must be preserved");

        assertTrue(events.stream().anyMatch(e -> e instanceof DebugEvent.Problem p
                && "debug.tool.missing".equals(p.diagnostic().code())), "DebugEvent.Problem must be emitted for launch failure");
        assertTrue(events.stream().anyMatch(e -> e instanceof DebugEvent.Exited exited
                && exited.exitInfo().exitCode() == 1), "Terminal event must be Exited(1)");
        assertEquals(0, events.stream().filter(e -> e instanceof DebugEvent.Stopped).count(),
                "No Stopped events must be emitted on launch failure");
    }

    @Test
    void lifecycleCoordinatorDirectUnitContract() throws Exception {
        List<DebugEvent> downstream = new CopyOnWriteArrayList<>();
        DebugProject.DebugLifecycleCoordinator coordinator = new DebugProject.DebugLifecycleCoordinator(downstream::add);

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        ExitInfo info = ExitInfo.success(Duration.ofMillis(10));

        for (int i = 0; i < threadCount; i++) {
            final int id = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (id % 2 == 0) {
                        coordinator.onSessionEvent(new DebugEvent.Exited(info));
                    } else {
                        coordinator.ensureTerminal(false, info);
                    }
                    coordinator.onSessionEvent(new DebugEvent.Output("late " + id));
                    coordinator.onSessionEvent(new DebugEvent.Stopped());
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals(1, downstream.size(), "Coordinator must deliver exactly one event under concurrent barrage");
        assertTrue(downstream.getFirst() instanceof DebugEvent.Exited);
        assertTrue(coordinator.isTerminalEmitted());
    }
}
