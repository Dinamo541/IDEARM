package io.github.dinamo541.idearm.toolchain.dos.execution;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.ProcessRequest;
import io.github.dinamo541.idearm.domain.debug.DebugLaunchSpec;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider;
import io.github.dinamo541.idearm.domain.port.DebugSession;
import io.github.dinamo541.idearm.domain.port.ProcessLauncher;
import io.github.dinamo541.idearm.toolchain.dos.DosBoxDialect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Launches an interactive DOSBox debugging session with Turbo Debugger or CodeView.
 */
public final class DosBoxDebugEnvironmentProvider implements DebugEnvironmentProvider {

    private static final Duration NO_TIMEOUT = Duration.ofDays(365);
    private static final String DEBUG_BATCH = "debug.bat";

    private final ProcessLauncher launcher;

    public DosBoxDebugEnvironmentProvider() {
        this(ServiceLoader.load(ProcessLauncher.class).findFirst().orElseThrow(() -> new DomainException(
                "execution.launcher.missing", "No process launcher registered for debug isolation.")));
    }

    public DosBoxDebugEnvironmentProvider(ProcessLauncher launcher) {
        this.launcher = Objects.requireNonNull(launcher, "launcher");
    }

    @Override
    public String id() {
        return "dosbox";
    }

    @Override
    public boolean supports(TargetProfile profile) {
        return "DOS".equalsIgnoreCase(profile.platform())
                && ("MZ".equalsIgnoreCase(profile.executableFormat())
                || "COM".equalsIgnoreCase(profile.executableFormat()));
    }

    @Override
    public DebugSession launchDebug(DebugLaunchSpec spec) {
        Path stagingRoot = spec.stagingDirectory().toAbsolutePath().normalize();
        Path projectRoot = spec.projectRoot().toAbsolutePath().normalize();
        requireMountable(stagingRoot);

        Path session = null;
        try {
            Files.createDirectories(stagingRoot);
            session = Files.createTempDirectory(stagingRoot, "debug-");
            Path driveC = Files.createDirectories(session.resolve("C"));
            Path driveS = Files.createDirectories(session.resolve("S"));
            Path driveT = Files.createDirectories(session.resolve("T"));

            // 1. Stage executable into Drive C
            String executableName = spec.executable().getFileName().toString().toUpperCase(Locale.ROOT);
            Files.copy(spec.executable(), driveC.resolve(executableName));

            // 2. Stage runtime resources into Drive C
            copyResources(projectRoot, driveC, spec.resources());

            // 3. Stage source files into Drive S so external debuggers can show source code
            copySources(projectRoot, driveS);

            // 4. Stage debugger tools into Drive T
            copyDebuggerTools(spec, driveT);

            // 5. Generate DEBUG.BAT and debug.conf
            DosBoxDialect dialect = DosBoxDialect.forId(spec.environmentInstallation().toolId());
            Files.writeString(driveC.resolve(DEBUG_BATCH), debugBatch(executableName, spec), StandardCharsets.US_ASCII);

            Path configuration = session.resolve("debug.conf");
            Files.writeString(configuration, debugConfiguration(session, driveC, driveS, driveT, spec, dialect),
                    StandardCharsets.US_ASCII);

            List<String> command = debugCommand(spec.environmentInstallation().executable(), dialect, configuration);
            long startNanos = System.nanoTime();
            var process = launcher.start(ProcessRequest.isolated(command, session, NO_TIMEOUT));
            return new DosBoxDebugSession(process, session, driveC, startNanos);
        } catch (IOException | RuntimeException failure) {
            deleteQuietly(session);
            if (failure instanceof DomainException domain) {
                throw domain;
            }
            throw new DomainException("debug.launch.failed",
                    "Failed to launch DOSBox debug session: " + failure.getMessage(), failure, failure.getMessage());
        }
    }

