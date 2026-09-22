package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.debug.DebugEvent;
import io.github.dinamo541.idearm.domain.debug.DebugLaunchSpec;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider;
import io.github.dinamo541.idearm.domain.port.DebugSession;
import io.github.dinamo541.idearm.infrastructure.tools.ToolDetector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Debugs native 32-bit and 64-bit programs with GDB, driven through its machine interface (GDB/MI).
 *
 * <p>GDB and everything it starts belong to a Job Object, so ending the session, or the IDE, ends the program.
 */
public final class GdbDebugEnvironmentProvider implements DebugEnvironmentProvider {

    public static final String ID = "gdb";
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supports(TargetProfile profile) {
        if (profile == null || profile.isDos() || (profile.codeMode() != 32 && profile.codeMode() != 64)) {
            return false;
        }
        // GDB debugs programs this machine can run: Windows programs on Windows, Linux programs on Linux.
        return WINDOWS ? "Windows".equalsIgnoreCase(profile.platform()) : "Linux".equalsIgnoreCase(profile.platform());
    }

    @Override
    public DebugSession launchDebug(DebugLaunchSpec spec) {
        return launchDebug(spec, e -> {});
    }

    @Override
    public DebugSession launchDebug(DebugLaunchSpec spec, Consumer<DebugEvent> events) {
        Objects.requireNonNull(spec, "spec cannot be null");
        Consumer<DebugEvent> eventSink = (events != null) ? events : e -> {};

        Path gdbExe = resolveGdbExecutable(spec);

        List<String> command = new ArrayList<>();
        command.add(gdbExe.toAbsolutePath().toString());
        command.add("--interpreter=mi3");
        command.add("--nx");
        command.add("--quiet");

        WindowsJob job = null;
        try {
            if (WINDOWS) {
                try {
                    job = WindowsJob.create();
                } catch (Exception ignored) {}
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            if (spec.projectRoot() != null && Files.isDirectory(spec.projectRoot())) {
                pb.directory(spec.projectRoot().toFile());
            } else if (spec.executable().getParent() != null) {
                pb.directory(spec.executable().getParent().toFile());
            }
            pb.redirectErrorStream(true);

            long startNanos = System.nanoTime();
            Process process = pb.start();

            if (job != null) {
                job.assign(process);
            }

            GdbProcessDebugSession session = new GdbProcessDebugSession(process, job, eventSink, startNanos);
            session.initialize(spec);
            return session;
        } catch (IOException e) {
            if (job != null) {
                job.close();
            }
            throw new DomainException("debug.launch.failed", "Failed launching GDB debugger: " + e.getMessage(), e, e.getMessage());
        }
    }

    /** The GDB the registry chose; without one, the first GDB found on this machine (MSYS2 folders, PATH). */
    private static Path resolveGdbExecutable(DebugLaunchSpec spec) {
        if (spec.debuggerInstallation() != null && Files.isRegularFile(spec.debuggerInstallation().executable())) {
            return spec.debuggerInstallation().executable();
        }
        var detected = ToolDetector.detectAll().get("gdb");
        if (detected != null && !detected.isEmpty()) {
            return detected.getFirst().executable();
        }
        throw new DomainException("debug.gdb.missing",
                "GDB (the GNU Debugger) was not found. Install it, for example with MSYS2: "
                        + "pacman -S mingw-w64-ucrt-x86_64-gdb");
    }
}
