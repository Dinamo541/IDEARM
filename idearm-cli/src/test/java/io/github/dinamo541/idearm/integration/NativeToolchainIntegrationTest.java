package io.github.dinamo541.idearm.integration;

import io.github.dinamo541.idearm.application.CreateProject;
import io.github.dinamo541.idearm.cli.Main;
import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.DebugEvent;
import io.github.dinamo541.idearm.domain.debug.DebugLaunchSpec;
import io.github.dinamo541.idearm.domain.execution.ExitInfo;
import io.github.dinamo541.idearm.domain.execution.SessionState;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.RunConfiguration;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.infrastructure.persistence.TomlProjectRepository;
import io.github.dinamo541.idearm.infrastructure.process.GdbDebugEnvironmentProvider;
import io.github.dinamo541.idearm.infrastructure.tools.DefaultToolRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The native workflow end to end with the real tools (NASM, GNU ld and GDB; MSYS2 on Windows): a new project
 * builds, runs with the right exit code (in its console window on Windows, through the IDE's console on Linux),
 * and stops on a source line under GDB.
 */
@Tag("requires-nasm")
class NativeToolchainIntegrationTest {

    @TempDir
    Path parent;

    @TempDir
    Path staging;

    @ParameterizedTest
    @EnabledOnOs(OS.WINDOWS)
    @CsvSource({"win-pe64-console, x86-64, call WriteFile", "win-pe32-console, 80386, call _WriteFile@20"})
    void aNewWindowsProjectBuildsRunsAndDebugs(String profile, String cpu, String callLine) throws Exception {
        buildRunAndDebug(profile, cpu, callLine, "main.exe");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void aNewLinuxProjectBuildsRunsAndDebugs() throws Exception {
        String out = buildRunAndDebug("linux-elf64", "x86-64", "syscall", "main");
        // Without a console window of its own, the program prints where it was started.
        assertTrue(out.contains("Hello from 64-bit Linux Assembly!"), out);
    }

    /**
     * On Linux the debugged program would share GDB's input, which carries the IDE's commands; a program reading
     * the keyboard must get an empty input instead of swallowing them and hanging the session.
     */
    @Test
    @EnabledOnOs(OS.LINUX)
    void aLinuxProgramReadingTheKeyboardUnderGdbReachesItsBreakpoint() throws Exception {
        new CreateProject(new TomlProjectRepository()).execute(parent, "reader", "linux-elf64", "x86-64", "nasm", null);
        Path root = parent.resolve("reader");
        Files.writeString(root.resolve("src/main.asm"), """
                global main
                section .bss
                    buffer resb 16
                section .text
                main:
                    xor eax, eax                ; sys_read from stdin
                    xor edi, edi
                    lea rsi, [rel buffer]
                    mov edx, 16
                    syscall
                    mov edi, eax                ; after the read: exit with the number of bytes read
                    mov eax, 60
                    syscall
                """);
        var out = new ByteArrayOutputStream();
        assertEquals(0, Main.run(new String[]{"build", root.toString(), "--config", "debug"},
                new PrintStream(out), new PrintStream(out)), out::toString);
        int afterRead = 1 + Files.readAllLines(root.resolve("src/main.asm")).stream().map(String::strip).toList()
                .indexOf("mov edi, eax                ; after the read: exit with the number of bytes read");

        ToolInstallation gdb = DefaultToolRegistry.system().find("gdb").orElseThrow();
        var paused = new CopyOnWriteArrayList<DebugEvent.Paused>();
        var spec = new DebugLaunchSpec(root.resolve("build/debug/bin/main"), root, List.of(), List.of(), gdb, gdb,
                List.of(new Breakpoint("src/main.asm", afterRead)), staging, "gdb");
        try (var session = new GdbDebugEnvironmentProvider().launchDebug(spec, event -> {
            if (event instanceof DebugEvent.Paused stop) {
                paused.add(stop);
            }
        })) {
            for (int i = 0; i < 100 && paused.isEmpty(); i++) {
                Thread.sleep(100);
            }
            assertFalse(paused.isEmpty(), "The program never got past its read");
            assertEquals(afterRead, paused.getFirst().line());

            session.resume();
            assertEquals(0, session.exit().get(30, TimeUnit.SECONDS).exitCode(), "An empty input reads 0 bytes");
        }
    }

    /** @return what the build and the run printed. */
    private String buildRunAndDebug(String profile, String cpu, String callLine, String program) throws Exception {
        var repository = new TomlProjectRepository();
        new CreateProject(repository).execute(parent, "hello", profile, cpu, "nasm", null);
        Path root = parent.resolve("hello");
        // No key press is needed to close the program's window.
        Project created = repository.load(root);
        RunConfiguration run = created.run();
        repository.save(root, new Project(created.schema(), created.info(), created.target(), created.toolchain(),
                created.sources(), created.resources(), created.build(),
                new RunConfiguration(run.environment(), run.isolation(), false, run.cycles(), run.memsize(), run.args()),
                created.debug(), created.dist()));

        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int built = Main.run(new String[]{"build", root.toString(), "--config", "debug"},
                new PrintStream(out), new PrintStream(err));
        assertEquals(0, built, "Build failed.\n" + out + err);
        Path exe = root.resolve("build/debug/bin/" + program);
        assertTrue(Files.isRegularFile(exe));
        assertFalse(Files.exists(root.resolve("obj")), "Outputs never land in the project root");
        assertFalse(Files.exists(root.resolve("bin")), "Outputs never land in the project root");

        int ran = Main.run(new String[]{"run", root.toString()}, new PrintStream(out), new PrintStream(err));
        assertEquals(0, ran, "Run failed.\n" + out + err);

        List<String> source = Files.readAllLines(root.resolve("src/main.asm"));
        int line = 1 + source.stream().map(String::strip).toList().indexOf(callLine);
        assertTrue(line > 0, "The template contains " + callLine);

        ToolInstallation gdb = DefaultToolRegistry.system().find("gdb").orElseThrow();
        var paused = new CopyOnWriteArrayList<DebugEvent.Paused>();
        var spec = new DebugLaunchSpec(exe, root, List.of(), List.of(), gdb, gdb,
                List.of(new Breakpoint("src/main.asm", line)), staging, "gdb");
        try (var session = new GdbDebugEnvironmentProvider().launchDebug(spec, event -> {
            if (event instanceof DebugEvent.Paused stop) {
                paused.add(stop);
            }
        })) {
            for (int i = 0; i < 100 && paused.isEmpty(); i++) {
                Thread.sleep(100);
            }
            assertFalse(paused.isEmpty(), "GDB never stopped at the breakpoint");
            assertTrue(paused.getFirst().file().replace('\\', '/').endsWith("main.asm"), paused.getFirst().file());
            assertEquals(line, paused.getFirst().line());

            session.resume();
            ExitInfo exit = session.exit().get(30, TimeUnit.SECONDS);
            assertEquals(0, exit.exitCode());
            assertEquals(SessionState.EXITED, session.state());
        }

        // A native distribution has no launcher script; printing its name used to fail a successful package.
        var distOut = new ByteArrayOutputStream();
        int packaged = Main.run(new String[]{"dist", root.toString()}, new PrintStream(distOut), new PrintStream(err));
        assertEquals(0, packaged, "Dist failed.\n" + distOut + err);
        assertTrue(distOut.toString().contains("packaged successfully"), distOut.toString());

        assertEquals(0, Main.run(new String[]{"clean", root.toString()}, new PrintStream(out), new PrintStream(err)));
        assertFalse(Files.exists(exe));
        return out.toString();
    }
}
