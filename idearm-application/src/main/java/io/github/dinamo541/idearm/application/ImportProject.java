package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.*;
import io.github.dinamo541.idearm.domain.port.BuildWorkspace;
import io.github.dinamo541.idearm.domain.port.ProjectRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Use case: Inspects an unconfigured directory containing legacy or existing x86 Assembly code,
 * discovers the entry point and secondary modules, infers the toolchain dialect (TASM vs MASM),
 * and scaffolds an {@code idearm.toml} configuration and {@code .gitignore}.
 */
public final class ImportProject {

    private static final Pattern TASM_DIRECTIVES = Pattern.compile("(?i)\\b(IDEAL|LOCALS|QUIRKS|P386|P486|P586)\\b");
    private static final Pattern MASM_DIRECTIVES = Pattern.compile("(?i)\\b(OPTION\\s+(?:SCOPED|CASEMAP|DOTNAME|NOKEYWORD)|INVOKE|PROTO|\\.MSFLOAT)\\b");
    private static final Pattern ENTRY_PROC_PATTERN = Pattern.compile("(?i)\\b(?:main|start|inicio)\\s+proc\\b");
    private static final Pattern STARTUP_PATTERN = Pattern.compile("(?i)\\.startup\\b");
    private static final Set<String> IGNORED_DIRS = Set.of(".git", ".vscode", "build", "dist", ".idearm", "target", "node_modules", ".idea", "herramientas", "tools", "tool", "bin");

    private final ProjectRepository repository;

    public ImportProject(ProjectRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    public record Request(
            Path directory,
            String projectName,
            String targetProfile,
            String cpu,
            String preferredToolchain
    ) {
        public Request {
            Objects.requireNonNull(directory, "directory cannot be null");
        }
    }

    public Project execute(Path directory) {
        return execute(new Request(directory, null, null, null, null));
    }

    public Project execute(Request request) {
        Path root = request.directory().toAbsolutePath().normalize();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new DomainException("project.import.invalid_dir", "Directory does not exist or is not a directory: " + root, root);
        }

        Path tomlPath = root.resolve("idearm.toml");
        if (Files.exists(tomlPath)) {
            throw new DomainException("project.already.configured", "Project is already configured at: " + root, root);
        }

        List<Path> asmFiles = discoverAsmFiles(root);
        if (asmFiles.isEmpty()) {
            throw new DomainException("project.no_sources", "No .ASM source files found in: " + root, root);
        }

        // Determine entry point and module files
        Path entryFile = selectEntryPoint(root, asmFiles);
        String entryRelative = normalizePath(root.relativize(entryFile));

        List<String> moduleRelatives = asmFiles.stream()
                .filter(p -> !p.equals(entryFile))
                .map(p -> normalizePath(root.relativize(p)))
                .sorted()
                .toList();

        // Infer or select toolchain
        String toolchain;
        String toolchainVersion;
        if (request.preferredToolchain() != null && !request.preferredToolchain().isBlank()) {
            toolchain = request.preferredToolchain().trim();
            toolchainVersion = toolchain.equals("microsoft-masm") ? ">=6.11" : ">=3.2";
        } else {
            toolchain = inferToolchain(asmFiles);
            toolchainVersion = toolchain.equals("microsoft-masm") ? ">=6.11" : ">=3.2";
        }

        String name = (request.projectName() != null && !request.projectName().isBlank())
                ? CreateProject.requireValidName(request.projectName())
                : sanitizeName(root.getFileName() != null ? root.getFileName().toString() : "ImportedProject");

        String profile = (request.targetProfile() != null && !request.targetProfile().isBlank())
                ? request.targetProfile().trim()
                : "dos-exe-16";

        String cpu = (request.cpu() != null && !request.cpu().isBlank())
                ? request.cpu().trim()
                : "8086";

        // Generate .gitignore if missing
        createGitignoreIfMissing(root);

        // Mark pre-existing build and dist directories as generated to allow safe cleaning/invalidation
        markGeneratedIfPresent(root);

        Project project = new Project(
                1,
                new ProjectInfo(name, "0.1.0"),
                new TargetSelection(profile, cpu),
                new ToolchainSelection(toolchain, toolchainVersion),
                new Sources(entryRelative, moduleRelatives, List.of(), List.of()),
                new Resources(List.of()),
                Map.of("debug", BuildConfiguration.debug(), "release", BuildConfiguration.release()),
                RunConfiguration.defaults(),
                new DebugConfiguration("external"),
                new DistConfiguration(true, false)
        );

        repository.save(root, project);
        return project;
    }

    private static List<Path> discoverAsmFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        // Skip ignored directories in path
                        for (Path part : root.relativize(p)) {
                            if (IGNORED_DIRS.contains(part.toString().toLowerCase(Locale.ROOT))) {
                                return false;
                            }
                        }
                        return p.getFileName().toString().toUpperCase(Locale.ROOT).endsWith(".ASM");
                    })
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new DomainException("project.import.scan_failed", "Failed to scan directory for assembly sources: " + root, e, root);
        }
    }

    private static Path selectEntryPoint(Path root, List<Path> asmFiles) {
        // 1. Look for main.asm / MAIN.ASM at root or src/
        for (Path file : asmFiles) {
            String name = file.getFileName().toString().toUpperCase(Locale.ROOT);
            if (name.equals("MAIN.ASM")) {
                return file;
            }
        }

        // 2. Look for entry label / PROC / .STARTUP in file content
        for (Path file : asmFiles) {
            try {
                String content = Files.readString(file, StandardCharsets.ISO_8859_1);
                if (STARTUP_PATTERN.matcher(content).find() || ENTRY_PROC_PATTERN.matcher(content).find()) {
                    return file;
                }
            } catch (IOException ignored) {}
        }

        // 3. Fallback: first file alphabetically
        return asmFiles.getFirst();
    }

    private static String inferToolchain(List<Path> asmFiles) {
        int tasmScore = 0;
        int masmScore = 0;

        for (Path file : asmFiles) {
            try {
                String content = Files.readString(file, StandardCharsets.ISO_8859_1);
                if (TASM_DIRECTIVES.matcher(content).find()) {
                    tasmScore += 2;
                }
                if (MASM_DIRECTIVES.matcher(content).find()) {
                    masmScore += 2;
                }
            } catch (IOException ignored) {}
        }

        if (masmScore > tasmScore) {
            return "microsoft-masm";
        }
        return "borland-tasm";
    }

    private static String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String sanitizeName(String name) {
        String cleaned = name.replaceAll("[^a-zA-Z0-9_.-]", "_");
        return cleaned.isBlank() ? "ImportedProject" : cleaned;
    }

    private static void createGitignoreIfMissing(Path root) {
        Path gitignore = root.resolve(".gitignore");
        if (!Files.exists(gitignore)) {
            String content = """
                    # IDEARM generated build outputs
                    build/
                    dist/
                    .idearm/
                    *.obj
                    *.exe
                    *.map
                    *.lst
                    """;
            try {
                Files.writeString(gitignore, content, StandardCharsets.UTF_8);
            } catch (IOException ignored) {}
        }
    }

    private static void markGeneratedIfPresent(Path root) {
        for (String dirName : List.of("build", "dist")) {
            Path dir = root.resolve(dirName);
            if (Files.isDirectory(dir)) {
                Path marker = dir.resolve(BuildWorkspace.GENERATED_MARKER);
                if (!Files.exists(marker)) {
                    try {
                        Files.writeString(marker, BuildWorkspace.GENERATED_MARKER_CONTENT, StandardCharsets.UTF_8);
                    } catch (IOException ignored) {}
                }
            }
        }
    }
}
