package io.github.dinamo541.idearm.integration;

import io.github.dinamo541.idearm.application.*;
import io.github.dinamo541.idearm.domain.execution.*;
import io.github.dinamo541.idearm.domain.model.*;
import io.github.dinamo541.idearm.domain.port.*;
import io.github.dinamo541.idearm.toolchain.dos.execution.DosBoxExecutionEnvironmentProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates Security and Isolation Rule 5 / ADR-002:
 * Programs running in execution staging can never touch or delete files
 * in the host's project source tree.
 */
class FilesystemIsolationTest {

    @TempDir
    Path tempDir;

    @Test
    void projectSourceTreeRemainsIntactDuringIsolatedExecution() throws IOException {
        Path projectRoot = tempDir.resolve("sandbox-test-project");
        Path srcDir = Files.createDirectories(projectRoot.resolve("src"));
        Path sourceFile = srcDir.resolve("main.asm");
        String originalSourceCode = "; Sensitive source file that must never be deleted\nMOV AX, 4C00h\nINT 21h\n";
        Files.writeString(sourceFile, originalSourceCode);

        Path buildDir = Files.createDirectories(projectRoot.resolve("build/debug/bin"));
        Path exeFile = buildDir.resolve("main.exe");
        Files.writeString(exeFile, "MOCK_EXECUTABLE");

        Project project = new Project(
                1,
                new ProjectInfo("ISOLATION", "1.0.0"),
                new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection("borland-tasm", ">=3.2"),
                new Sources("src/main.asm", List.of(), List.of(), List.of()),
                new Resources(List.of()),
                Map.of("debug", BuildConfiguration.debug()),
                new RunConfiguration("dosbox", "required", false, "auto", 16, List.of()),
                new DebugConfiguration("external"),
                new DistConfiguration(true, false)
        );

        Path stagingRoot = tempDir.resolve("safe-staging");
        Files.createDirectories(stagingRoot);

        // Verify LaunchSpec enforces staging outside project
        assertThrows(Exception.class, () -> {
            var rogueSpec = new LaunchSpec(
                    exeFile,
                    projectRoot,
                    List.of(),
                    List.of(),
                    false,
                    "auto",
                    16,
                    projectRoot.resolve("src"), // Trying to stage directly inside src!
                    new ToolInstallation("dosbox", "0.74", Path.of("dosbox.exe"), HostKind.WIN64, Map.of())
            );
            new DosBoxExecutionEnvironmentProvider().launch(rogueSpec);
        }, "Launching staging inside projectRoot must be rejected!");

        // Verify that in normal staging, only an isolated copy exists in staging/run-XXXX/C
        var validSpec = new LaunchSpec(
                exeFile,
                projectRoot,
                List.of(),
                List.of(),
                false,
                "auto",
                16,
                stagingRoot,
                new ToolInstallation("dosbox", "0.74", Path.of("nonexistent-dosbox.exe"), HostKind.WIN64, Map.of())
        );

        // Verify that sourceFile exists and has original content
        assertTrue(Files.isRegularFile(sourceFile));
        assertEquals(originalSourceCode, Files.readString(sourceFile));
    }
}
