package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.build.ProcessRequest;
import io.github.dinamo541.idearm.domain.build.ProcessResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Covers the guarantees a build depends on: reproducible environment, timeouts and cancellation. */
@EnabledOnOs(OS.WINDOWS)
class ProcessServiceTest {

    private final ProcessService processes = new ProcessService();

    @Test
    void capturesOutputAndExitCode() {
        ProcessResult result = processes.run(
                ProcessRequest.isolated(List.of("cmd", "/c", "echo hello && exit 3"), null, Duration.ofSeconds(30)),
                CancellationToken.NONE);

        assertEquals(3, result.exitCode());
        assertTrue(result.output().contains("hello"), result.output());
        assertFalse(result.timedOut());
        assertFalse(result.cancelled());
    }

    @Test
    void keepsOnlyTheVariablesAProcessNeedsToStart() {
        // USERNAME is inherited by the JVM but is not on the allowlist, so a tool must never see it.
        assumeTrue(System.getenv("USERNAME") != null, "The host does not define USERNAME.");

        ProcessResult result = processes.run(
                ProcessRequest.isolated(List.of("cmd", "/c", "set"), null, Duration.ofSeconds(30)),
                CancellationToken.NONE);

        String environment = result.output().toUpperCase(java.util.Locale.ROOT);
        assertTrue(environment.contains("SYSTEMROOT="), "SystemRoot must survive: " + result.output());
        assertFalse(environment.contains("USERNAME="), "Non-essential variables must be dropped: " + result.output());
    }

    @Test
    void doesNotLeakToolVariablesIntoABuild() {
        // INCLUDE and LIB change how ML 6.11 and LINK resolve files; only the request may set them.
        ProcessResult result = processes.run(
                new ProcessRequest(List.of("cmd", "/c", "set"), null, Map.of("INCLUDE", "S:\\INCLUDE"),
                        Duration.ofSeconds(30)),
                CancellationToken.NONE);

        assertTrue(result.output().toUpperCase(java.util.Locale.ROOT).contains("INCLUDE=S:\\INCLUDE"),
                result.output());
    }

    @Test
    void inheritsEverythingOnlyWhenAskedTo() {
        assumeTrue(System.getenv("USERNAME") != null, "The host does not define USERNAME.");

        ProcessResult result = processes.run(
                new ProcessRequest(List.of("cmd", "/c", "set"), null, Map.of(), Duration.ofSeconds(30), true),
                CancellationToken.NONE);

        assertTrue(result.output().toUpperCase(java.util.Locale.ROOT).contains("USERNAME="), result.output());
    }

    @Test
    void stopsAToolThatWaitsForever() {
        // LINK 5.31 prompts for input when the command line has no trailing semicolon: the timeout is the rescue.
        ProcessResult result = processes.run(
                ProcessRequest.isolated(List.of("cmd", "/c", "ping -n 20 127.0.0.1 > nul"), null,
                        Duration.ofMillis(400)),
                CancellationToken.NONE);

        assertTrue(result.timedOut(), "A process past its deadline must be reported as timed out.");
        assertFalse(result.cancelled());
    }

    @Test
    void reportsCancellationWithoutStartingTheProcess() {
        ProcessResult result = processes.run(
                ProcessRequest.isolated(List.of("cmd", "/c", "echo unreachable"), null, Duration.ofSeconds(30)),
                () -> true);

        assertTrue(result.cancelled());
        assertEquals("", result.output());
    }
}
