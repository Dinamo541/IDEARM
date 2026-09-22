package io.github.dinamo541.idearm.toolchain.dos;

import io.github.dinamo541.idearm.domain.build.BuildPhase;

import io.github.dinamo541.idearm.domain.build.*;
import io.github.dinamo541.idearm.domain.model.*;
import io.github.dinamo541.idearm.domain.port.ProcessExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HybridToolRunnerTest {

    @Test
    void executesPureDosBuildByDelegation(@TempDir Path tempDir) throws IOException {
        Path projectRoot = Files.createDirectory(tempDir.resolve("proj"));
        Path stagingRoot = Files.createDirectory(tempDir.resolve("staging"));
        Path src = Files.createDirectory(projectRoot.resolve("src"));
        Files.writeString(src.resolve("MAIN.ASM"), "; test");

        Path tasmExe = Files.createFile(tempDir.resolve("TASM.EXE"));
        Path tlinkExe = Files.createFile(tempDir.resolve("TLINK.EXE"));
        Path dbxExe = Files.createFile(tempDir.resolve("DOSBOX.EXE"));

        ProcessExecutor processes = (req, cancel) -> {
            // Emulate DOSBox running BUILD.BAT and producing sentinel and logs
            Path driveC = req.workingDirectory().resolve("C");
            try {
                Files.writeString(driveC.resolve("LOG/DONE.TXT"), "DONE\n");
                Files.writeString(driveC.resolve("LOG/S001.RC"), "RC=0\n");
                Files.writeString(driveC.resolve("LOG/S001.LOG"), "Assembled.\n");
                Files.writeString(driveC.resolve("LOG/S002.RC"), "RC=0\n");
                Files.writeString(driveC.resolve("LOG/S002.LOG"), "Linked.\n");
                Files.createDirectories(driveC.resolve("BIN"));
                Files.createDirectories(driveC.resolve("OBJ"));
                Files.writeString(driveC.resolve("OBJ/MAIN.OBJ"), "obj");
                Files.writeString(driveC.resolve("BIN/MAIN.EXE"), "exe");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return new ProcessResult(0, "DOSBox finished", false, false);
        };

        HybridToolRunner runner = new HybridToolRunner(processes, stagingRoot);

        Project project = sampleProject("borland-tasm", "src/MAIN.ASM", List.of());
        ToolInvocation s1 = new ToolInvocation("tasm", BuildPhase.ASSEMBLE, HostKind.DOS_REAL, List.of("S:\\src\\MAIN.ASM"), null, null, List.of("OBJ/MAIN.OBJ"));
        ToolInvocation s2 = new ToolInvocation("tlink", BuildPhase.LINK, HostKind.DOS_REAL, List.of("C:\\OBJ\\MAIN.OBJ"), null, null, List.of("BIN/MAIN.EXE"));
        BuildPlan plan = new BuildPlan(project, "release", TargetProfileCatalog.DOS_EXE_16, List.of(s1, s2), "BIN/MAIN.EXE", List.of("OBJ/MAIN.OBJ", "BIN/MAIN.EXE"));

        ResolvedToolchain tools = new ResolvedToolchain("borland-tasm", Map.of(
                "tasm", new ToolInstallation("tasm", "4.1", tasmExe, HostKind.DOS_REAL, Map.of(), null, "t"),
                "tlink", new ToolInstallation("tlink", "7.1", tlinkExe, HostKind.DOS_REAL, Map.of(), null, "t")
        ), new ToolInstallation("dosbox", "0.74-3", dbxExe, HostKind.WIN64, Map.of(), null, "t"));

        ToolRunResult result = runner.run(plan, projectRoot, tools, CancellationToken.NONE, Duration.ofSeconds(5));
        assertEquals(BuildStatus.SUCCEEDED, result.status());
        assertEquals(2, result.steps().size());

        runner.release(result);
        assertFalse(Files.exists(result.outputDirectory()), "Staging directory must be cleaned up on release");
    }

    @Test
    void executesHybridBuildHostAndDosSteps(@TempDir Path tempDir) throws IOException {
        Path projectRoot = Files.createDirectory(tempDir.resolve("proj"));
        Path stagingRoot = Files.createDirectory(tempDir.resolve("staging"));
        Path src = Files.createDirectory(projectRoot.resolve("src"));
        Files.writeString(src.resolve("MAIN.ASM"), "; test");

        Path mlExe = Files.createFile(tempDir.resolve("ML.EXE"));
        Path linkExe = Files.createFile(tempDir.resolve("LINK.EXE"));
        Path dbxExe = Files.createFile(tempDir.resolve("DOSBOX.EXE"));

        ProcessExecutor processes = (req, cancel) -> {
            if (req.command().getFirst().contains("ML.EXE")) {
                // Host step
                try {
                    Path driveC = req.workingDirectory();
                    Files.createDirectories(driveC.resolve("OBJ"));
                    Files.writeString(driveC.resolve("OBJ/MAIN.OBJ"), "ml_obj");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return new ProcessResult(0, "Assembled on host", false, false);
            } else {
                // DOSBox step
                Path driveC = req.workingDirectory().resolve("C");
                try {
                    Files.writeString(driveC.resolve("LOG/DONE.TXT"), "DONE\n");
                    Files.writeString(driveC.resolve("LOG/S002.RC"), "RC=0\n");
                    Files.writeString(driveC.resolve("LOG/S002.LOG"), "Linked in DOSBox.\n");
                    Files.createDirectories(driveC.resolve("BIN"));
                    Files.writeString(driveC.resolve("BIN/MAIN.EXE"), "link_exe");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return new ProcessResult(0, "DOSBox finished", false, false);
            }
        };

        HybridToolRunner runner = new HybridToolRunner(processes, stagingRoot);

        Project project = sampleProject("microsoft-masm", "src/MAIN.ASM", List.of());
        ToolInvocation s1 = new ToolInvocation("ml", BuildPhase.ASSEMBLE, HostKind.WIN32_CONSOLE, List.of("/c", "/FoOBJ/MAIN.OBJ", "src/MAIN.ASM"), null, null, List.of("OBJ/MAIN.OBJ"));
        ToolInvocation s2 = new ToolInvocation("link", BuildPhase.LINK, HostKind.DOS_REAL, List.of("@C:\\LINK.RSP"), "LINK.RSP", "C:\\OBJ\\MAIN.OBJ,C:\\BIN\\MAIN.EXE;", List.of("BIN/MAIN.EXE"));
        BuildPlan plan = new BuildPlan(project, "release", TargetProfileCatalog.DOS_EXE_16, List.of(s1, s2), "BIN/MAIN.EXE", List.of("OBJ/MAIN.OBJ", "BIN/MAIN.EXE"));

        ResolvedToolchain tools = new ResolvedToolchain("microsoft-masm", Map.of(
                "ml", new ToolInstallation("ml", "6.11", mlExe, HostKind.WIN32_CONSOLE, Map.of(), null, "t"),
                "link", new ToolInstallation("link", "5.31", linkExe, HostKind.DOS_REAL, Map.of(), null, "t")
        ), new ToolInstallation("dosbox", "0.74-3", dbxExe, HostKind.WIN64, Map.of(), null, "t"));

        ToolRunResult result = runner.run(plan, projectRoot, tools, CancellationToken.NONE, Duration.ofSeconds(5));
        assertEquals(BuildStatus.SUCCEEDED, result.status());
        assertEquals(2, result.steps().size());
        assertEquals(0, result.steps().get(0).exitCode());
        assertEquals(0, result.steps().get(1).exitCode());

        runner.release(result);
        assertFalse(Files.exists(result.outputDirectory()));
    }

    /** A user folder such as "Juan Perez" used to stop the IDE from starting, because the runner threw at creation. */
    @Test
    void anUnmountableStagingRootOnlyFailsTheDosBuild(@TempDir Path tempDir) throws IOException {
        Path projectRoot = Files.createDirectory(tempDir.resolve("proj"));
        Files.createDirectories(projectRoot.resolve("src"));
        Files.writeString(projectRoot.resolve("src/MAIN.ASM"), "; test");
        Path unmountable = tempDir.resolve("Juan Perez").resolve("staging");

        HybridToolRunner runner = assertDoesNotThrow(() -> new HybridToolRunner(
                (request, cancellation) -> fail("No DOSBox session may start from an unmountable folder"), unmountable));

        Project project = sampleProject("borland-tasm", "src/MAIN.ASM", List.of());
        ToolInvocation step = new ToolInvocation("tasm", BuildPhase.ASSEMBLE, HostKind.DOS_REAL, List.of("S:\\SRC\\MAIN.ASM"),
                null, null, List.of("OBJ/MAIN.OBJ"));
        BuildPlan plan = new BuildPlan(project, "release", TargetProfileCatalog.DOS_EXE_16, List.of(step), "OBJ/MAIN.OBJ",
                List.of("OBJ/MAIN.OBJ"));
        ResolvedToolchain tools = new ResolvedToolchain("borland-tasm", Map.of(), null);

        var failure = assertThrows(io.github.dinamo541.idearm.domain.DomainException.class,
                () -> runner.run(plan, projectRoot, tools, CancellationToken.NONE, Duration.ofSeconds(5)));
        assertEquals("build.non-ascii-staging", failure.code());
    }

    private static Project sampleProject(String toolchain, String entry, List<String> modules) {
        return new Project(
                1,
                new ProjectInfo("APP", "1.0.0"),
                new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection(toolchain, "*"),
                new Sources(entry, modules, List.of(), List.of()),
                new Resources(List.of()),
                Map.of("release", BuildConfiguration.release()),
                RunConfiguration.defaults(),
                new DebugConfiguration("external"),
                new DistConfiguration(true, false)
        );
    }
}
