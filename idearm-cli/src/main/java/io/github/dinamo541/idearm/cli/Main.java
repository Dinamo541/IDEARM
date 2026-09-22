package io.github.dinamo541.idearm.cli;

import io.github.dinamo541.idearm.application.*;
import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.IdearmInfo;
import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.dist.DistResult;
import io.github.dinamo541.idearm.domain.execution.SessionState;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.domain.port.DistPackager;
import io.github.dinamo541.idearm.domain.port.ExecutionEnvironmentProvider;
import io.github.dinamo541.idearm.domain.port.ToolchainProvider;
import io.github.dinamo541.idearm.infrastructure.persistence.TomlProjectRepository;
import io.github.dinamo541.idearm.infrastructure.process.CompositeToolRunner;
import io.github.dinamo541.idearm.infrastructure.process.HostProcessToolRunner;
import io.github.dinamo541.idearm.infrastructure.process.ProcessService;
import io.github.dinamo541.idearm.infrastructure.tools.DefaultToolRegistry;
import io.github.dinamo541.idearm.infrastructure.workspace.FileBuildWorkspace;
import io.github.dinamo541.idearm.toolchain.dos.HybridToolRunner;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * Command-line interface and composition root for headless execution of IDEARM tasks.
 */
public final class Main {

    /** A build that goes past this is assumed to be a tool waiting for input (ADR-002). */
    private static final Duration DEFAULT_BUILD_TIMEOUT = Duration.ofSeconds(60);

