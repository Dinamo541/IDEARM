package io.github.dinamo541.idearm.toolchain.dos.execution;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.ProcessRequest;
import io.github.dinamo541.idearm.domain.execution.EnvCapability;
import io.github.dinamo541.idearm.domain.execution.ExecutionSession;
import io.github.dinamo541.idearm.domain.execution.IsolationLevel;
import io.github.dinamo541.idearm.domain.execution.LaunchSpec;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.port.ExecutionEnvironmentProvider;
import io.github.dinamo541.idearm.domain.port.ProcessLauncher;
import io.github.dinamo541.idearm.toolchain.dos.DosBoxDialect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Runs assembled DOS programs inside an isolated DOSBox session.
 *
 * <p>Only an ASCII staging directory is mounted, holding a copy of the executable and of the declared runtime
 * resources, so a program can neither read nor damage the project sources. The session is started through
 * {@link ProcessLauncher}, which ties the emulator to a Windows Job Object: if the IDE dies, DOSBox dies with it
 * instead of being left behind (ADR-002).
 */
public final class DosBoxExecutionEnvironmentProvider implements ExecutionEnvironmentProvider {

    /** A run has no deadline: the user decides when the program is done. */
    private static final Duration NO_TIMEOUT = Duration.ofDays(365);
    /** 8.3 name of the generated batch that runs the program inside the emulator. */
    private static final String RUN_BATCH = "run.bat";

    private final ProcessLauncher launcher;

    /** Used by {@link ServiceLoader}; resolves the launcher registered by the infrastructure layer. */
    public DosBoxExecutionEnvironmentProvider() {
        this(ServiceLoader.load(ProcessLauncher.class).findFirst().orElseThrow(() -> new DomainException(
                "execution.launcher.missing", "No process launcher is registered, so programs cannot be isolated.")));
    }

    public DosBoxExecutionEnvironmentProvider(ProcessLauncher launcher) {
        this.launcher = Objects.requireNonNull(launcher, "launcher");
    }

    @Override
    public String id() {
        return "dosbox";
    }

    @Override
    public IsolationLevel isolation() {
        return IsolationLevel.EMULATED;
    }

    @Override
    public Set<EnvCapability> capabilities() {
        return Set.of(EnvCapability.WINDOW, EnvCapability.EXTERNAL_DEBUGGER, EnvCapability.SHELL);
    }

    @Override
    public boolean supports(TargetProfile profile) {
        return "DOS".equalsIgnoreCase(profile.platform())
                && ("MZ".equalsIgnoreCase(profile.executableFormat())
                    || "COM".equalsIgnoreCase(profile.executableFormat()));
    }

    @Override
    public ExecutionSession launch(LaunchSpec spec) {
        Path stagingRoot = spec.stagingDirectory().toAbsolutePath().normalize();
        Path projectRoot = spec.projectRoot().toAbsolutePath().normalize();
        if (stagingRoot.startsWith(projectRoot)) {
            throw new DomainException("execution.staging.insideProject",
                    "Execution staging directory must be outside the project root: " + stagingRoot, stagingRoot);
        }
        requireMountable(stagingRoot);

        Path session = null;
        try {
            Files.createDirectories(stagingRoot);
            session = Files.createTempDirectory(stagingRoot, "run-");
            Path driveC = Files.createDirectories(session.resolve("C"));

            String executableName = spec.executable().getFileName().toString().toUpperCase(Locale.ROOT);
            Files.copy(spec.executable(), driveC.resolve(executableName));
            copyResources(projectRoot, driveC, spec.resources());

            DosBoxDialect dialect = DosBoxDialect.forId(spec.environmentInstallation().toolId());
            Files.writeString(driveC.resolve(RUN_BATCH), batch(executableName, spec), StandardCharsets.US_ASCII);
            Path configuration = session.resolve("run.conf");
            Files.writeString(configuration, configuration(driveC, spec, dialect), StandardCharsets.US_ASCII);

            List<String> command = command(spec.environmentInstallation().executable(), dialect, configuration);
            long startNanos = System.nanoTime();
            var process = launcher.start(ProcessRequest.isolated(command, session, NO_TIMEOUT));
            return new DosBoxExecutionSession(process, session, driveC, startNanos);
        } catch (IOException | RuntimeException failure) {
            deleteQuietly(session);
            if (failure instanceof DomainException domain) {
                throw domain;
            }
            throw new DomainException("execution.launch.failed",
                    "Failed to launch the DOSBox session: " + failure.getMessage(), failure, failure.getMessage());
        }
    }

