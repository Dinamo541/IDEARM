package io.github.dinamo541.idearm.toolchain.dos.execution;

import io.github.dinamo541.idearm.domain.build.ProcessRequest;
import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.debug.DebugLaunchSpec;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.model.TargetProfileCatalog;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.domain.port.DebugSession;
import io.github.dinamo541.idearm.domain.port.ProcessLauncher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DosBoxDebugEnvironmentProviderTest {

    @Test
    void supportsDosMzAndComProfiles() {
        DosBoxDebugEnvironmentProvider provider = new DosBoxDebugEnvironmentProvider(request -> null);
        assertEquals("dosbox", provider.id());
        assertTrue(provider.supports(TargetProfileCatalog.DOS_EXE_16));
        TargetProfile comProfile = new TargetProfile("dos-com-16", "x86", "8086", 16, "real", "DOS", "COM", "tiny", "BIN");
        assertTrue(provider.supports(comProfile));
        TargetProfile winProfile = new TargetProfile("win32-pe", "x86", "386", 32, "protected", "Windows", "PE", "flat", "PE");
        assertFalse(provider.supports(winProfile));
    }

    @Test
    void stagesDebugSessionWithTurboDebugger(@TempDir Path tempDir) throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Files.createDirectories(projectRoot.resolve("src"));
        Path mainAsm = projectRoot.resolve("src").resolve("MAIN.ASM");
        Files.writeString(mainAsm, "code segment\nmain proc\nmov ax, 4c00h\nint 21h\nmain endp\ncode ends\nend main");

        Path binDir = tempDir.resolve("bin");
        Files.createDirectories(binDir);
        Path mainExe = binDir.resolve("MAIN.EXE");
        Files.writeString(mainExe, "MZfake");

        Path toolsDir = tempDir.resolve("tools");
        Files.createDirectories(toolsDir);
        Path tdExe = toolsDir.resolve("TD.EXE");
        Files.writeString(tdExe, "MZtd");
        Path tdHelp = toolsDir.resolve("TDHELP.TDH");
        Files.writeString(tdHelp, "help");

        Path stagingDir = tempDir.resolve("staging");

        ToolInstallation dosboxTool = new ToolInstallation("dosbox-x", "2026.08.31",
                tempDir.resolve("dosbox-x.exe"), HostKind.WIN32_CONSOLE, Map.of(), "sha", "detected");
        ToolInstallation tdTool = new ToolInstallation("td", "3.1",
                tdExe, HostKind.DOS_REAL, Map.of("TDHELP.TDH", tdHelp), "sha", "detected");

        DebugLaunchSpec spec = new DebugLaunchSpec(
                mainExe,
                projectRoot,
                List.of(),
                List.of(),
                dosboxTool,
                tdTool,
                List.of(new Breakpoint("src/MAIN.ASM", 3)),
                stagingDir,
                "td"
        );

        CompletableFuture<Integer> exitFuture = new CompletableFuture<>();
        ProcessLauncher fakeLauncher = request -> new ProcessLauncher.LaunchedProcess() {
            @Override public long pid() { return 1234; }
            @Override public boolean alive() { return true; }
            @Override public CompletableFuture<Integer> onExit() { return exitFuture; }
            @Override public void stop() { exitFuture.complete(0); }
            @Override public void close() { stop(); }
        };

        DosBoxDebugEnvironmentProvider provider = new DosBoxDebugEnvironmentProvider(fakeLauncher);
        DebugSession session = provider.launchDebug(spec);
        assertNotNull(session);

        // Verify staging happened inside stagingDir
        try (var stream = Files.list(stagingDir)) {
            Path sessionDir = stream.findFirst().orElseThrow();
            Path driveC = sessionDir.resolve("C");
            Path driveS = sessionDir.resolve("S");
            Path driveT = sessionDir.resolve("T");

            assertTrue(Files.exists(driveC.resolve("MAIN.EXE")));
            assertTrue(Files.exists(driveC.resolve("debug.bat")));
            assertTrue(Files.exists(driveS.resolve("SRC").resolve("MAIN.ASM")));
            assertTrue(Files.exists(driveT.resolve("TD.EXE")));
            assertTrue(Files.exists(driveT.resolve("TDHELP.TDH")));

            String batch = Files.readString(driveC.resolve("debug.bat"));
            assertTrue(batch.contains("T:\\TD.EXE -sdS:\\;S:\\SRC C:\\MAIN.EXE"));
        } finally {
            session.close();
        }
    }
}
