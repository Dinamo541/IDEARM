package io.github.dinamo541.idearm.toolchain.dos;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.BuildPhase;
import io.github.dinamo541.idearm.domain.build.BuildPlan;
import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.build.ProcessRequest;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.build.ToolResult;
import io.github.dinamo541.idearm.domain.execution.StagingPaths;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.ResolvedToolchain;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.domain.port.ProcessExecutor;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Staging, batch protocol and session lifetime shared by every DOS-hosted runner.
 *
 * <p>Every rule here comes from the Phase 0 findings recorded in ADR-002: only ASCII staging copies are mounted,
 * redirections never sit inside an IF (DOSBox opens them before evaluating the condition), success is decided by
 * explicit exit codes because TLINK writes its executable even when it fails, and long command lines move into
 * response files.
 */
final class DosStaging {

    static final String MARKER = ".idearm-session";
    static final Charset CP437 = Charset.forName("IBM437");

    /** A DOS program only receives 126 characters of command tail. */
    static final int MAX_COMMAND = 126;

    private DosStaging() {
    }

    /** One planned invocation together with its position in the plan, which names its log files. */
    record Step(int index, ToolInvocation invocation) {
    }

    static List<Step> steps(BuildPlan plan) {
        var steps = new ArrayList<Step>();
        for (int i = 0; i < plan.steps().size(); i++) {
            steps.add(new Step(i, plan.steps().get(i)));
        }
        return List.copyOf(steps);
    }

    static List<String> sources(BuildPlan plan) {
        var sources = new ArrayList<String>();
        sources.add(plan.project().sources().entry());
        sources.addAll(plan.project().sources().modules());
        return List.copyOf(sources);
    }

    static String stepName(int index) {
        return "S%03d".formatted(index + 1);
    }

    // ---------------------------------------------------------------- session lifetime

    static void requireMountable(Path root) {
        if (!StagingPaths.isMountable(root.toString())) {
            throw new DomainException("build.non-ascii-staging",
                    "Choose a staging root with ASCII characters and no spaces: " + root, root);
        }
    }

    static Path createSession(Path stagingRoot, String prefix, Path projectRoot) throws IOException {
        if (stagingRoot.startsWith(projectRoot)) {
            throw new DomainException("build.unsafe-staging",
                    "Tool staging must be outside the project: " + stagingRoot, stagingRoot);
        }
        Files.createDirectories(stagingRoot);
        rejectLinks(stagingRoot);
        Path session = Files.createTempDirectory(stagingRoot, prefix);
        Files.writeString(session.resolve(MARKER), "IDEARM DOS build session\n", StandardCharsets.US_ASCII);
        Files.createDirectory(session.resolve("C"));
        Files.createDirectory(session.resolve("S"));
        Files.createDirectory(session.resolve("T"));
        Files.createDirectories(session.resolve("C").resolve("LOG"));
        if (session.toRealPath().startsWith(projectRoot.toRealPath())) {
            throw new DomainException("build.unsafe-staging", "Tool staging resolves inside the project.");
        }
        return session;
    }