    private static void copyResources(Path projectRoot, Path driveC, List<Path> resources) throws IOException {
        for (Path resource : resources) {
            Path absolute = resource.toAbsolutePath().normalize();
            if (!absolute.startsWith(projectRoot)) {
                throw new DomainException("run.resource.traversal",
                        "Runtime resources must live inside the project: " + resource, resource);
            }
            Path destination = driveC.resolve(projectRoot.relativize(absolute).toString());
            if (Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
                try (var tree = Files.walk(absolute)) {
                    for (Path file : tree.toList()) {
                        if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                            Path target = destination.resolve(absolute.relativize(file).toString());
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

    /**
     * The generated session configuration.
     *
     * <p>The autoexec section only mounts the drive and calls the batch: DOSBox copies autoexec into a 4 KiB
     * AUTOEXEC.BAT and aborts with "Autoexec.bat file overflow" if it grows, which the exit-code ladder does.
     */
    private static String configuration(Path driveC, LaunchSpec spec, DosBoxDialect dialect) {
        var lines = new ArrayList<>(List.of(
                "[sdl]", "fullscreen=false", "autolock=false",
                "[dosbox]", "memsize=" + spec.memsize(),
                "[cpu]", "cycles=" + spec.cycles(),
                "[serial]", "serial1=disabled", "serial2=disabled", "serial3=disabled", "serial4=disabled",
                "[ipx]", "ipx=false"));
        if (dialect == DosBoxDialect.DOSBOX_X) {
            // Long file names let a program open resources such as inv_bottom.spr (Phase 0, S4).
            lines.addAll(List.of("[dos]", "lfn=true", "automount=false",
                    "[ne2000]", "ne2000=false",
                    "[parallel]", "parallel1=disabled", "parallel2=disabled", "parallel3=disabled",
                    "[printer]", "printer=false"));
        }
        // CALL, because a batch started without it never returns: the exit below would never run and the
        // emulator would sit at a DOS prompt after the program ended. Secure mode, after the mount, stops the
        // program from mounting a host folder of its own (S7).
        lines.addAll(List.of("[autoexec]", "@echo off",
                "mount C " + quote(driveC), "C:", DosBoxDialect.SECURE_MODE, "call " + RUN_BATCH, "exit"));
        return String.join("\r\n", lines) + "\r\n";
    }

    /**
     * The batch that runs the program and records how it ended.
     *
     * <p>DOS only offers "if errorlevel n", which is true for n and anything above, so the exact status is found
     * by climbing from 1 to 255 and keeping the last match. DOSBox reports nothing about the program itself, so
     * the status is left in a sentinel file the session reads afterwards.
     */
    private static String batch(String executableName, LaunchSpec spec) {
        String arguments = String.join(" ", spec.args());
        var lines = new ArrayList<String>();
        lines.add("@echo off");
        lines.add(executableName + (arguments.isBlank() ? "" : " " + arguments));
        lines.add("set RC=0");
        for (int level = 1; level <= 255; level++) {
            lines.add("if errorlevel " + level + " set RC=" + level);
        }
        lines.add("echo RC=%RC%>C:\\" + DosBoxExecutionSession.EXIT_SENTINEL);
        if (spec.keepOpen()) {
            lines.addAll(List.of("echo.", "echo ========================================================",
                    "echo Program finished with exit code %RC%.", "echo Press any key to close DOSBox...",
                    "pause > nul"));
        }
        // The batch closes the emulator itself, so a run always ends even if the shell returns here.
        lines.add("exit");
        return String.join("\r\n", lines) + "\r\n";
    }

    private static List<String> command(Path executable, DosBoxDialect dialect, Path configuration) {
        return dialect.sessionCommand(executable, configuration);
    }

    private static String quote(Path path) {
        return '"' + path.toString() + '"';
    }

    private static void requireMountable(Path root) {
        if (!io.github.dinamo541.idearm.domain.execution.StagingPaths.isMountable(root.toString())) {
            throw new DomainException("execution.staging.nonAscii",
                    "Choose a staging root with ASCII characters and no spaces: " + root, root);
        }
    }

    private static void deleteQuietly(Path session) {
        if (session == null) {
            return;
        }
        try (var tree = Files.walk(session)) {
            tree.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort: the stale session sweep removes what is left.
                }
            });
        } catch (IOException ignored) {
            // Nothing to clean.
        }
    }
}
