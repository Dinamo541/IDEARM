package io.github.dinamo541.idearm.toolchain.dos;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.BuildPlan;
import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.build.ToolResult;
import io.github.dinamo541.idearm.domain.build.ToolRunResult;
import io.github.dinamo541.idearm.domain.model.ResolvedToolchain;
import io.github.dinamo541.idearm.domain.port.ProcessExecutor;
import io.github.dinamo541.idearm.domain.port.ToolRunner;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes a complete DOS build in one DOSBox session. Only disposable copies inside an ASCII staging folder are
 * mounted, so a program or tool can never reach the project sources.
 *
 * <p>The application must release each result after publishing or inspecting its staged outputs.
 */
public final class DosBoxToolRunner implements ToolRunner {

    /** Sessions older than this were abandoned by a process that died before releasing them. */
    private static final Duration STALE_SESSION_AGE = Duration.ofHours(6);

    private final ProcessExecutor processes;
    private final Path stagingRoot;
    private final Map<Path, Path> sessions = new ConcurrentHashMap<>();

    public DosBoxToolRunner(ProcessExecutor processes, Path stagingRoot) {
        this.processes = Objects.requireNonNull(processes, "processes");
        this.stagingRoot = stagingRoot.toAbsolutePath().normalize();
        // An unmountable root must not stop the IDE from starting: native projects never use it, and a DOS build
        // reports the problem when it runs.
        DosStaging.sweepStaleSessions(this.stagingRoot, STALE_SESSION_AGE);
    }

    @Override
    public ToolRunResult run(BuildPlan plan, Path projectRoot, ResolvedToolchain tools,
                             CancellationToken cancellation, Duration timeout) {
        DosStaging.requireMountable(stagingRoot);
        Path root = projectRoot.toAbsolutePath().normalize();
        List<String> sources = DosStaging.sources(plan);
        var sourcePaths = new PathMapper(root, sources);
        if (cancellation.cancelled()) {
            return new ToolRunResult(BuildStatus.CANCELLED, List.of(), null, "Build cancelled.", sourcePaths);
        }
        Path session = null;
        try {
            session = DosStaging.createSession(stagingRoot, "S", root);
            Path driveC = session.resolve("C");
            Path driveS = session.resolve("S");
            DosStaging.copySources(root, driveS, sources);
            DosStaging.copyIncludes(root, driveS, plan.project().sources().include());
            DosStaging.prepareOutputs(plan, driveC);

            var log = new StringBuilder();
            var results = new ArrayList<ToolResult>();
            BuildStatus status = DosStaging.runDosSession(processes, session, DosStaging.steps(plan), tools,
                    cancellation, timeout, log, results);
            if (status == BuildStatus.SUCCEEDED) {
                status = DosStaging.validateArtifacts(plan, driveC, results, log);
            }
            Path outputDirectory = driveC;
            if (status == BuildStatus.SUCCEEDED) {
                outputDirectory = DosStaging.exportArtifacts(plan, session);
            } else {
                DosStaging.discardOutputs(plan, driveC);
            }
            sessions.put(outputDirectory, session);
            return new ToolRunResult(status, List.copyOf(results), outputDirectory, log.toString(), sourcePaths);
        } catch (IOException exception) {
            cleanupAfterFailure(session, exception);
            throw new DomainException("build.staging-failed",
                    "Cannot stage or read the DOS build: " + exception.getMessage(), exception, exception.getMessage());
        } catch (RuntimeException exception) {
            cleanupAfterFailure(session, exception);
            throw exception;
        }
    }

    @Override
    public void release(ToolRunResult result) {
        if (result.outputDirectory() == null) {
            return;
        }
        Path session = sessions.remove(result.outputDirectory());
        if (session == null) {
            return;
        }
        try {
            DosStaging.deleteSession(stagingRoot, session);
        } catch (IOException exception) {
            throw new DomainException("build.staging-cleanup-failed",
                    "Cannot remove the temporary DOS session: " + session, exception, session);
        }
    }

    private void cleanupAfterFailure(Path session, Exception exception) {
        if (session == null) {
            return;
        }
        try {
            DosStaging.deleteSession(stagingRoot, session);
        } catch (IOException cleanup) {
            exception.addSuppressed(cleanup);
        }
    }
}
