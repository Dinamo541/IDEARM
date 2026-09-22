package io.github.dinamo541.idearm.toolchain.dos;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.BuildPhase;
import io.github.dinamo541.idearm.domain.build.BuildPlan;
import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.build.ProcessRequest;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.build.ToolResult;
import io.github.dinamo541.idearm.domain.build.ToolRunResult;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.ResolvedToolchain;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.domain.port.ProcessExecutor;
import io.github.dinamo541.idearm.domain.port.ToolRunner;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes build plans whose tools do not share a single host.
 *
 * <p>MASM 6.11 is the motivating case: ML is a Win32 console binary that runs natively on the host, while its
 * 16-bit LINK only runs inside DOSBox. Host steps run first and write their objects straight into the staged
 * output drive, then the remaining DOS steps run in one DOSBox session. Plans made entirely of DOS steps are
 * delegated to {@link DosBoxToolRunner}.
 *
 * <p>As in the pure DOS runner, every source is assembled even after one fails, so the user sees all errors at
 * once, and linking is skipped as soon as any source failed.
 */
public final class HybridToolRunner implements ToolRunner {

    private final ProcessExecutor processes;
    private final Path stagingRoot;
    private final DosBoxToolRunner dosRunner;
    private final Map<Path, Path> sessions = new ConcurrentHashMap<>();

    public HybridToolRunner(ProcessExecutor processes, Path stagingRoot) {
        this.processes = Objects.requireNonNull(processes, "processes");
        this.stagingRoot = stagingRoot.toAbsolutePath().normalize();
        this.dosRunner = new DosBoxToolRunner(processes, stagingRoot);
    }

    @Override
    public ToolRunResult run(BuildPlan plan, Path projectRoot, ResolvedToolchain tools,
                             CancellationToken cancellation, Duration timeout) {
        DosStaging.requireMountable(stagingRoot);
        List<DosStaging.Step> steps = DosStaging.steps(plan);
        if (steps.stream().noneMatch(step -> runsOnHost(step.invocation()))) {
            return dosRunner.run(plan, projectRoot, tools, cancellation, timeout);
        }

        Path root = projectRoot.toAbsolutePath().normalize();
        List<String> sources = DosStaging.sources(plan);
        if (cancellation.cancelled()) {
            return new ToolRunResult(BuildStatus.CANCELLED, List.of(), null, "Build cancelled.",
                    new PathMapper(root, sources));
        }

        Path session = null;
        try {
            session = DosStaging.createSession(stagingRoot, "H", root);
            Path driveC = session.resolve("C");
            Path driveS = session.resolve("S");
            var sourcePaths = new PathMapper(root, sources, driveS);
            DosStaging.copySources(root, driveS, sources);
            DosStaging.copyIncludes(root, driveS, plan.project().sources().include());
            DosStaging.prepareOutputs(plan, driveC);

            var log = new StringBuilder();
            var byIndex = new TreeMap<Integer, ToolResult>();
            boolean failed = false;
            boolean cancelledDuringHostSteps = false;

            var dosSteps = new ArrayList<DosStaging.Step>();
            for (DosStaging.Step step : steps) {
                if (!runsOnHost(step.invocation())) {
                    dosSteps.add(step);
                    continue;
                }
                if (cancellation.cancelled()) {
                    cancelledDuringHostSteps = true;
                    break;
                }
                if (failed && step.invocation().phase() == BuildPhase.LINK) {
                    continue;
                }
                ToolResult result = runHostStep(step, tools, driveC, driveS, timeout, cancellation, log);
                byIndex.put(step.index(), result);
                failed |= result.exitCode() == null || result.exitCode() != 0;
            }

            // A host failure is invisible to the DOS batch, so skip linking here instead.
            if (failed) {
                dosSteps.removeIf(step -> step.invocation().phase() == BuildPhase.LINK);
            }

            BuildStatus status;
            if (cancelledDuringHostSteps) {
                status = BuildStatus.CANCELLED;
            } else if (dosSteps.isEmpty()) {
                status = BuildStatus.SUCCEEDED;
            } else {
                var dosResults = new ArrayList<ToolResult>();
                status = DosStaging.runDosSession(processes, session, List.copyOf(dosSteps), tools, cancellation,
                        timeout, log, dosResults);
                for (int i = 0; i < dosResults.size(); i++) {
                    byIndex.put(dosSteps.get(i).index(), dosResults.get(i));
                }
            }

            List<ToolResult> results = inPlanOrder(byIndex, steps.size());
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
            return new ToolRunResult(status, results, outputDirectory, log.toString(), sourcePaths);
        } catch (IOException exception) {
            cleanupAfterFailure(session, exception);
            throw new DomainException("build.staging-failed",
                    "Cannot stage or execute the hybrid build: " + exception.getMessage(), exception, exception.getMessage());
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
            dosRunner.release(result);
            return;
        }
        try {
            DosStaging.deleteSession(stagingRoot, session);
        } catch (IOException exception) {
            throw new DomainException("build.staging-cleanup-failed",
                    "Cannot remove the temporary hybrid session: " + session, exception, session);
        }
    }

    private static boolean runsOnHost(ToolInvocation invocation) {
        return invocation.hostKind() == HostKind.WIN32_CONSOLE || invocation.hostKind() == HostKind.WIN64;
    }

    /** Results must reach the application in plan order and without gaps, so it can match steps by position. */
    private static List<ToolResult> inPlanOrder(Map<Integer, ToolResult> byIndex, int plannedSteps) {
        var ordered = new ArrayList<ToolResult>();
        for (int index = 0; index < plannedSteps; index++) {
            ToolResult result = byIndex.get(index);
            if (result == null) {
                break;
            }
            ordered.add(result);
        }
        return List.copyOf(ordered);
    }

    private ToolResult runHostStep(DosStaging.Step step, ResolvedToolchain tools, Path driveC, Path driveS,
                                   Duration timeout, CancellationToken cancellation, StringBuilder log) {
        ToolInvocation invocation = step.invocation();
        ToolInstallation tool = tools.tools().get(invocation.toolId());
        if (tool == null) {
            throw new DomainException("toolchain.missing-tool",
                    "No tool installation registered for " + invocation.toolId(), invocation.toolId());
        }
        var command = new ArrayList<String>();
        command.add(tool.executable().toString());
        for (String argument : invocation.arguments()) {
            command.add(translate(argument, driveC, driveS));
        }
        log.append('[').append(DosStaging.stepName(step.index())).append(" host command]: ").append(command)
                .append('\n');
        var process = processes.run(ProcessRequest.isolated(command, driveC, timeout), cancellation);
        log.append(process.output()).append('\n');
        return new ToolResult(invocation, process.exitCode(), process.output());
    }

    /**
     * Host tools receive real paths: outputs land in the staged output drive and inputs come from the staged
     * source drive, so the tool still never sees the project directory.
     */
    private static String translate(String argument, Path driveC, Path driveS) {
        if (argument.startsWith("/Fo") || argument.startsWith("/Fl")) {
            return argument.substring(0, 3) + resolve(driveC, argument.substring(3));
        }
        if (argument.startsWith("/I")) {
            return "/I" + resolve(driveS, argument.substring(2));
        }
        if (argument.toUpperCase(Locale.ROOT).endsWith(".ASM")) {
            return resolve(driveS, argument).toString();
        }
        return argument;
    }

    private static Path resolve(Path drive, String relative) {
        return DosStaging.safeOutput(drive, relative);
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
