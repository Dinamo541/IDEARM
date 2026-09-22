package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.*;
import io.github.dinamo541.idearm.domain.model.*;
import io.github.dinamo541.idearm.domain.port.ProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HostProcessToolRunnerTest {

    @TempDir
    Path project;

    @TempDir
    Path staging;

    private static final ToolInvocation ASSEMBLE = new ToolInvocation("nasm", BuildPhase.ASSEMBLE, HostKind.WIN64,
            List.of("-f", "win64", "-o", "obj/main.obj", "src/main.asm"), null, null, List.of("obj/main.obj"));
    private static final ToolInvocation LINK = new ToolInvocation("ld", BuildPhase.LINK, HostKind.WIN64,
            List.of("-o", "bin/main.exe", "-Map=bin/main.map", "obj/main.obj"), null, null,
            List.of("bin/main.exe", "bin/main.map"));
    private static final BuildPlan PLAN = new BuildPlan(null, "debug", null, List.of(ASSEMBLE, LINK),
            "bin/main.exe", List.of("obj/main.obj", "bin/main.exe", "bin/main.map"));

    private ResolvedToolchain tools() {
        return new ResolvedToolchain("nasm", Map.of(
                "nasm", new ToolInstallation("nasm", "3.01", project.resolve("nasm.exe"), HostKind.WIN64, Map.of(), "sha", "detected"),
                "ld", new ToolInstallation("ld", "2.46", project.resolve("ld.exe"), HostKind.WIN64, Map.of(), "sha", "detected")));
    }

    /** A fake tool that writes every file it was told to produce, as NASM and ld do. */
    private static ProcessExecutor writingOutputs(List<ProcessRequest> seen) {
        return (request, cancel) -> {
            seen.add(request);
            List<String> command = request.command();
            try {
                for (int i = 0; i < command.size(); i++) {
                    if (command.get(i).equals("-o")) {
                        write(Path.of(command.get(i + 1)));
                    } else if (command.get(i).startsWith("-Map=")) {
                        write(Path.of(command.get(i).substring("-Map=".length())));
                    }
                }
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            return new ProcessResult(0, "ok", false, false);
        };
    }

    private static void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
    }

    @Test
    void canRunIdentifiesHostInvocations() {
        assertTrue(HostProcessToolRunner.canRun(PLAN));

        ToolInvocation dosStep = new ToolInvocation("tasm", BuildPhase.ASSEMBLE, HostKind.DOS_REAL, List.of(), null, null, List.of());
        BuildPlan dosPlan = new BuildPlan(null, "debug", null, List.of(dosStep), "bin/main.exe", List.of());
        assertFalse(HostProcessToolRunner.canRun(dosPlan));
    }

    @Test
    void runsStepsFromTheProjectAndWritesOutputsOutsideIt() {
        var seen = new ArrayList<ProcessRequest>();
        var runner = new HostProcessToolRunner(writingOutputs(seen), staging);

        ToolRunResult result = runner.run(PLAN, project, tools(), () -> false, Duration.ofSeconds(10));

        assertEquals(BuildStatus.SUCCEEDED, result.status(), result.output());
        assertEquals(2, result.steps().size());
        Path session = result.outputDirectory();
        assertTrue(session.startsWith(staging));
        assertTrue(Files.isRegularFile(session.resolve("obj/main.obj")));
        assertTrue(Files.isRegularFile(session.resolve("bin/main.exe")));
        assertTrue(Files.isRegularFile(session.resolve("bin/main.map")));
        // Sources resolve from the project; outputs never land in it.
        assertEquals(project.toAbsolutePath().normalize(), seen.get(0).workingDirectory());
        assertTrue(seen.get(0).command().contains("src/main.asm"));
        assertFalse(Files.exists(project.resolve("obj")));
        assertFalse(Files.exists(project.resolve("bin")));
        assertTrue(seen.get(1).command().stream().anyMatch(a -> a.startsWith("-Map=") && a.contains(session.toString())));

        runner.release(result);
        assertFalse(Files.exists(session), "Release deletes the session");
    }

    @Test
    void failedAssemblySkipsTheLinkStep() {
        var seen = new ArrayList<ProcessRequest>();
        ProcessExecutor failing = (request, cancel) -> {
            seen.add(request);
            return new ProcessResult(1, "src/main.asm:3: error: parser: instruction expected", false, false);
        };
        var runner = new HostProcessToolRunner(failing, staging);

        ToolRunResult result = runner.run(PLAN, project, tools(), () -> false, Duration.ofSeconds(10));

        assertEquals(BuildStatus.FAILED, result.status());
        assertEquals(1, seen.size(), "ld must not run after NASM failed");
        assertTrue(result.output().contains("instruction expected"));
        runner.release(result);
    }

    @Test
    void aToolThatReportsSuccessWithoutItsOutputFailsTheBuild() {
        var runner = new HostProcessToolRunner((request, cancel) -> new ProcessResult(0, "", false, false), staging);

        ToolRunResult result = runner.run(PLAN, project, tools(), () -> false, Duration.ofSeconds(10));

        assertEquals(BuildStatus.FAILED, result.status());
        assertTrue(result.output().contains("expected output is missing"));
        runner.release(result);
    }

    @Test
    void cancellationBeforeTheFirstStepRunsNothing() {
        var seen = new ArrayList<ProcessRequest>();
        var runner = new HostProcessToolRunner(writingOutputs(seen), staging);

        ToolRunResult result = runner.run(PLAN, project, tools(), () -> true, Duration.ofSeconds(10));

        assertEquals(BuildStatus.CANCELLED, result.status());
        assertTrue(seen.isEmpty());
    }

    @Test
    void stagingInsideTheProjectIsRefused() {
        var runner = new HostProcessToolRunner(writingOutputs(new ArrayList<>()), project.resolve("tmp"));

        DomainException error = assertThrows(DomainException.class,
                () -> runner.run(PLAN, project, tools(), () -> false, Duration.ofSeconds(10)));
        assertEquals("build.unsafe-staging", error.code());
    }

    @Test
    void compositeRunnerDispatchesCorrectly() {
        var hostRunner = new HostProcessToolRunner(writingOutputs(new ArrayList<>()), staging);

        boolean[] fallbackCalled = new boolean[1];
        io.github.dinamo541.idearm.domain.port.ToolRunner fallback = (p, r, t, c, d) -> {
            fallbackCalled[0] = true;
            return new ToolRunResult(BuildStatus.SUCCEEDED, List.of(), null, "fallback", null);
        };

        CompositeToolRunner composite = new CompositeToolRunner(hostRunner, fallback);

        ToolRunResult hostResult = composite.run(PLAN, project, tools(), () -> false, Duration.ofSeconds(5));
        assertFalse(fallbackCalled[0]);
        composite.release(hostResult);
        assertFalse(Files.exists(hostResult.outputDirectory()));

        ToolInvocation dosStep = new ToolInvocation("tasm", BuildPhase.ASSEMBLE, HostKind.DOS_REAL, List.of(), null, null, List.of());
        BuildPlan dosPlan = new BuildPlan(null, "debug", null, List.of(dosStep), "bin/main.exe", List.of());
        composite.run(dosPlan, project, null, () -> false, Duration.ofSeconds(5));
        assertTrue(fallbackCalled[0]);
    }
}
