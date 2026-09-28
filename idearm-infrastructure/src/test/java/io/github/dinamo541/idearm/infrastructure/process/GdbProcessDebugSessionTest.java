package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.debug.DebugEvent;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GdbProcessDebugSessionTest {

    /**
     * GDB output that stays open, as a running GDB's does. An empty stream ends at once, which the session
     * rightly treats as GDB having died.
     */
    private static java.io.InputStream liveGdb() {
        try {
            return new java.io.PipedInputStream(new java.io.PipedOutputStream());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void streamsOutputEvents() {
        var out = new ByteArrayOutputStream();
        var in = liveGdb();
        List<DebugEvent> events = new ArrayList<>();

        try (var session = new GdbProcessDebugSession(out, in, null, null, events::add, System.nanoTime())) {
            GdbMiRecord record = GdbMiParser.parse("~\"Hello from debugger\\n\"");
            session.handleRecord(record);
            session.awaitEvents();

            assertEquals(1, events.size());
            assertTrue(events.get(0) instanceof DebugEvent.Output);
            assertEquals("Hello from debugger\n", ((DebugEvent.Output) events.get(0)).text());
        }
    }

    /** Outside Windows the program writes into GDB's own output; those lines are not MI and were dropped. */
    @Test
    void linesThatAreNotMiAreTheProgramsOutput() {
        var out = new ByteArrayOutputStream();
        var in = liveGdb();
        List<DebugEvent> events = new ArrayList<>();

        try (var session = new GdbProcessDebugSession(out, in, null, null, events::add, System.nanoTime())) {
            session.handleRecord(GdbMiParser.parse("Hello from 64-bit Linux Assembly!"));
            session.awaitEvents();

            assertEquals(List.of(new DebugEvent.Output("Hello from 64-bit Linux Assembly!\n")), events);
        }
    }

    @Test
    void locationsAndArgumentsAreQuotedMiStrings() {
        assertEquals("\"src/my file.asm:12\"", GdbProcessDebugSession.miString("src/my file.asm:12"));
        assertEquals("\"C:\\\\a \\\"b\\\"\"", GdbProcessDebugSession.miString("C:\\a \"b\""));
    }

    @Test
    void handlesExitedNormally() throws Exception {
        var out = new ByteArrayOutputStream();
        // The record arrives through GDB's output, as it does in a real session. Handing it to the session by hand
        // raced with the reader thread: the end of an empty stream also ends the session, and on the CI machine the
        // reader got there first, so the session ended as "stopped" (-1) instead of with exit code 0.
        var in = new ByteArrayInputStream("*stopped,reason=\"exited-normally\"\n".getBytes(StandardCharsets.UTF_8));
        List<DebugEvent> events = new ArrayList<>();

        try (var session = new GdbProcessDebugSession(out, in, null, null, events::add, System.nanoTime())) {
            ExitInfo exit = session.exit().get(5, TimeUnit.SECONDS);

            assertEquals(0, exit.exitCode());
            assertEquals(SessionState.EXITED, session.state());
        }
    }

    @Test
    void sendsNumberedCommands() {
        var out = new ByteArrayOutputStream();
        var in = liveGdb();

        try (var session = new GdbProcessDebugSession(out, in, null, null, e -> {}, System.nanoTime())) {
            CompletableFuture<GdbMiRecord> f1 = session.sendCommand("-gdb-set pagination off");
            CompletableFuture<GdbMiRecord> f2 = session.sendCommand("-exec-continue");

            String sent = out.toString(StandardCharsets.UTF_8);
            assertTrue(sent.contains("1-gdb-set pagination off\n"));
            assertTrue(sent.contains("2-exec-continue\n"));

            assertFalse(f1.isDone());
            assertFalse(f2.isDone());

            session.handleRecord(GdbMiParser.parse("1^done"));
            assertTrue(f1.isDone());
            assertFalse(f2.isDone());

            session.handleRecord(GdbMiParser.parse("2^running"));
            assertTrue(f2.isDone());
        }
    }

    @Test
    void transitionsToPausedOnBreakpointHit() {
        var out = new ByteArrayOutputStream();
        var in = liveGdb();
        List<DebugEvent> events = new ArrayList<>();

        try (var session = new GdbProcessDebugSession(out, in, null, null, events::add, System.nanoTime())) {
            GdbMiRecord stopRecord = GdbMiParser.parse("*stopped,reason=\"breakpoint-hit\",frame={file=\"main.asm\",line=\"14\"}");
            session.handleRecord(stopRecord);

            assertTrue(session.isPaused());
            assertEquals(1L, session.instructionsExecuted());

            var parsed = GdbMiParser.parse("1^done,register-values=[]");
            session.handleRecord(parsed);
            session.awaitEvents();

            boolean foundPaused = events.stream().anyMatch(e -> e instanceof DebugEvent.Paused);
            assertTrue(foundPaused);
        }
    }

    @Test
    void prioritizesFullnameOverFileWhenAvailable() {
        var out = new ByteArrayOutputStream();
        var in = liveGdb();
        List<DebugEvent> events = new ArrayList<>();

        try (var session = new GdbProcessDebugSession(out, in, null, null, events::add, System.nanoTime())) {
            GdbMiRecord stopRecord = GdbMiParser.parse("*stopped,reason=\"breakpoint-hit\",frame={file=\"main.asm\",fullname=\"C:/repo/src/main.asm\",line=\"42\"}");
            session.handleRecord(stopRecord);

            var regRecord = GdbMiParser.parse("1^done,register-values=[]");
            session.handleRecord(regRecord);
            session.awaitEvents();

            var pausedEvent = events.stream()
                    .filter(e -> e instanceof DebugEvent.Paused)
                    .map(e -> (DebugEvent.Paused) e)
                    .findFirst()
                    .orElseThrow();

            assertEquals("C:/repo/src/main.asm", pausedEvent.file());
            assertEquals(42, pausedEvent.line());
        }
    }

    @Test
    void parsesCallStackFrames() {
        var out = new ByteArrayOutputStream();
        var in = liveGdb();

        try (var session = new GdbProcessDebugSession(out, in, null, null, e -> {}, System.nanoTime())) {
            CompletableFuture<List<io.github.dinamo541.idearm.domain.debug.CallFrame>> future =
                    CompletableFuture.supplyAsync(() -> session.callStack(5));

            // Wait until command is sent (token 1)
            while (!out.toString(StandardCharsets.UTF_8).contains("1-stack-list-frames")) {
                Thread.onSpinWait();
            }

            session.handleRecord(GdbMiParser.parse("1^done,stack=[frame={level=\"0\",addr=\"0x00401000\",func=\"main\",file=\"src/main.asm\",fullname=\"C:/repo/src/main.asm\",line=\"15\"},frame={level=\"1\",addr=\"0x7FF61234\",func=\"__start\",file=\"crt0.c\",line=\"45\"}]"));

            List<io.github.dinamo541.idearm.domain.debug.CallFrame> frames = future.join();
            assertEquals(2, frames.size());

            var f0 = frames.get(0);
            assertEquals(0, f0.level());
            assertEquals("0x00401000", f0.address());
            assertEquals("main", f0.function());
            assertEquals("C:/repo/src/main.asm", f0.file());
            assertEquals(15, f0.line());
            assertEquals("#0 0x00401000 in main at C:/repo/src/main.asm:15", f0.toDisplayString());

            var f1 = frames.get(1);
            assertEquals(1, f1.level());
            assertEquals("0x7FF61234", f1.address());
            assertEquals("__start", f1.function());
            assertEquals("crt0.c", f1.file());
            assertEquals(45, f1.line());
        }
    }

    @Test
    void evaluatesExpression() {
        var out = new ByteArrayOutputStream();
        var in = liveGdb();

        try (var session = new GdbProcessDebugSession(out, in, null, null, e -> {}, System.nanoTime())) {
            CompletableFuture<java.util.Optional<String>> future =
                    CompletableFuture.supplyAsync(() -> session.evaluateExpression("$rax + 4"));

            while (!out.toString(StandardCharsets.UTF_8).contains("1-data-evaluate-expression")) {
                Thread.onSpinWait();
            }

            session.handleRecord(GdbMiParser.parse("1^done,value=\"42\""));

            var res = future.join();
            assertTrue(res.isPresent());
            assertEquals("42 (0x2A)", res.get());
        }
    }

    @Test
    void translatesAssemblyWatchesIntoGdbExpressions() {
        Set<String> registers = Set.of("RAX", "RSP", "RIP", "EAX", "ESP");

        assertEquals("$rax", GdbProcessDebugSession.toGdbExpression("RAX", registers, true));
        assertEquals("$rax + 8", GdbProcessDebugSession.toGdbExpression("rax + 8", registers, true));
        assertEquals("*(unsigned long long *)($rsp+8)",
                GdbProcessDebugSession.toGdbExpression("[RSP+8]", registers, true));
        assertEquals("*(unsigned int *)($esp)", GdbProcessDebugSession.toGdbExpression("[esp]", registers, false));
        assertEquals("*(unsigned char *)(((unsigned long long)&message) + 0x10)",
                GdbProcessDebugSession.toGdbExpression("byte [message + 10h]", registers, true));
        assertEquals("((unsigned long long)&message)",
                GdbProcessDebugSession.toGdbExpression("message", registers, true));
        // Expressions already written for GDB are left alone.
        assertEquals("$rsp", GdbProcessDebugSession.toGdbExpression("$rsp", registers, true));
        assertEquals("(char)$rax", GdbProcessDebugSession.toGdbExpression("(char)$rax", registers, true));
        assertEquals("*(unsigned long long *)($rsp)",
                GdbProcessDebugSession.toGdbExpression("[$rsp]", registers, true));
    }

    @Test
    void readsMemoryAtAFlatAddress() {
        var out = new ByteArrayOutputStream();
        var in = liveGdb();

        try (var session = new GdbProcessDebugSession(out, in, null, null, e -> {}, System.nanoTime())) {
            var future = CompletableFuture.supplyAsync(() -> session.readMemory(0x7FF612345000L, 4));
            while (!out.toString(StandardCharsets.UTF_8).contains("1-data-read-memory-bytes 0x7ff612345000 4")) {
                Thread.onSpinWait();
            }
            session.handleRecord(GdbMiParser.parse(
                    "1^done,memory=[{begin=\"0x7ff612345000\",offset=\"0x0\",end=\"0x7ff612345004\",contents=\"48656c6c\"}]"));

            var view = future.join();
            assertTrue(view.isFlat());
            assertEquals(0x7FF612345000L, view.address());
            assertTrue(view.toHexDump().startsWith("00007FF612345000  48 65 6C 6C"), view.toHexDump());
        }
    }

    /** GDB crashing left every waiting command hanging and ended the session as if the user had stopped it. */
    @Test
    void gdbEndingUnexpectedlyFailsWaitingCommandsAndEndsTheSession() throws Exception {
        var gdbOutput = new java.io.PipedOutputStream();
        var in = new java.io.PipedInputStream(gdbOutput);
        try (var session = new GdbProcessDebugSession(new ByteArrayOutputStream(), in, null, null, e -> { },
                System.nanoTime())) {
            CompletableFuture<GdbMiRecord> waiting = session.sendCommand("-data-list-register-names");
            gdbOutput.close();

            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> waiting.get(5, TimeUnit.SECONDS));
            assertInstanceOf(java.io.IOException.class, failure.getCause());
            ExitInfo exit = session.exit().get(5, TimeUnit.SECONDS);
            assertFalse(exit.wasStopped());
            assertEquals(SessionState.EXITED, session.state());
        }
    }

    @Test
    void stopEndsTheSessionAsStopped() throws Exception {
        var gdbOutput = new java.io.PipedOutputStream();
        var in = new java.io.PipedInputStream(gdbOutput);
        var session = new GdbProcessDebugSession(new ByteArrayOutputStream(), in, null, null, e -> { },
                System.nanoTime());
        session.stop();
        gdbOutput.close();

        assertTrue(session.exit().get(5, TimeUnit.SECONDS).wasStopped());
        assertEquals(SessionState.STOPPED, session.state());
    }

    /**
     * A breakpoint toggled while the program ran was dropped, and one toggled while paused blocked the JavaFX
     * thread for up to two seconds per breakpoint while GDB answered.
     */
    @Test
    void breakpointsChangedWhileRunningAreSentAtTheNextStopWithoutBlocking() throws Exception {
        var gdbOutput = new java.io.PipedOutputStream();
        var in = new java.io.PipedInputStream(gdbOutput);
        var commands = new ByteArrayOutputStream();
        try (var session = new GdbProcessDebugSession(commands, in, null, null, e -> { }, System.nanoTime())) {
            long start = System.nanoTime();
            session.setBreakpoints(List.of(new io.github.dinamo541.idearm.domain.debug.Breakpoint("src/main.asm", 12, true)));
            assertTrue(System.nanoTime() - start < 100_000_000L, "setBreakpoints must not wait for GDB");
            assertFalse(commands.toString(StandardCharsets.UTF_8).contains("-break-insert"),
                    "GDB refuses breakpoints while the program runs");

            gdbOutput.write("*stopped,reason=\"signal-received\"\n".getBytes(StandardCharsets.UTF_8));
            gdbOutput.flush();
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (!commands.toString(StandardCharsets.UTF_8).contains("-break-insert")
                    && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(commands.toString(StandardCharsets.UTF_8).contains("-break-insert \"src/main.asm:12\""),
                    commands.toString(StandardCharsets.UTF_8));
        }
    }
}
