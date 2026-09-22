package io.github.dinamo541.idearm.integration;

import io.github.dinamo541.idearm.domain.execution.DosBoxDialects;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.LaunchSpec;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.infrastructure.tools.DefaultToolRegistry;
import io.github.dinamo541.idearm.infrastructure.workspace.StagingLocation;
import io.github.dinamo541.idearm.toolchain.dos.execution.DosBoxExecutionEnvironmentProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A DOS program runs in the DOSBox the IDE picks by itself (0.74-3 first) and its exit code comes back, on any
 * operating system that has DOSBox installed. No assembler is needed: the program is five bytes.
 */
@Tag("requires-dosbox")
class DosBoxRunIntegrationTest {

    /** {@code MOV AX,4C07h / INT 21h}: ends with exit code 7. */
    private static final byte[] EXIT_7 = {(byte) 0xB8, 0x07, 0x4C, (byte) 0xCD, 0x21};

    @TempDir
    Path project;

    @Test
    void aDosProgramEndsWithItsExitCodeInTheAutomaticDosBox() throws Exception {
        ToolInstallation dosBox = DefaultToolRegistry.system().find(DosBoxDialects.AUTO)
                .orElseThrow(() -> new AssertionError("No DOSBox is installed or registered."));
        Path program = Files.write(project.resolve("exit7.com"), EXIT_7);
        var spec = new LaunchSpec(program, project, List.of(), List.of(), false, "auto", 16,
                StagingLocation.resolve(), dosBox);

        try (var session = new DosBoxExecutionEnvironmentProvider().launch(spec)) {
            ExitInfo exit = session.exit().get(60, TimeUnit.SECONDS);
            assertEquals(7, exit.exitCode(), exit.message() + " (" + dosBox.toolId() + " " + dosBox.executable() + ")");
        }
    }
}
