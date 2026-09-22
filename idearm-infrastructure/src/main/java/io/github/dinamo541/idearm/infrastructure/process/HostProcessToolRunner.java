package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.BuildPhase;
import io.github.dinamo541.idearm.domain.build.BuildPlan;
import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.build.ProcessRequest;
import io.github.dinamo541.idearm.domain.build.ProcessResult;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.build.ToolResult;
import io.github.dinamo541.idearm.domain.build.ToolRunResult;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.ResolvedToolchain;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.domain.port.ProcessExecutor;
import io.github.dinamo541.idearm.domain.port.ToolRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Runs build plans whose tools are native programs (NASM, GNU ld).
 *
 * <p>Tools run from the project folder, so sources and include folders resolve as written, but every output is
 * redirected into a disposable session folder outside the project. The workspace then publishes the finished
 * artifacts into {@code build/<configuration>}, exactly as it does for DOS builds: a failed or cancelled build
 * never leaves half-written files in the project.
 */
public final class HostProcessToolRunner implements ToolRunner {

    private static final String MARKER = ".idearm-native-session";
    private static final Duration STALE_SESSION_AGE = Duration.ofHours(6);

    private final ProcessExecutor processes;
    private final Path stagingRoot;
    private final Map<Path, Path> sessions = new ConcurrentHashMap<>();

    public HostProcessToolRunner() {
        this(new ProcessService());
    }

    public HostProcessToolRunner(ProcessExecutor processes) {
        this(processes, Path.of(System.getProperty("java.io.tmpdir"), "idearm-native"));
    }

    public HostProcessToolRunner(ProcessExecutor processes, Path stagingRoot) {
        this.processes = Objects.requireNonNull(processes, "processes");
        this.stagingRoot = stagingRoot.toAbsolutePath().normalize();
        sweepStaleSessions();
    }

    /** Whether every step of a plan is a native program this runner can start directly. */
    public static boolean canRun(BuildPlan plan) {
        return plan.steps().stream().allMatch(step ->
                step.hostKind() == HostKind.WIN64
                        || step.hostKind() == HostKind.WIN32_CONSOLE
                        || step.hostKind() == HostKind.LINUX_ELF);
    }

    @Override
    public ToolRunResult run(BuildPlan plan, Path projectRoot, ResolvedToolchain toolchain,
                             CancellationToken cancellation, Duration timeout) {
        Path root = projectRoot.toAbsolutePath().normalize();
        CancellationToken token = cancellation == null ? CancellationToken.NONE : cancellation;
        if (token.cancelled()) {
            return new ToolRunResult(BuildStatus.CANCELLED, List.of(), null, "Build cancelled.", Function.identity());
        }

        Path session = createSession(root);
        var log = new StringBuilder();
        var results = new ArrayList<ToolResult>();
        Set<String> outputs = new HashSet<>(plan.outputs());
        boolean assembleFailed = false;
        BuildStatus status = BuildStatus.SUCCEEDED;
        try {
            for (ToolInvocation invocation : plan.steps()) {
                if (token.cancelled()) {
                    status = BuildStatus.CANCELLED;
                    break;
                }
                // Every source is assembled so all errors show at once; linking needs them all.
                if (assembleFailed && invocation.phase() == BuildPhase.LINK) {
                    continue;
                }
                ToolInstallation tool = toolchain.tools().get(invocation.toolId());
                if (tool == null) {
                    throw new DomainException("toolchain.missing-tool",
                            "No tool installation registered for " + invocation.toolId(), invocation.toolId());
                }
                for (String output : invocation.outputs()) {
                    Files.createDirectories(staged(session, output).getParent());
                }

                var command = new ArrayList<String>();
                command.add(tool.executable().toString());
                for (String argument : invocation.arguments()) {
                    command.add(redirect(argument, outputs, session));
                }
                log.append('[').append(invocation.phase()).append("] ").append(String.join(" ", command)).append('\n');

                ProcessResult process = processes.run(ProcessRequest.isolated(command, root, timeout), token);
                if (!process.output().isBlank()) {
                    log.append(process.output().stripTrailing()).append('\n');
                }
                results.add(new ToolResult(invocation, process.exitCode(), process.output()));
                if (process.cancelled()) {
                    status = BuildStatus.CANCELLED;
                    break;
                }
                if (process.timedOut()) {
                    status = BuildStatus.TIMED_OUT;
                    break;
                }
                if (process.exitCode() != 0) {
                    status = BuildStatus.FAILED;
                    assembleFailed |= invocation.phase() == BuildPhase.ASSEMBLE;
                }
            }
            if (status == BuildStatus.SUCCEEDED) {
                status = verifyArtifacts(plan, session, log);
            }
        } catch (IOException failure) {
            deleteQuietly(session);
            throw new DomainException("build.staging-failed",
                    "Cannot prepare the native build folder: " + failure.getMessage(), failure, failure.getMessage());
        } catch (RuntimeException failure) {
            deleteQuietly(session);
            throw failure;
        }
        sessions.put(session, session);
        return new ToolRunResult(status, List.copyOf(results), session, log.toString(), Function.identity());
    }

