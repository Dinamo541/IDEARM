package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.ProcessRequest;
import io.github.dinamo541.idearm.domain.execution.EnvCapability;
import io.github.dinamo541.idearm.domain.execution.ExecutionSession;
import io.github.dinamo541.idearm.domain.execution.IsolationLevel;
import io.github.dinamo541.idearm.domain.execution.LaunchSpec;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.port.ExecutionEnvironmentProvider;
import io.github.dinamo541.idearm.domain.port.ProcessLauncher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Runs native 32-bit and 64-bit console programs.
 *
 * <p>On Windows the program opens in its own console window, as a DOS program opens in DOSBox: the IDE has no
 * console to share, so without one a program's output would simply vanish and it could not read the keyboard.
 * A small generated batch runs it, reports its exit code and, when asked, waits for a key before the window
 * closes. The whole process tree belongs to a Job Object, so Stop, or the IDE closing, ends it.
 *
 * <p>The program runs from a copy of its folder in the staging area, with the declared resources next to it, so
 * a run never writes into the project.
 */
public final class HostExecutionEnvironmentProvider implements ExecutionEnvironmentProvider {

    private static final Duration NO_TIMEOUT = Duration.ofDays(365);
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    static final String MARKER = ".idearm-native-run";
    static final String EXIT_SENTINEL = "exitcode.txt";

    private final ProcessLauncher launcher;

    public HostExecutionEnvironmentProvider() {
        this(ServiceLoader.load(ProcessLauncher.class).findFirst().orElseGet(JobProcessLauncher::new));
    }

    public HostExecutionEnvironmentProvider(ProcessLauncher launcher) {
        this.launcher = Objects.requireNonNull(launcher, "launcher");
    }

    @Override
    public String id() {
        return "host";
    }

    @Override
    public IsolationLevel isolation() {
        return IsolationLevel.NATIVE;
    }

    @Override
    public Set<EnvCapability> capabilities() {
        return Set.of(EnvCapability.WINDOW);
    }

    @Override
    public boolean supports(TargetProfile profile) {
        if (profile == null || (profile.codeMode() != 32 && profile.codeMode() != 64)) {
            return false;
        }
        return WINDOWS ? "Windows".equalsIgnoreCase(profile.platform()) : "Linux".equalsIgnoreCase(profile.platform());
    }

    @Override
    public ExecutionSession launch(LaunchSpec spec) {
        Path executable = spec.executable().toAbsolutePath().normalize();
        if (!Files.isRegularFile(executable)) {
            throw new DomainException("run.executable.missing", "Executable not found: " + executable, executable);
        }
        Path projectRoot = spec.projectRoot().toAbsolutePath().normalize();
        Path stagingRoot = spec.stagingDirectory().toAbsolutePath().normalize();
        if (stagingRoot.startsWith(projectRoot)) {
            throw new DomainException("execution.staging.insideProject",
                    "Execution staging directory must be outside the project root: " + stagingRoot, stagingRoot);
        }

        Path session = null;
        try {
            Files.createDirectories(stagingRoot);
            session = Files.createTempDirectory(stagingRoot, "native-run-");
            Files.writeString(session.resolve(MARKER), "IDEARM native run\n", StandardCharsets.US_ASCII);
            Path folder = Files.createDirectories(session.resolve("app"));
            Path program = folder.resolve(executable.getFileName().toString());
            Files.copy(executable, program);
            copyResources(projectRoot, folder, spec.resources());

            List<String> command = WINDOWS
                    ? consoleWindow(session, program, spec)
                    : direct(program, spec);
            long start = System.nanoTime();
            // Outside Windows the program has no console window: its output and keyboard go through the IDE.
            ProcessRequest request = ProcessRequest.isolated(command, folder, NO_TIMEOUT);
            ProcessLauncher.LaunchedProcess process = launcher.start(WINDOWS ? request : request.asInteractive());
            return new HostExecutionSession(process, session, start);
        } catch (IOException | RuntimeException failure) {
            HostExecutionSession.deleteSession(session);
            if (failure instanceof DomainException domain) {
                throw domain;
            }
            throw new DomainException("execution.launch.failed",
                    "Failed to start the program: " + failure.getMessage(), failure, failure.getMessage());
        }
    }

    /**
     * {@code cmd /c start "" /wait cmd /c run.cmd}: start opens a new console window and /wait returns when it
     * closes; the batch leaves the program's exit code in {@value #EXIT_SENTINEL}.
     */
    private static List<String> consoleWindow(Path session, Path program, LaunchSpec spec) throws IOException {
        var lines = new ArrayList<String>();
        lines.add("@echo off");
        // The batch is UTF-8, so a program or folder name with accents still resolves.
        lines.add("chcp 65001 > nul");
        lines.add("title " + safe(program.getFileName().toString()));
        StringBuilder call = new StringBuilder("\"").append(program).append('"');
        for (String argument : spec.args()) {
            call.append(' ').append(safe(argument));
        }
        lines.add(call.toString());
        lines.add("set IDEARM_EXIT=%ERRORLEVEL%");
        // start does not pass the exit code on, so it is left in a file; the redirection comes first because
        // "echo 5>file" would redirect stream 5 instead.
        lines.add(">\"%~dp0" + EXIT_SENTINEL + "\" echo %IDEARM_EXIT%");
        if (spec.keepOpen()) {
            lines.add("echo.");
            lines.add("echo Program finished with exit code %IDEARM_EXIT%. Press any key to close this window.");
            lines.add("pause > nul");
        }
        lines.add("exit /b %IDEARM_EXIT%");
        Path batch = session.resolve("run.cmd");
        Files.writeString(batch, String.join("\r\n", lines) + "\r\n", StandardCharsets.UTF_8);
        String shell = Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"), "System32", "cmd.exe")
                .toString();
        // Java passes the empty title as "", which start needs so it does not take the program for a title.
        return List.of(shell, "/c", "start", "", "/wait", shell, "/c", batch.toString());
    }

    private static List<String> direct(Path program, LaunchSpec spec) {
        var command = new ArrayList<String>();
        command.add(program.toString());
        command.addAll(spec.args());
        return command;
    }

    /** Batch metacharacters would turn an argument into a command; they are refused instead. */
    private static String safe(String text) {
        if (text.chars().anyMatch(c -> c < 32 || "&|<>^%\"".indexOf(c) >= 0)) {
            throw new DomainException("run.argument.unsafe",
                    "Program arguments cannot contain & | < > ^ % or quotes: " + text, text);
        }
        return text;
    }

    private static void copyResources(Path projectRoot, Path folder, List<Path> resources) throws IOException {
        for (Path resource : resources) {
            Path absolute = resource.toAbsolutePath().normalize();
            if (!absolute.startsWith(projectRoot)) {
                throw new DomainException("run.resource.traversal",
                        "Runtime resources must live inside the project: " + resource, resource);
            }
            Path destination = folder.resolve(projectRoot.relativize(absolute).toString());
            if (Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
                try (var tree = Files.walk(absolute)) {
                    for (Path file : tree.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                        Path target = destination.resolve(absolute.relativize(file).toString());
                        Files.createDirectories(target.getParent());
                        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                continue;
            }
            Files.createDirectories(destination.getParent());
            Files.copy(absolute, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
