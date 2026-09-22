package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.debug.DebugEvent;
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

import static org.junit.jupiter.api.Assertions.*;

class GdbProcessDebugSessionTest {

    @Test
    void streamsOutputEvents() {
        var out = new ByteArrayOutputStream();
        var in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
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
        var in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
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
    void handlesExitedNormally() {
        var out = new ByteArrayOutputStream();
        var in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        List<DebugEvent> events = new ArrayList<>();

        try (var session = new GdbProcessDebugSession(out, in, null, null, events::add, System.nanoTime())) {
            GdbMiRecord record = GdbMiParser.parse("*stopped,reason=\"exited-normally\"");
            session.handleRecord(record);

            assertEquals(SessionState.EXITED, session.state());
            assertTrue(session.exit().isDone());
            assertEquals(0, session.exit().join().exitCode());
        }
    }

    @Test
    void sendsNumberedCommands() {
        var out = new ByteArrayOutputStream();
        var in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));

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
        var in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
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
        var in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
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
        var in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));

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
        var in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));

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
        var in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));

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
}
