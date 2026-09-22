package io.github.dinamo541.idearm.toolchain.dos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.ProcessRequest;
import io.github.dinamo541.idearm.domain.execution.ExecutionSession;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.LaunchSpec;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.domain.port.ProcessLauncher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The generated run session, checked without an emulator.
 *
 * <p>DOSBox copies the autoexec section into a 4 KiB AUTOEXEC.BAT and aborts with "Autoexec.bat file overflow"
 * when it does not fit, which is what the 255-step exit-code ladder does; it therefore lives in a batch file
 * that autoexec calls.
 */
class DosBoxRunSessionTest {

    private static final int AUTOEXEC_LIMIT = 4096;

    @TempDir
    Path tempDir;

    @Test
    void autoexecOnlyMountsAndCallsTheBatch() throws IOException {
        var launcher = new CapturingLauncher();
        launch(launcher, false);

        String autoexec = section(Files.readString(launcher.workingDirectory.resolve("run.conf")), "[autoexec]");
        assertTrue(autoexec.length() < AUTOEXEC_LIMIT,
                "DOSBox aborts when its autoexec does not fit in 4 KiB: " + autoexec.length() + " characters");
        assertTrue(autoexec.contains("call run.bat"),
                "A batch started without CALL never returns, so the emulator would stay open: " + autoexec);
        assertTrue(autoexec.strip().endsWith("exit"), autoexec);
        int mount = autoexec.indexOf("mount C");
        int secure = autoexec.indexOf("config -securemode");
        assertTrue(mount >= 0 && secure > mount && secure < autoexec.indexOf("call run.bat"),
                "The program must not be able to mount a host folder after the IDE's own mount: " + autoexec);
    }

    @Test
    void theBatchRecordsTheExactExitCodeAndClosesTheEmulator() throws IOException {
        var launcher = new CapturingLauncher();
        launch(launcher, false);

        String batch = Files.readString(launcher.workingDirectory.resolve("C").resolve("run.bat"));
        assertTrue(batch.contains("if errorlevel 1 set RC=1"), batch);
        assertTrue(batch.contains("if errorlevel 255 set RC=255"),
                "DOS errorlevel is a >= test, so every step up to 255 is needed");
        assertTrue(batch.contains("echo RC=%RC%>C:\\EXITCODE.TXT"), batch);
        assertFalse(batch.contains("pause"), "This run did not ask to keep the window open");
        assertTrue(batch.strip().endsWith("exit"), batch);
    }

    @Test
    void keepOpenWaitsForAKeyBeforeClosing() throws IOException {
        var launcher = new CapturingLauncher();
        launch(launcher, true);

        String batch = Files.readString(launcher.workingDirectory.resolve("C").resolve("run.bat"));
        assertTrue(batch.contains("pause > nul"), batch);
    }

    @Test
    void theProgramStatusComesFromTheSentinelNotFromTheEmulator() throws Exception {
        var launcher = new CapturingLauncher();
        ExecutionSession session = launch(launcher, false);

        Files.writeString(launcher.workingDirectory.resolve("C").resolve("EXITCODE.TXT"), "RC=7\r\n",
                StandardCharsets.US_ASCII);
        launcher.process.exit.complete(0);

        ExitInfo exit = session.exit().get(5, TimeUnit.SECONDS);
        assertEquals(7, exit.exitCode());
        assertFalse(exit.wasStopped());

        session.close();
        assertFalse(Files.exists(launcher.workingDirectory), "Closing a session removes its staging copy");
    }

    @Test
    void stagingInsideTheProjectIsRejected() {
        var provider = new DosBoxExecutionEnvironmentProvider(new CapturingLauncher());
        Path projectRoot = tempDir.resolve("project");
        LaunchSpec spec = new LaunchSpec(tempDir.resolve("MAIN.EXE"), projectRoot, List.of(), List.of(), false,
                "auto", 16, projectRoot.resolve("staging"), dosbox());

        // A program that can reach the project could delete the sources it was built from.
        assertThrows(DomainException.class, () -> provider.launch(spec));
    }

    private ExecutionSession launch(CapturingLauncher launcher, boolean keepOpen) throws IOException {
        Path projectRoot = Files.createDirectories(tempDir.resolve("project"));
        Path executable = Files.writeString(projectRoot.resolve("MAIN.EXE"), "MZ");
        Path stagingRoot = Files.createDirectories(tempDir.resolve("staging"));
        return new DosBoxExecutionEnvironmentProvider(launcher).launch(
                new LaunchSpec(executable, projectRoot, List.of(), List.of(), keepOpen, "auto", 16, stagingRoot,
                        dosbox()));
    }

    private static ToolInstallation dosbox() {
        return new ToolInstallation("dosbox-x", "2026.08.31", Path.of("dosbox-x.exe"), HostKind.WIN64, Map.of());
    }

    /** The lines of one configuration section, up to the next one. */
    private static String section(String configuration, String header) {
        int start = configuration.indexOf(header);
        assertTrue(start >= 0, "Missing section " + header);
        int next = configuration.indexOf("\n[", start + header.length());
        return next < 0 ? configuration.substring(start) : configuration.substring(start, next);
    }

    private static final class CapturingLauncher implements ProcessLauncher {
        private Path workingDirectory;
        private final FakeProcess process = new FakeProcess();

        @Override
        public LaunchedProcess start(ProcessRequest request) {
            this.workingDirectory = request.workingDirectory();
            return process;
        }
    }

    private static final class FakeProcess implements ProcessLauncher.LaunchedProcess {
        private final CompletableFuture<Integer> exit = new CompletableFuture<>();

        @Override
        public long pid() {
            return 1234;
        }

        @Override
        public boolean alive() {
            return !exit.isDone();
        }

        @Override
        public CompletableFuture<Integer> onExit() {
            return exit;
        }

        @Override
        public void stop() {
            exit.complete(1);
        }

        @Override
        public void close() {
            exit.complete(1);
        }
    }
}