    static void deleteSession(Path stagingRoot, Path session) throws IOException {
        Path absolute = session.toAbsolutePath().normalize();
        if (!absolute.getParent().equals(stagingRoot) || Files.isSymbolicLink(absolute)
                || !Files.isRegularFile(absolute.resolve(MARKER), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing to delete an unowned DOS session: " + absolute);
        }
        try (var tree = Files.walk(absolute)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /**
     * Removes sessions abandoned by an earlier run that died before releasing them. Failures are ignored, because
     * a sweep must never stop a build.
     */
    static void sweepStaleSessions(Path stagingRoot, Duration maxAge) {
        if (!Files.isDirectory(stagingRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Instant deadline = Instant.now().minus(maxAge);
        try (var entries = Files.list(stagingRoot)) {
            for (Path candidate : entries.toList()) {
                try {
                    if (!Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)
                            || !Files.isRegularFile(candidate.resolve(MARKER), LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    BasicFileAttributes attributes =
                            Files.readAttributes(candidate, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attributes.lastModifiedTime().toInstant().isBefore(deadline)) {
                        deleteSession(stagingRoot, candidate);
                    }
                } catch (IOException | RuntimeException ignored) {
                    // A session still owned by another IDE instance stays untouched.
                }
            }
        } catch (IOException ignored) {
            // Nothing to sweep.
        }
    }

    // ---------------------------------------------------------------- staging contents

    static void copySources(Path projectRoot, Path driveS, List<String> logicalSources) throws IOException {
        for (String logical : logicalSources) {
            Path source = projectRoot.resolve(logical.replace('\\', '/')).normalize();
            Path staged = safeOutput(driveS, logical);
            if (!source.startsWith(projectRoot) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                throw new DomainException("build.source-not-found",
                        "Project source is not a regular file: " + logical, logical);
            }
            rejectLinks(source);
            if (!source.toRealPath().startsWith(projectRoot.toRealPath())) {
                throw new DomainException("build.source-outside-project",
                        "Source resolves outside the project: " + logical, logical);
            }
            Files.createDirectories(staged.getParent());
            Files.copy(source, staged);
        }
    }

    /** Include directories travel with the sources so INCLUDE directives resolve inside the emulated drive. */
    static void copyIncludes(Path projectRoot, Path driveS, List<String> includeDirs) throws IOException {
        for (String logical : includeDirs) {
            Path directory = projectRoot.resolve(logical.replace('\\', '/')).normalize();
            if (!directory.startsWith(projectRoot) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new DomainException("build.include-not-found", "Include directory is missing: " + logical, logical);
            }
            rejectLinks(directory);
            Path stagedRoot = safeOutput(driveS, logical);
            Files.createDirectories(stagedRoot);
            try (var tree = Files.walk(directory)) {
                for (Path file : tree.toList()) {
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    rejectLinks(file);
                    String relative = directory.relativize(file).toString().replace('\\', '/');
                    Path staged = safeOutput(stagedRoot, relative);
                    if (Files.exists(staged, LinkOption.NOFOLLOW_LINKS)) {
                        // include = ["src"] is common: those sources are already staged from the same files.
                        continue;
                    }
                    Files.createDirectories(staged.getParent());
                    Files.copy(file, staged);
                }
            }
        }
    }

    /** Copies the DOS-hosted tools and their companions; host-executed tools are left to the caller. */
    static Map<String, String> copyDosTools(ResolvedToolchain toolchain, Path driveT) throws IOException {
        var executables = new HashMap<String, String>();
        var copied = new HashMap<String, Path>();
        for (ToolInstallation tool : toolchain.tools().values()) {
            if (tool.hostKind() != HostKind.DOS_REAL && tool.hostKind() != HostKind.DOS_DPMI) {
                continue;
            }
            String name = tool.executable().getFileName().toString().toUpperCase(Locale.ROOT);
            copyToolFile(tool.executable(), name, driveT, copied);
            executables.put(tool.toolId(), name);
            for (var companion : tool.companions().entrySet()) {
                copyToolFile(companion.getValue(), companion.getKey().toUpperCase(Locale.ROOT), driveT, copied);
            }
        }
        return Map.copyOf(executables);
    }

    private static void copyToolFile(Path file, String name, Path driveT, Map<String, Path> copied)
            throws IOException {
        if (name.contains("/") || name.contains("\\")) {
            throw new DomainException("toolchain.invalid-companion", "Tool files need plain DOS names: " + name, name);
        }
        Path target = safeOutput(driveT, name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new DomainException("toolchain.missing-file", "A registered tool file is missing: " + file, file);
        }
        Path previous = copied.putIfAbsent(name, file);
        if (previous != null) {
            if (Files.mismatch(previous, file) != -1) {
                throw new DomainException("toolchain.companion-conflict",
                        "Two tools require different copies of " + name, name);
            }
            return;
        }
        Files.copy(file, target);
    }

    static void prepareOutputs(BuildPlan plan, Path driveC) throws IOException {
        for (String output : plan.outputs()) {
            Files.createDirectories(safeOutput(driveC, output).getParent());
        }
    }

    static void discardOutputs(BuildPlan plan, Path driveC) throws IOException {
        // TLINK leaves an executable behind after reporting failure; such outputs must never reach publication.
        for (String artifact : plan.outputs()) {
            Files.deleteIfExists(safeOutput(driveC, artifact));
        }
    }

    /**
     * Copies the artifacts out of the emulated drive under the exact names the plan spells.
     *
     * <p>DOS upper-cases every name it creates, but the project receives them as planned, with lower-case
     * extensions; a case-sensitive file system would not even find them under the planned spelling. The copy
     * lives in the session, so {@link #deleteSession} removes it with everything else.
     */
    static Path exportArtifacts(BuildPlan plan, Path session) throws IOException {
        Path export = Files.createDirectory(session.resolve("out"));
        for (String artifact : plan.outputs()) {
            Path staged = safeOutput(session.resolve("C"), artifact);
            Path target = export.resolve(artifact.replace('\\', '/')).normalize();
            if (!target.startsWith(export) || target.equals(export)) {
                throw new DomainException("build.unsafe-path", "A build artifact escaped its export folder.");
            }
            Files.createDirectories(target.getParent());
            Files.copy(staged, target);
        }
        return export;
    }

    // ---------------------------------------------------------------- DOS session

    /**
     * Runs the given DOS steps in one DOSBox session and appends their results.
     *
     * @return the session status, without validating the plan artifacts, which the caller owns.
     */
    static BuildStatus runDosSession(ProcessExecutor processes, Path session, List<Step> dosSteps,
                                     ResolvedToolchain tools, CancellationToken cancellation, Duration timeout,
                                     StringBuilder log, List<ToolResult> results) throws IOException {
        Path driveC = session.resolve("C");
        Map<String, String> executables = copyDosTools(tools, session.resolve("T"));
        writeBatch(dosSteps, driveC, executables, log);
        DosBoxDialect dialect = DosBoxDialect.forId(tools.environment().toolId());
        if (!dialect.readOnlyMounts()) {
            log.append("Warning: this DOSBox build ignores read-only mounts; "
                    + "only disposable tool and source copies are exposed.\n");
        }
        Path configuration = writeConfiguration(session, dialect);
        List<String> command = dialect.command(tools.environment().executable(), configuration);
        log.append("DOSBox: ").append(command).append('\n');
        var request = ProcessRequest.isolated(command, session, timeout).withEnvironment(dialect.buildEnvironment());
        var process = processes.run(request, cancellation);
        if (!process.output().isBlank()) {
            log.append(process.output()).append('\n');
        }
        results.addAll(readSteps(dosSteps, driveC, log));
        if (process.cancelled() || cancellation.cancelled()) {
            return BuildStatus.CANCELLED;
        }
        if (process.timedOut()) {
            return BuildStatus.TIMED_OUT;
        }
        Path sentinel = driveC.resolve("LOG").resolve("DONE.TXT");
        if (process.exitCode() != 0 || !Files.isRegularFile(sentinel, LinkOption.NOFOLLOW_LINKS)
                || !Files.readString(sentinel, StandardCharsets.US_ASCII).strip().equals("DONE")) {
            log.append("Build protocol failed: DOSBox did not finish with a valid completion sentinel.\n");
            return BuildStatus.FAILED;
        }
        return BuildStatus.SUCCEEDED;
    }

    static void writeBatch(List<Step> steps, Path driveC, Map<String, String> executables, StringBuilder log)
            throws IOException {
        var batch = new ArrayList<>(List.of("@echo off", "set PATH=T:\\", "set FAILED=0", "C:", "cd \\"));
        for (Step step : steps) {
            ToolInvocation invocation = step.invocation();
            String name = stepName(step.index());
            String executable = executables.get(invocation.toolId());
            if (executable == null) {
                throw new DomainException("toolchain.missing-tool",
                        "No installation for build step " + invocation.toolId(), invocation.toolId());
            }
            var arguments = new ArrayList<>(invocation.arguments());
            if (invocation.responseFileName() != null) {
                String declared = "@C:\\" + invocation.responseFileName();
                // Files the IDE writes use lower-case extensions; DOS matches names case-insensitively.
                String responseFile = name.toLowerCase(Locale.ROOT) + ".rsp";
                String actual = "@C:\\" + responseFile;
                if (!arguments.contains(declared)) {
                    throw new DomainException("build.invalid-response-file",
                            "The invocation does not reference its declared response file.");
                }
                arguments.replaceAll(argument -> argument.equals(declared) ? actual : argument);
                requireSafeText(invocation.responseFileContents());
                Files.writeString(driveC.resolve(responseFile), invocation.responseFileContents() + "\r\n",
                        StandardCharsets.US_ASCII);
                log.append(actual).append(" = ").append(invocation.responseFileContents()).append('\n');
            }
            String command = "T:\\" + executable + " " + String.join(" ", arguments);
            requireSafeText(command);
            String redirected = command + " > C:\\LOG\\" + name + ".LOG";
            if (redirected.length() > MAX_COMMAND) {
                throw new DomainException("build.command-too-long",
                        "DOS commands longer than " + MAX_COMMAND + " characters require a response file.", MAX_COMMAND);
            }
            log.append(command).append('\n');
            // Linking is pointless once a source failed, but every source is assembled so all errors surface at once.
            if (invocation.phase() == BuildPhase.LINK) {
                batch.add("if not \"%FAILED%\"==\"0\" goto end");
            }
            batch.add(redirected);
            batch.add("set RC=0");
            // An ascending ladder preserves the exact DOS status, including uncommon fatal codes.
            for (int level = 1; level <= 255; level++) {
                batch.add("if errorlevel " + level + " set RC=" + level);
            }
            // DOSBox opens a redirection before evaluating IF, so one must never sit inside a condition (ADR-002).
            batch.add("echo RC=%RC%>C:\\LOG\\" + name + ".RC");
            batch.add("if not \"%RC%\"==\"0\" set FAILED=1");
        }
        batch.addAll(List.of(":end", "echo DONE>C:\\LOG\\DONE.TXT", "exit"));
        Files.writeString(driveC.resolve("build.bat"), String.join("\r\n", batch) + "\r\n", StandardCharsets.US_ASCII);
    }

    /**
     * The build session's configuration. The last {@code [autoexec]} lines lock the drives: after
     * {@code config -securemode} no program can mount a host folder (S7), and the build batch still runs.
     */
    static Path writeConfiguration(Path session, DosBoxDialect dialect) throws IOException {
        var lines = new ArrayList<>(List.of("[sdl]", "fullscreen=false", "[dosbox]", "memsize=16",
                "[cpu]", "core=auto", "cycles=max",
                "[mixer]", "nosound=true",
                "[serial]", "serial1=disabled", "serial2=disabled", "serial3=disabled", "serial4=disabled",
                "[ipx]", "ipx=false"));
        if (dialect == DosBoxDialect.DOSBOX_X) {
            lines.addAll(List.of("[dos]", "lfn=true", "automount=false", "automountall=false",
                    "automount drive directories=false", "dos clipboard device enable=false",
                    "dos clipboard api=false", "[ne2000]", "ne2000=false", "[parallel]", "parallel1=disabled",
                    "parallel2=disabled", "parallel3=disabled", "[printer]", "printer=false"));
        }
        String readOnly = dialect.readOnlyMounts() ? " -ro" : "";
        lines.addAll(List.of("[autoexec]",
                "mount T \"" + session.resolve("T") + "\"" + readOnly,
                "mount S \"" + session.resolve("S") + "\"" + readOnly,
                "mount C \"" + session.resolve("C") + "\"",
                "C:", DosBoxDialect.SECURE_MODE, "build.bat"));
        Path configuration = session.resolve("build.conf");
        Files.writeString(configuration, String.join("\r\n", lines) + "\r\n", StandardCharsets.US_ASCII);
        return configuration;
    }

    static List<ToolResult> readSteps(List<Step> steps, Path driveC, StringBuilder log) throws IOException {
        var results = new ArrayList<ToolResult>();
        for (Step step : steps) {
            ToolResult result = readStep(step, driveC, log);
            if (result == null) {
                break;
            }
            results.add(result);
        }
        return results;
    }

    /** Returns null when the step never ran, for example a link skipped after a failed source. */
    static ToolResult readStep(Step step, Path driveC, StringBuilder log) throws IOException {
        String name = stepName(step.index());
        Path logFile = driveC.resolve("LOG").resolve(name + ".LOG");
        Path exitFile = driveC.resolve("LOG").resolve(name + ".RC");
        if (!Files.exists(logFile, LinkOption.NOFOLLOW_LINKS) && !Files.exists(exitFile, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        String text = Files.isRegularFile(logFile, LinkOption.NOFOLLOW_LINKS) ? Files.readString(logFile, CP437) : "";
        Integer exit = null;
        if (Files.isRegularFile(exitFile, LinkOption.NOFOLLOW_LINKS)) {
            String content = Files.readString(exitFile, StandardCharsets.US_ASCII).strip();
            if (content.matches("RC=\\d{1,3}")) {
                int parsed = Integer.parseInt(content.substring(3));
                if (parsed <= 255) {
                    exit = parsed;
                }
            }
        }
        log.append('[').append(name).append(" exit=").append(exit).append("]\n").append(text).append('\n');
        return new ToolResult(step.invocation(), exit, text);
    }

    /** A build only succeeds when every planned step reported zero and every planned artifact exists. */
    static BuildStatus validateArtifacts(BuildPlan plan, Path driveC, List<ToolResult> steps, StringBuilder log)
            throws IOException {
        if (steps.size() != plan.steps().size()
                || steps.stream().anyMatch(step -> step.exitCode() == null || step.exitCode() != 0)) {
            log.append("Build failed: every required step must report an explicit zero exit code.\n");
            return BuildStatus.FAILED;
        }
        for (String artifact : plan.outputs()) {
            if (!Files.isRegularFile(safeOutput(driveC, artifact), LinkOption.NOFOLLOW_LINKS)) {
                log.append("Build failed: expected output is missing: ").append(artifact).append('\n');
                return BuildStatus.FAILED;
            }
        }
        return BuildStatus.SUCCEEDED;
    }

    // ---------------------------------------------------------------- safety

    static Path safeOutput(Path root, String logical) {
        if (logical == null) {
            throw new DomainException("build.invalid-path", "A DOS path is absent.");
        }
        String normalized = logical.replace('\\', '/');
        for (String part : normalized.split("/", -1)) {
            if (!part.matches("[A-Za-z0-9_][A-Za-z0-9_-]{0,7}(\\.[A-Za-z0-9_]{1,3})?")) {
                throw new DomainException("build.invalid-dos-path",
                        "DOS staging requires safe 8.3 path names: " + logical, logical);
            }
        }
        Path result = root.resolve(normalized.toUpperCase(Locale.ROOT)).normalize();
        if (!result.startsWith(root)) {
            throw new DomainException("build.unsafe-path", "DOS output escaped staging.");
        }
        return result;
    }

    static void requireSafeText(String text) {
        if (text == null || text.chars().anyMatch(c -> c < 32 || c > 126 || "&|<>%\"".indexOf(c) >= 0)) {
            throw new DomainException("build.unsafe-command",
                    "DOS commands and response files must contain safe ASCII arguments.");
        }
    }

    static void rejectLinks(Path file) throws IOException {
        for (Path part = file; part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part)
                    || Files.readAttributes(part, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther()) {
                throw new DomainException("build.linked-path",
                        "DOS staging and source paths cannot pass through links: " + part, part);
            }
        }
    }
}
