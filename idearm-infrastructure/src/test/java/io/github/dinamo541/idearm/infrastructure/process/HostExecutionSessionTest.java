package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.build.ProcessRequest;
import io.github.dinamo541.idearm.domain.port.ProcessLauncher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A native program without a console window of its own (Linux) talks to the user through the IDE. */
class HostExecutionSessionTest {

    @Test
    void everythingTheProgramPrintedArrivesInOrderBeforeItsEnd() throws Exception {
        var fake = new FakeProcess();
        var session = new HostExecutionSession(fake, null, System.nanoTime());
        fake.print("What is your name? ");
        // Text written before anyone listened is not lost.
        Thread.sleep(100);

        var shown = new StringBuffer();
        session.onOutput(shown::append);
        fake.print("Hello, Ana!\n");
        fake.end(0);

        assertEquals(0, session.exit().get(10, TimeUnit.SECONDS).exitCode());
        assertEquals("What is your name? Hello, Ana!\n", shown.toString());
        assertTrue(session.interactive());
    }

    @Test
    void typedTextReachesTheProgramAsUtf8() throws Exception {
        var fake = new FakeProcess();
        var session = new HostExecutionSession(fake, null, System.nanoTime());

        session.sendInput("José\n");

        assertEquals("José\n", fake.typed.toString(StandardCharsets.UTF_8));
        fake.end(0);
        session.exit().get(10, TimeUnit.SECONDS);
    }

    @Test
    void aProgramWithItsOwnConsoleIsNotInteractive() {
        var fake = new FakeProcess() {
            @Override
            public Optional<InputStream> output() {
                return Optional.empty();
            }
        };
        var session = new HostExecutionSession(fake, null, System.nanoTime());

        assertFalse(session.interactive());
        fake.end(3);
        assertEquals(3, session.exit().join().exitCode());
    }

    @Test
    void aRealProgramReadsALineAndAnswers() throws Exception {
        List<String> command = OS.current() == OS.WINDOWS
                ? List.of("cmd", "/c", "set /p NAME=& call echo Hello, %NAME%!")
                : List.of("sh", "-c", "read NAME; echo \"Hello, $NAME!\"");
        ProcessLauncher.LaunchedProcess process = new JobProcessLauncher().start(
                ProcessRequest.isolated(command, null, Duration.ofSeconds(30)).asInteractive());
        var session = new HostExecutionSession(process, null, System.nanoTime());
        var shown = new StringBuffer();
        session.onOutput(shown::append);

        session.sendInput("Ana\n");

        assertEquals(0, session.exit().get(30, TimeUnit.SECONDS).exitCode());
        assertEquals("Hello, Ana!", shown.toString().strip());
        session.close();
    }

    private static class FakeProcess implements ProcessLauncher.LaunchedProcess {
        private final PipedOutputStream program = new PipedOutputStream();
        private final PipedInputStream screen;
        private final CompletableFuture<Integer> exit = new CompletableFuture<>();
        final ByteArrayOutputStream typed = new ByteArrayOutputStream();

        FakeProcess() {
            try {
                screen = new PipedInputStream(program);
            } catch (java.io.IOException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        void print(String text) throws java.io.IOException {
            program.write(text.getBytes(StandardCharsets.UTF_8));
            program.flush();
        }

        void end(int code) {
            try {
                program.close();
            } catch (java.io.IOException ignored) {
                // Already closed.
            }
            exit.complete(code);
        }

        @Override public long pid() { return 1; }
        @Override public boolean alive() { return !exit.isDone(); }
        @Override public CompletableFuture<Integer> onExit() { return exit; }
        @Override public void stop() { end(-1); }
        @Override public Optional<InputStream> output() { return Optional.of(screen); }
        @Override public Optional<OutputStream> input() { return Optional.of(typed); }
        @Override public void close() { }
    }
}