    private static void copySources(Path projectRoot, Path driveS) throws IOException {
        try (var walk = Files.walk(projectRoot)) {
            for (Path path : walk.toList()) {
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    String name = path.getFileName().toString().toUpperCase(Locale.ROOT);
                    if (name.endsWith(".ASM") || name.endsWith(".INC")) {
                        Path rel = projectRoot.relativize(path);
                        Path target = driveS.resolve(rel.toString().toUpperCase(Locale.ROOT));
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target);
                    }
                }
            }
        }
    }

    private static void copyDebuggerTools(DebugLaunchSpec spec, Path driveT) throws IOException {
        if (spec.debuggerInstallation() == null || spec.debuggerInstallation().executable() == null) {
            return;
        }
        Path debuggerExe = spec.debuggerInstallation().executable();
        if (Files.isRegularFile(debuggerExe)) {
            Files.copy(debuggerExe, driveT.resolve(debuggerExe.getFileName().toString().toUpperCase(Locale.ROOT)));
        }
        for (Path companion : spec.debuggerInstallation().companions().values()) {
            if (Files.isRegularFile(companion)) {
                Files.copy(companion, driveT.resolve(companion.getFileName().toString().toUpperCase(Locale.ROOT)));
            }
        }
    }

    private static void copyResources(Path projectRoot, Path driveC, List<Path> resources) throws IOException {
        for (Path resource : resources) {
            Path absolute = resource.toAbsolutePath().normalize();
            if (!absolute.startsWith(projectRoot)) {
                throw new DomainException("run.resource.traversal",
                        "Runtime resources must live inside the project: " + resource, resource);
            }
            Path destination = driveC.resolve(projectRoot.relativize(absolute).toString().toUpperCase(Locale.ROOT));
            if (Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
                try (var tree = Files.walk(absolute)) {
                    for (Path file : tree.toList()) {
                        if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                            Path target = destination.resolve(absolute.relativize(file).toString().toUpperCase(Locale.ROOT));
                            Files.createDirectories(target.getParent());
                            Files.copy(file, target);
                        }
                    }
                }
                continue;
            }
            if (destination.getParent() != null) {
                Files.createDirectories(destination.getParent());
            }
            Files.copy(absolute, destination);
        }
    }

    private static String debugBatch(String executableName, DebugLaunchSpec spec) {
        var lines = new ArrayList<String>();
        lines.add("@echo off");
        lines.add("PATH T:\\;%PATH%");

        String kind = spec.debuggerKind().toLowerCase(Locale.ROOT);
        if (kind.contains("cv") || kind.contains("codeview")) {
            lines.add("T:\\CV.EXE /S:S:\\ C:\\" + executableName);
        } else {
            // Turbo Debugger default
            lines.add("T:\\TD.EXE -sdS:\\;S:\\SRC C:\\" + executableName);
        }

        lines.add("set RC=0");
        for (int i = 1; i <= 255; i++) {
            lines.add("if errorlevel " + i + " set RC=" + i);
        }
        lines.add("echo RC=%RC%>C:\\EXITCODE.TXT");
        lines.add("exit");
        return String.join("\r\n", lines) + "\r\n";
    }

    private static String debugConfiguration(Path session, Path driveC, Path driveS, Path driveT,
                                              DebugLaunchSpec spec, DosBoxDialect dialect) {
        var lines = new ArrayList<>(List.of(
                "[sdl]", "fullscreen=false", "autolock=false",
                "[dosbox]", "memsize=16",
                "[cpu]", "core=auto", "cycles=3000",
                "[serial]", "serial1=disabled", "serial2=disabled", "serial3=disabled", "serial4=disabled",
                "[ipx]", "ipx=false"
        ));
        if (dialect == DosBoxDialect.DOSBOX_X) {
            lines.addAll(List.of(
                    "[dos]", "lfn=true", "automount=false", "automountall=false",
                    "automount drive directories=false", "dos clipboard device enable=false",
                    "dos clipboard api=false",
                    "[ne2000]", "ne2000=false",
                    "[parallel]", "parallel1=disabled", "parallel2=disabled", "parallel3=disabled",
                    "[printer]", "printer=false"
            ));
        }
        String ro = dialect.readOnlyMounts() ? " -ro" : "";
        lines.addAll(List.of(
                "[autoexec]",
                "mount C \"" + driveC.toAbsolutePath().toString().replace('\\', '/') + "\"",
                "mount S \"" + driveS.toAbsolutePath().toString().replace('\\', '/') + "\"" + ro,
                "mount T \"" + driveT.toAbsolutePath().toString().replace('\\', '/') + "\"" + ro,
                "C:",
                DosBoxDialect.SECURE_MODE,
                "call " + DEBUG_BATCH,
                "exit"
        ));
        return String.join("\r\n", lines) + "\r\n";
    }

    private static List<String> debugCommand(Path executable, DosBoxDialect dialect, Path configuration) {
        return dialect.sessionCommand(executable.toAbsolutePath(), configuration.toAbsolutePath());
    }

    private static void requireMountable(Path directory) {
        if (!io.github.dinamo541.idearm.domain.execution.StagingPaths.isMountable(directory.toString())) {
            throw new DomainException("execution.staging.nonAscii",
                    "Staging directory must contain only ASCII characters without spaces: " + directory, directory);
        }
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
