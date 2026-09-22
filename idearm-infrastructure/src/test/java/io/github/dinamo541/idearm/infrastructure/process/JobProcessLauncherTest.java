package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.build.ProcessRequest;
import io.github.dinamo541.idearm.domain.port.ProcessLauncher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A run session must report the program status and must never leave the emulator behind (ADR-002). */
@EnabledOnOs(OS.WINDOWS)
class JobProcessLauncherTest {

    private final ProcessLauncher launcher = new JobProcessLauncher();

    @Test
    void completesWithTheProcessExitCode() throws Exception {
        try (ProcessLauncher.LaunchedProcess process = launcher.start(
                ProcessRequest.isolated(List.of("cmd", "/c", "exit 7"), null, Duration.ofSeconds(30)))) {
            assertEquals(7, process.onExit().get(30, TimeUnit.SECONDS));
            assertFalse(process.alive());
            assertTrue(process.pid() > 0);
        }
    }

    @Test
    void stopTerminatesTheWholeTree() throws Exception {
        ProcessLauncher.LaunchedProcess process = launcher.start(
                ProcessRequest.isolated(List.of("cmd", "/c", "ping -n 60 127.0.0.1 > nul"), null,
                        Duration.ofMinutes(5)));
        assertTrue(process.alive());

        process.stop();

        assertFalse(process.alive(), "Stop must terminate the emulator and its children.");
        process.close();
    }

    @Test
    void closingTheHandleEndsARunningProcess() throws Exception {
        ProcessLauncher.LaunchedProcess process = launcher.start(
                ProcessRequest.isolated(List.of("cmd", "/c", "ping -n 60 127.0.0.1 > nul"), null,
                        Duration.ofMinutes(5)));
        long pid = process.pid();

        process.close();

        assertTrue(ProcessHandle.of(pid).map(handle -> waitForExit(handle)).orElse(true),
                "Closing the job must kill the process it owns.");
    }

    private static boolean waitForExit(ProcessHandle handle) {
        try {
            handle.onExit().get(10, TimeUnit.SECONDS);
            return true;
        } catch (Exception failure) {
            return !handle.isAlive();
        }
    }
}