    @Override
    public void release(ToolRunResult result) {
        if (result.outputDirectory() != null && sessions.remove(result.outputDirectory()) != null) {
            deleteQuietly(result.outputDirectory());
        }
    }

    /** Output paths, and linker options that name one ({@code -Map=...}), point into the session. */
    private static String redirect(String argument, Set<String> outputs, Path session) {
        if (outputs.contains(argument)) {
            return staged(session, argument).toString();
        }
        int equals = argument.indexOf('=');
        if (argument.startsWith("-") && equals > 0 && outputs.contains(argument.substring(equals + 1))) {
            return argument.substring(0, equals + 1) + staged(session, argument.substring(equals + 1));
        }
        return argument;
    }

    private static Path staged(Path session, String output) {
        Path target = session.resolve(output.replace('\\', '/')).normalize();
        if (!target.startsWith(session) || target.equals(session)) {
            throw new DomainException("build.unsafe-path", "A build output escaped its build folder: " + output, output);
        }
        return target;
    }

    /** A build only succeeds when every planned artifact was written. */
    private static BuildStatus verifyArtifacts(BuildPlan plan, Path session, StringBuilder log) {
        for (String output : plan.outputs()) {
            if (!Files.isRegularFile(staged(session, output), LinkOption.NOFOLLOW_LINKS)) {
                log.append("Build failed: expected output is missing: ").append(output).append('\n');
                return BuildStatus.FAILED;
            }
        }
        return BuildStatus.SUCCEEDED;
    }

    private Path createSession(Path projectRoot) {
        try {
            if (stagingRoot.startsWith(projectRoot)) {
                throw new DomainException("build.unsafe-staging", "Build staging must be outside the project: " + stagingRoot, stagingRoot);
            }
            Files.createDirectories(stagingRoot);
            Path session = Files.createTempDirectory(stagingRoot, "native-");
            Files.writeString(session.resolve(MARKER), "IDEARM native build session\n", StandardCharsets.US_ASCII);
            return session;
        } catch (IOException failure) {
            throw new DomainException("build.staging-failed",
                    "Cannot create the native build folder in " + stagingRoot + ": " + failure.getMessage(), failure, failure.getMessage(), stagingRoot);
        }
    }

    /** Removes sessions left behind by an IDE that stopped before releasing them. */
    private void sweepStaleSessions() {
        if (!Files.isDirectory(stagingRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Instant deadline = Instant.now().minus(STALE_SESSION_AGE);
        try (var entries = Files.list(stagingRoot)) {
            for (Path candidate : entries.toList()) {
                try {
                    if (Files.isRegularFile(candidate.resolve(MARKER), LinkOption.NOFOLLOW_LINKS)
                            && Files.readAttributes(candidate, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
                            .lastModifiedTime().toInstant().isBefore(deadline)) {
                        deleteQuietly(candidate);
                    }
                } catch (IOException ignored) {
                    // A session another IDE instance still uses stays untouched.
                }
            }
        } catch (IOException ignored) {
            // Nothing to sweep.
        }
    }

    private static void deleteQuietly(Path session) {
        if (session == null || !Files.isRegularFile(session.resolve(MARKER), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var tree = Files.walk(session)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // The next sweep removes what is left.
        }
    }
}
