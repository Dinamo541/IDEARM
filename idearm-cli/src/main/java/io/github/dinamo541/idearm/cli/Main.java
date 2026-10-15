package io.github.dinamo541.idearm.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.dinamo541.idearm.application.*;
import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.IdearmInfo;
import io.github.dinamo541.idearm.domain.build.BuildStatus;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.dist.DistResult;
import io.github.dinamo541.idearm.domain.execution.SessionState;
import io.github.dinamo541.idearm.domain.model.Project;
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

    private static final ObjectMapper JSON = new ObjectMapper();

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
            case "new" -> handleNew(Arrays.copyOfRange(args, 1, args.length), out, err);
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
            } else if (arg.equals("--debug")) {
                config = "debug";
            } else if (!arg.startsWith("-")) {
                projectPath = Path.of(arg);
            }
        }

        if (!Files.exists(projectPath.resolve("idearm.toml"))) {
            if (json) {
                out.println(jsonBuildError("No idearm.toml found at " + projectPath.toAbsolutePath().normalize()));
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
            } else if (arg.equals("--debug")) {
                config = "debug";
            } else if (!arg.startsWith("-")) {
                projectPath = Path.of(arg);
            }
        }

        if (!Files.exists(projectPath.resolve("idearm.toml"))) {
            if (json) {
                out.println(jsonError("No idearm.toml found at " + projectPath.toAbsolutePath().normalize()));
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
            ObjectNode node = JSON.createObjectNode();
            node.put("status", result.state().name());
            node.put("exitCode", result.exitInfo() != null ? result.exitInfo().exitCode() : -1);
            node.put("durationMs", result.exitInfo() != null ? result.exitInfo().duration().toMillis() : 0);
            out.println(node.toString());
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
                out.println(jsonError("No idearm.toml found at " + projectPath.toAbsolutePath().normalize()));
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
                ObjectNode node = JSON.createObjectNode();
                node.put("status", "PACKAGED");
                node.put("distDir", result.distDirectory().toString());
                node.put("executable", result.mainExecutable().toString());
                node.put("files", result.packagedFiles().size());
                out.println(node.toString());
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
                out.println(jsonError(ex.getMessage() != null ? ex.getMessage() : ex.toString()));
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
                ObjectNode node = JSON.createObjectNode();
                node.put("status", "CLEANED");
                node.put("project", projectPath.toString());
                out.println(node.toString());
            } else {
                out.println("Successfully cleaned project at: " + projectPath.toAbsolutePath().normalize());
            }
            return 0;
        } catch (Exception ex) {
            if (json) {
                out.println(jsonError(ex.getMessage() != null ? ex.getMessage() : ex.toString()));
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
                ObjectNode node = JSON.createObjectNode();
                node.put("status", "IMPORTED");
                node.put("project", project.info().name());
                node.put("toolchain", project.toolchain().id());
                out.println(node.toString());
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
                out.println(jsonError(ex.getMessage() != null ? ex.getMessage() : ex.toString()));
            } else {
                err.println("Import failed: " + ex.getMessage());
            }
            return 1;
        }
    }

    private static int handleNew(String[] args, PrintStream out, PrintStream err) {
        Path folder = null;
        String name = null;
        String target = null;
        String cpu = null;
        String toolchain = null;
        String toolchainVersion = null;
        String debugBackend = null;
        boolean json = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--json")) {
                json = true;
            } else if (arg.equals("--name") && i + 1 < args.length) {
                name = args[++i];
            } else if (arg.equals("--target") && i + 1 < args.length) {
                target = args[++i];
            } else if (arg.equals("--cpu") && i + 1 < args.length) {
                cpu = args[++i];
            } else if (arg.equals("--toolchain") && i + 1 < args.length) {
                toolchain = args[++i];
            } else if (arg.equals("--toolchain-version") && i + 1 < args.length) {
                toolchainVersion = args[++i];
            } else if (arg.equals("--debug") && i + 1 < args.length) {
                debugBackend = args[++i];
            } else if (!arg.startsWith("-") && folder == null) {
                folder = Path.of(arg);
            }
        }

        if (folder == null) {
            if (json) {
                out.println(jsonError("Project folder must be specified"));
            } else {
                err.println("Error: Project folder must be specified. Usage: idearm new <folder> [options]");
            }
            return 2;
        }

        var repository = new TomlProjectRepository();
        var createProject = new CreateProject(repository);

        try {
            Project project = createProject.createInDirectory(
                    folder, name, target, cpu, toolchain, toolchainVersion, debugBackend);
            Path projectRoot = folder.toAbsolutePath().normalize();

            if (json) {
                ObjectNode node = JSON.createObjectNode();
                node.put("status", "CREATED");
                node.put("project", project.info().name());
                node.put("directory", projectRoot.toString());
                node.put("target", project.target().profile());
                node.put("cpu", project.target().cpu());
                node.put("toolchain", project.toolchain().id());
                node.put("toolchainVersion", project.toolchain().version());
                node.put("debugBackend", project.debug().backend());
                out.println(node.toString());
            } else {
                out.println("Successfully created project: " + project.info().name());
                out.println("  Directory:  " + projectRoot);
                out.println("  Target:     " + project.target().profile() + " (" + project.target().cpu() + ")");
                out.println("  Toolchain:  " + project.toolchain().id() + " " + project.toolchain().version());
                out.println("  Debugger:   " + project.debug().backend());
            }
            return 0;
        } catch (DomainException ex) {
            if (json) {
                out.println(jsonError(ex.getMessage()));
            } else {
                err.println("Error: " + ex.getMessage());
            }
            return 1;
        } catch (Exception ex) {
            if (json) {
                out.println(jsonError(ex.getMessage() != null ? ex.getMessage() : ex.toString()));
            } else {
                err.println("Error: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString()));
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
        ObjectNode root = JSON.createObjectNode();
        root.put("status", result.status().name());
        root.put("configuration", result.configuration());

        ArrayNode artifacts = root.putArray("artifacts");
        for (Path artifact : result.artifacts()) {
            artifacts.add(artifact.toString());
        }

        ArrayNode diagnostics = root.putArray("diagnostics");
        for (Diagnostic d : result.diagnostics()) {
            ObjectNode node = diagnostics.addObject();
            node.put("severity", d.severity().name());
            node.put("message", d.message());
            node.put("tool", d.tool());
            if (d.location() != null) {
                node.put("file", d.location().path());
                if (d.location().line() != null) {
                    node.put("line", d.location().line());
                }
            }
        }
        return root.toString();
    }

    private static String jsonError(String error) {
        ObjectNode node = JSON.createObjectNode();
        node.put("status", "FAILED");
        node.put("error", error != null ? error : "");
        return node.toString();
    }

    private static String jsonBuildError(String message) {
        ObjectNode node = JSON.createObjectNode();
        node.put("status", "FAILED");
        ArrayNode diags = node.putArray("diagnostics");
        ObjectNode diag = diags.addObject();
        diag.put("severity", "ERROR");
        diag.put("message", message != null ? message : "");
        return node.toString();
    }

    private static void printHelp(PrintStream out) {
        out.println(IdearmInfo.NAME + " - Specialized x86 Assembly IDE (Headless CLI)");
        out.println();
        out.println("Usage:");
        out.println("  idearm new <folder> [--name <name>] [--target <profile>] [--cpu <cpu>] [--toolchain <toolchain>] [--json]");
        out.println("  idearm build <directory> [--config <name>] [--release] [--json]");
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
