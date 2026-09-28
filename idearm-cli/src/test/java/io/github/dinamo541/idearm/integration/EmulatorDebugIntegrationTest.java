package io.github.dinamo541.idearm.integration;

import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.DebugCapability;
import io.github.dinamo541.idearm.domain.debug.DebugEvent;
import io.github.dinamo541.idearm.domain.debug.DebugLaunchSpec;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider;
import io.github.dinamo541.idearm.domain.port.DebugSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The default debugger of a DOS project, end to end and with no external tool: a real program, a real TASM-style
 * listing, and the whole path a student walks — stop on the first line, step, read the registers, see the
 * machine code, and stop on a breakpoint.
 *
 * <p>This is the path the IDE takes when F5 is pressed on a new DOS project, so it is the one that must work.
 */
class EmulatorDebugIntegrationTest {

    /**
     * A .COM program, loaded at 0100h:
     * <pre>
     * 0100  B8 34 12   MOV AX, 1234h
     * 0103  BB 78 56   MOV BX, 5678h
     * 0106  B8 00 4C   MOV AX, 4C00h
     * 0109  CD 21      INT 21h
     * </pre>
     */
    private static final byte[] PROGRAM = {
            (byte) 0xB8, 0x34, 0x12,
            (byte) 0xBB, 0x78, 0x56,
            (byte) 0xB8, 0x00, 0x4C,
            (byte) 0xCD, 0x21
    };

    /**
     * A listing in TASM's layout, which is how the debugger learns which line each instruction belongs to. A .COM
     * program is loaded at 0100h, and TASM numbers a tiny-model listing from there, as this one does.
     */
    private static final String LISTING = """
            Turbo Assembler\t Version 4.1\t    09/14/26 22:51:55\t    Page 1
            main.asm

                  1\t\t\t\t     .MODEL tiny
                  2\t0000\t\t\t     .CODE
                  3\t0100\t\t\t     ORG 100h
                  4\t0100  B8 3412\t\t\t mov ax, 1234h
                  5\t0103  BB 7856\t\t\t mov bx, 5678h
                  6\t0106  B8 4C00\t\t\t mov ax, 4C00h
                  7\t0109  CD 21\t\t\t int 21h
                  8\t\t\t\t     END
            """;

    private static final String SOURCE = """
            .MODEL tiny
            .CODE
            ORG 100h
            mov ax, 1234h
            mov bx, 5678h
            mov ax, 4C00h
            int 21h
            END
            """;

    /** The source lines the four instructions sit on. */
    private static final int FIRST_LINE = 4;

    @TempDir
    Path project;

    @Test
    void aStudentCanStepThroughADosProgramAndReadItsRegisters() throws Exception {
        Path executable = project.resolve("main.com");
        Files.write(executable, PROGRAM);
        Files.writeString(project.resolve("main.asm"), SOURCE);
        Files.writeString(project.resolve("main.lst"), LISTING);

        var events = new CopyOnWriteArrayList<DebugEvent>();
        var spec = launchSpec(executable, List.of(new Breakpoint("main.asm", FIRST_LINE + 2, true)));

        try (DebugSession session = emulator().launchDebug(spec, events::add)) {
            // Everything the panels need is declared, so the workbench offers it.
            assertTrue(session.capabilities().containsAll(List.of(DebugCapability.STEP, DebugCapability.REGISTERS,
                    DebugCapability.MEMORY, DebugCapability.DISASSEMBLY, DebugCapability.PAUSE)));

            // It stops on the program's first line before running anything.
            var entry = lastPause(events);
            assertEquals("main.asm", entry.file());
            assertEquals(FIRST_LINE, entry.line());
            assertEquals(0x0000, entry.registers().ax());

            // The machine code of that line, which is what the listing said it would be.
            var instructions = session.disassemble(2);
            assertEquals("MOV AX, 1234h", instructions.getFirst().text());
            assertEquals("B8 34 12", instructions.getFirst().bytes());
            assertTrue(instructions.getFirst().current());
            assertEquals("MOV BX, 5678h", instructions.get(1).text());

            // One step: the next line, with the register the first line wrote.
            session.stepInto();
            var afterStep = awaitPause(events, 2);
            assertEquals(FIRST_LINE + 1, afterStep.line());
            assertEquals(0x1234, afterStep.registers().ax());

            // Continue: it stops on the breakpoint, not at the end.
            session.resume();
            var atBreakpoint = awaitPause(events, 3);
            assertEquals(FIRST_LINE + 2, atBreakpoint.line());
            assertEquals(0x5678, atBreakpoint.registers().bx());

            // And from there it runs to the DOS exit call.
            session.resume();
            var exit = session.exit().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(0, exit.exitCode(), exit.message());
        }
    }

    private DebugLaunchSpec launchSpec(Path executable, List<Breakpoint> breakpoints) {
        var emulatorTool = new ToolInstallation("emu8086", "1.0", Path.of("emu8086"), HostKind.WIN32_CONSOLE,
                Map.of());
        return new DebugLaunchSpec(executable, project, List.of(), List.of(), emulatorTool, emulatorTool,
                breakpoints, project.resolve("staging"), "emu8086",
                Map.of("main.asm", project.resolve("main.lst")));
    }

    private static DebugEnvironmentProvider emulator() {
        for (var provider : ServiceLoader.load(DebugEnvironmentProvider.class)) {
            if (provider.id().equalsIgnoreCase("emu8086")) {
                return provider;
            }
        }
        throw new AssertionError("The built-in emulator is not registered as a debug environment provider.");
    }

    private static DebugEvent.Paused lastPause(List<DebugEvent> events) {
        var pauses = new ArrayList<DebugEvent.Paused>();
        for (DebugEvent event : events) {
            if (event instanceof DebugEvent.Paused paused) {
                pauses.add(paused);
            }
        }
        assertFalse(pauses.isEmpty(), "The session never reported where it stopped: " + events);
        return pauses.getLast();
    }

    /** Waits for the {@code expected}-th pause, because the emulator runs the program on its own thread. */
    private static DebugEvent.Paused awaitPause(List<DebugEvent> events, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            long pauses = events.stream().filter(DebugEvent.Paused.class::isInstance).count();
            if (pauses >= expected) {
                return lastPause(events);
            }
            Thread.sleep(5);
        }
        throw new AssertionError("Only " + events.stream().filter(DebugEvent.Paused.class::isInstance).count()
                + " pauses arrived, expected " + expected + ": " + events);
    }
}