    public static void main(String[] args) {
        int exitCode = run(args, System.in, System.out, System.err);
        System.exit(exitCode);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, null, out, err);
    }

    /** @param keyboard what the user types for a program run here, or {@code null} when nobody can type. */
    public static int run(String[] args, java.io.InputStream keyboard, PrintStream out, PrintStream err) {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            printHelp(out);
            return 0;
        }

        if (args[0].equals("--version") || args[0].equals("-v")) {
            out.println(IdearmInfo.NAME + " v" + IdearmInfo.version());
            return 0;
        }

        String command = args[0].toLowerCase(Locale.ROOT);
        return switch (command) {
            case "build" -> handleBuild(Arrays.copyOfRange(args, 1, args.length), out, err);
            case "run" -> handleRun(Arrays.copyOfRange(args, 1, args.length), keyboard, out, err);
            case "dist", "package" -> handleDist(Arrays.copyOfRange(args, 1, args.length), out, err);
            case "clean" -> handleClean(Arrays.copyOfRange(args, 1, args.length), out, err);
            case "import" -> handleImport(Arrays.copyOfRange(args, 1, args.length), out, err);
            case "doctor" -> handleDoctor(out);
            case "tools" -> args.length >= 3 && args[1].equalsIgnoreCase("add")
                    ? handleToolsAdd(Path.of(args[2]), out, err)
                    : handleDoctor(out);
            default -> {
                err.println("Unknown command: " + command);
                printHelp(err);
                yield 2;
            }
        };
    }

    private static int handleBuild(String[] args, PrintStream out, PrintStream err) {
        Path projectPath = Path.of(".");
        String config = "release";
        boolean json = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--json")) {
                json = true;
            } else if (arg.equals("--config") && i + 1 < args.length) {
                config = args[++i];
            } else if (!arg.startsWith("-")) {
                projectPath = Path.of(arg);
            }
        }

        if (!Files.exists(projectPath.resolve("idearm.toml"))) {
            if (json) {
                out.println("{\"status\":\"FAILED\",\"diagnostics\":[{\"severity\":\"ERROR\",\"message\":\"No idearm.toml found at " + escapeJson(projectPath.toString()) + "\"}]}");
            } else {
                err.println("Error: No idearm.toml found at " + projectPath.toAbsolutePath().normalize());
            }
            return 2;
        }

        // Composition root for Build
        var repository = new TomlProjectRepository();
        var workspace = new FileBuildWorkspace();
        var toolRegistry = DefaultToolRegistry.system();
        var processService = new ProcessService();
        Path stagingRoot = resolveStagingDirectory();

        var runner = new CompositeToolRunner(new HostProcessToolRunner(processService, stagingRoot), new HybridToolRunner(processService, stagingRoot));
        List<ToolchainProvider> providers = new ArrayList<>();
        ServiceLoader.load(ToolchainProvider.class).forEach(providers::add);

        var buildProject = new BuildProject(
                repository,
                toolRegistry,
                providers,
                workspace,
                runner,
                buildTimeout()
        );

        final boolean isJson = json;
        BuildResult result = buildProject.execute(projectPath, config,
                io.github.dinamo541.idearm.domain.build.CancellationToken.NONE,
                event -> {
                    if (!isJson && event instanceof BuildEvent.Output line) {
                        out.print(line.text());
                    }
                });

        if (json) {
            out.println(toJson(result));
        } else {
            printHumanResult(result, out, err);
        }

        return result.status() == BuildStatus.SUCCEEDED ? 0 : 1;
    }

    private static int handleRun(String[] args, java.io.InputStream keyboard, PrintStream out, PrintStream err) {
        Path projectPath = Path.of(".");
        String config = "debug";
        boolean json = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--json")) {
                json = true;
            } else if (arg.equals("--config") && i + 1 < args.length) {
                config = args[++i];
            } else if (arg.equals("--release")) {
                config = "release";
            } else if (!arg.startsWith("-")) {
                projectPath = Path.of(arg);
            }
        }

        if (!Files.exists(projectPath.resolve("idearm.toml"))) {
            if (json) {
                out.println("{\"status\":\"FAILED\",\"error\":\"No idearm.toml found at " + escapeJson(projectPath.toString()) + "\"}");
            } else {
                err.println("Error: No idearm.toml found at " + projectPath.toAbsolutePath().normalize());
            }
            return 2;
        }

        var repository = new TomlProjectRepository();
        var workspace = new FileBuildWorkspace();
        var toolRegistry = DefaultToolRegistry.system();
        var processService = new ProcessService();
        Path stagingRoot = resolveStagingDirectory();

        var runner = new CompositeToolRunner(new HostProcessToolRunner(processService, stagingRoot), new HybridToolRunner(processService, stagingRoot));
        List<ToolchainProvider> toolchains = new ArrayList<>();
        ServiceLoader.load(ToolchainProvider.class).forEach(toolchains::add);

        List<ExecutionEnvironmentProvider> envs = new ArrayList<>();
        ServiceLoader.load(ExecutionEnvironmentProvider.class).forEach(envs::add);

        var builder = new BuildProject(repository, toolRegistry, toolchains, workspace, runner, buildTimeout());
        var runProject = new RunProject(repository, toolRegistry, envs, toolchains, workspace, builder, stagingRoot);

        if (!json) {
            out.println("[RUN] Preparing execution in isolated staging environment...");
        }

        final boolean isJson = json;
        RunResult result = runProject.execute(projectPath, config,
                io.github.dinamo541.idearm.domain.build.CancellationToken.NONE,
                event -> {
                    if (!isJson && event instanceof RunEvent.Started started) {
                        out.println("[RUN] Launched " + started.environment() + " session.");
                    }
                    // A program without a console window of its own (Linux) prints here and reads this terminal.
                    if (event instanceof RunEvent.ProgramOutput programOutput) {
                        (isJson ? err : out).print(programOutput.text());
                        (isJson ? err : out).flush();
                    }
                    if (event instanceof RunEvent.SessionAttached attached && keyboard != null) {
                        forwardKeyboard(keyboard, attached.session());
                    }
                });

        if (json) {
            out.printf("{\"status\":\"%s\",\"exitCode\":%d,\"durationMs\":%d}%n",
                    result.state(),
                    result.exitInfo() != null ? result.exitInfo().exitCode() : -1,
                    result.exitInfo() != null ? result.exitInfo().duration().toMillis() : 0);
        } else {
            if (result.state() == SessionState.EXITED) {
                out.printf("[RUN] Program finished with exit code %d after %d ms.%n",
                        result.exitInfo().exitCode(),
                        result.exitInfo().duration().toMillis());
            } else {
                err.println("[RUN] Execution failed: " + result.output());
                for (var d : result.diagnostics()) {
                    err.println("  " + d.message());
                }
            }
        }

        return result.state() == SessionState.EXITED && result.exitInfo() != null && result.exitInfo().exitCode() == 0 ? 0 : 1;
    }

    private static int handleDist(String[] args, PrintStream out, PrintStream err) {
        Path projectPath = Path.of(".");
        boolean json = false;

        for (String arg : args) {
            if (arg.equals("--json")) {
                json = true;
            } else if (!arg.startsWith("-")) {
                projectPath = Path.of(arg);
            }
        }

        if (!Files.exists(projectPath.resolve("idearm.toml"))) {
            if (json) {
                out.println("{\"status\":\"FAILED\",\"error\":\"No idearm.toml found at " + escapeJson(projectPath.toString()) + "\"}");
            } else {
                err.println("Error: No idearm.toml found at " + projectPath.toAbsolutePath().normalize());
            }
            return 2;
        }

        var repository = new TomlProjectRepository();
        var workspace = new FileBuildWorkspace();
        var toolRegistry = DefaultToolRegistry.system();
        var processService = new ProcessService();
        Path stagingRoot = resolveStagingDirectory();

        var runner = new CompositeToolRunner(new HostProcessToolRunner(processService, stagingRoot), new HybridToolRunner(processService, stagingRoot));
        List<ToolchainProvider> toolchains = new ArrayList<>();
        ServiceLoader.load(ToolchainProvider.class).forEach(toolchains::add);

        List<DistPackager> packagers = new ArrayList<>();
        ServiceLoader.load(DistPackager.class).forEach(packagers::add);

        var builder = new BuildProject(repository, toolRegistry, toolchains, workspace, runner, buildTimeout());
        var packageProject = new PackageProject(repository, workspace, packagers, toolchains, builder);

        try {
            DistResult result = packageProject.execute(projectPath);
            if (json) {
                out.printf("{\"status\":\"PACKAGED\",\"distDir\":\"%s\",\"executable\":\"%s\",\"files\":%d}%n",
                        escapeJson(result.distDirectory().toString()),
                        escapeJson(result.mainExecutable().toString()),
                        result.packagedFiles().size());
            } else {
                out.println("[DIST] Distribution packaged successfully!");
                out.println("  Output directory: " + result.distDirectory().toAbsolutePath().normalize());
                out.println("  Executable:       " + result.mainExecutable().getFileName());
                // Native programs start by themselves; only DOS distributions have a launcher script.
                if (result.launcherScript() != null) {
                    out.println("  Launcher:         " + result.launcherScript().getFileName());
                }
                out.println("  Files (" + result.packagedFiles().size() + "):");
                for (Path f : result.packagedFiles()) {
                    out.println("    - " + f.getFileName());
                }
                if (!result.warnings().isEmpty()) {
                    out.println("  Warnings:");
                    for (String w : result.warnings()) {
                        out.println("    ! " + w);
                    }
                }
            }
            return 0;
        } catch (Exception ex) {
            if (json) {
                out.println("{\"status\":\"FAILED\",\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            } else {
                err.println("[DIST] Packaging failed: " + ex.getMessage());
            }
            return 1;
        }
    }

    private static int handleClean(String[] args, PrintStream out, PrintStream err) {
        Path projectPath = Path.of(".");
        boolean json = false;

        for (String arg : args) {
            if (arg.equals("--json")) {
                json = true;
            } else if (!arg.startsWith("-")) {
                projectPath = Path.of(arg);
            }
        }

        var workspace = new FileBuildWorkspace();
        var cleanProject = new CleanProject(workspace);

        try {
            cleanProject.execute(projectPath);
            if (json) {
                out.println("{\"status\":\"CLEANED\",\"project\":\"" + escapeJson(projectPath.toString()) + "\"}");
            } else {
                out.println("Successfully cleaned project at: " + projectPath.toAbsolutePath().normalize());
            }
            return 0;
        } catch (Exception ex) {
            if (json) {
                out.println("{\"status\":\"FAILED\",\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            } else {
                err.println("Clean failed: " + ex.getMessage());
            }
            return 1;
        }
    }

    private static int handleImport(String[] args, PrintStream out, PrintStream err) {
        Path projectPath = Path.of(".");
        String toolchain = null;
        boolean json = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--json")) {
                json = true;
            } else if (arg.equals("--toolchain") && i + 1 < args.length) {
                toolchain = args[++i];
            } else if (!arg.startsWith("-")) {
                projectPath = Path.of(arg);
            }
        }

        var repository = new TomlProjectRepository();
        var importProject = new ImportProject(repository);

        try {
            var project = importProject.execute(new ImportProject.Request(
                    projectPath, null, null, null, toolchain
            ));
            if (json) {
                out.println("{\"status\":\"IMPORTED\",\"project\":\"" + escapeJson(project.info().name())
                        + "\",\"toolchain\":\"" + escapeJson(project.toolchain().id()) + "\"}");
            } else {
                out.println("Successfully imported project: " + project.info().name());
                out.println("  Directory:  " + projectPath.toAbsolutePath().normalize());
                out.println("  Entry:      " + project.sources().entry());
                if (!project.sources().modules().isEmpty()) {
                    out.println("  Modules:    " + project.sources().modules());
                }
                out.println("  Toolchain:  " + project.toolchain().id() + " " + project.toolchain().version());
            }
            return 0;
        } catch (Exception ex) {
            if (json) {
                out.println("{\"status\":\"FAILED\",\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
            } else {
                err.println("Import failed: " + ex.getMessage());
            }
            return 1;
        }
    }

    /** Registers the tools in a folder the user names, such as their own copy of TASM or MASM. */
    private static int handleToolsAdd(Path folder, PrintStream out, PrintStream err) {
        try {
            List<ToolInstallation> added = DefaultToolRegistry.system().registerFolder(folder);
            if (added.isEmpty()) {
                err.println("No known tools were found in " + folder.toAbsolutePath().normalize());
                return 1;
            }
            for (ToolInstallation tool : added) {
                out.printf("Registered %s %s: %s%n", tool.toolId(), tool.version(), tool.executable());
            }
            return 0;
        } catch (DomainException failure) {
            err.println("Error: " + failure.getMessage());
            return 1;
        }
    }

    private static int handleDoctor(PrintStream out) {
        out.println(IdearmInfo.NAME + " Tool Doctor");
        out.println("====================");
        var registry = DefaultToolRegistry.system();
        Map<String, ToolInstallation> tools = registry.all();

        if (tools.isEmpty()) {
            out.println("No external tools detected.");
            out.println("Register your own tools with: idearm tools add <folder>");
            return 1;
        }

        for (var entry : new TreeMap<>(tools).entrySet()) {
            ToolInstallation tool = entry.getValue();
            out.printf("[%s] %s %s (%s)%n",
                    entry.getKey().toUpperCase(Locale.ROOT),
                    tool.toolId(),
                    "unknown".equals(tool.version()) ? "(version unknown)" : "v" + tool.version(),
                    tool.hostKind());
            out.println("  Path: " + tool.executable());
            if (!tool.companions().isEmpty()) {
                out.println("  Companions: " + tool.companions().keySet());
            }
            if (tool.sha256() != null) {
                out.println("  SHA-256: " + tool.sha256());
            }
        }
        return 0;
    }

    /** Lines typed in this terminal go to the program, until it ends. */
    private static void forwardKeyboard(java.io.InputStream keyboard,
                                        io.github.dinamo541.idearm.domain.execution.ExecutionSession session) {
        Thread.ofPlatform().daemon().name("idearm-cli-keyboard").start(() -> {
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(keyboard,
                    java.nio.charset.Charset.defaultCharset()))) {
                for (String line; !session.exit().isDone() && (line = reader.readLine()) != null; ) {
                    session.sendInput(line + "\n");
                }
            } catch (java.io.IOException closed) {
                // No terminal to read from.
            }
        });
    }

    /** Staging must be outside the project and on an ASCII path, because DOSBox has to mount it. */
    private static Path resolveStagingDirectory() {
        return io.github.dinamo541.idearm.infrastructure.workspace.StagingLocation.resolve();
    }

    /**
     * How long one build may take before its session is killed. A minute is plenty for the projects this version
     * targets, and a slow machine or a large project can raise it without a rebuild.
     */
    private static Duration buildTimeout() {
        String configured = System.getenv("IDEARM_BUILD_TIMEOUT_SECONDS");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_BUILD_TIMEOUT;
        }
        try {
            long seconds = Long.parseLong(configured.strip());
            return seconds > 0 ? Duration.ofSeconds(seconds) : DEFAULT_BUILD_TIMEOUT;
        } catch (NumberFormatException notANumber) {
            return DEFAULT_BUILD_TIMEOUT;
        }
    }

    private static void printHumanResult(BuildResult result, PrintStream out, PrintStream err) {
        out.println();
        if (result.succeeded()) {
            out.println("BUILD SUCCEEDED [" + result.configuration() + "]");
            for (Path artifact : result.artifacts()) {
                out.println("  -> " + artifact);
            }
        } else {
            err.println("BUILD " + result.status() + " [" + result.configuration() + "]");
        }

        if (!result.diagnostics().isEmpty()) {
            out.println();
            out.println("Problems (" + result.diagnostics().size() + "):");
            for (Diagnostic diag : result.diagnostics()) {
                String loc = describe(diag);
                String prefix = switch (diag.severity()) {
                    case ERROR, FATAL -> "[ERROR]";
                    case WARNING -> "[WARN]";
                    case INFO -> "[INFO]";
                };
                PrintStream target = (diag.severity() == Severity.ERROR || diag.severity() == Severity.FATAL) ? err : out;
                target.printf("  %s %s: %s%n", prefix, loc, diag.message());
            }
        }
    }

    /** Editors and humans both read file:line:column, not a record dump. */
    private static String describe(Diagnostic diagnostic) {
        var location = diagnostic.location();
        if (location == null) {
            return diagnostic.tool();
        }
        var text = new StringBuilder(location.path());
        if (location.line() != null) {
            text.append(':').append(location.line());
            if (location.column() != null) {
                text.append(':').append(location.column());
            }
        }
        return text.toString();
    }

    private static String toJson(BuildResult result) {
        var sb = new StringBuilder();
        sb.append("{");
        sb.append("\"status\":\"").append(result.status()).append("\",");
        sb.append("\"configuration\":\"").append(escapeJson(result.configuration())).append("\",");
        sb.append("\"artifacts\":[");
        for (int i = 0; i < result.artifacts().size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(result.artifacts().get(i).toString())).append("\"");
        }
        sb.append("],");
        sb.append("\"diagnostics\":[");
        for (int i = 0; i < result.diagnostics().size(); i++) {
            if (i > 0) sb.append(",");
            Diagnostic d = result.diagnostics().get(i);
            sb.append("{");
            sb.append("\"severity\":\"").append(d.severity()).append("\",");
            sb.append("\"message\":\"").append(escapeJson(d.message())).append("\",");
            sb.append("\"tool\":\"").append(escapeJson(d.tool())).append("\"");
            if (d.location() != null) {
                sb.append(",\"file\":\"").append(escapeJson(d.location().path())).append("\"");
                if (d.location().line() != null) {
                    sb.append(",\"line\":").append(d.location().line());
                }
            }
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void printHelp(PrintStream out) {
        out.println(IdearmInfo.NAME + " - Specialized x86 Assembly IDE (Headless CLI)");
        out.println();
        out.println("Usage:");
        out.println("  idearm build <directory> [--config <name>] [--json]");
        out.println("  idearm run <directory> [--release] [--json]");
        out.println("  idearm dist | package <directory> [--json]");
        out.println("  idearm clean <directory> [--json]");
        out.println("  idearm import <directory> [--toolchain <name>] [--json]");
        out.println("  idearm doctor | tools");
        out.println("  idearm tools add <folder>       register the tools in a folder (TASM, MASM, DOSBox, NASM...)");
        out.println("  idearm --version");
        out.println("  idearm --help");
    }
}
