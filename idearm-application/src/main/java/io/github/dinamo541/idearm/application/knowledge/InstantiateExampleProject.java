package io.github.dinamo541.idearm.application.knowledge;

import io.github.dinamo541.idearm.application.CreateProject;
import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.port.ProjectRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Use case: Instantiates a runnable IDEARM project from an Academic Assistant code example
 * without touching or overwriting existing student work (AA-P3-07).
 */
public final class InstantiateExampleProject {

    private final ProjectRepository repository;
    private final CreateProject createProject;

    public InstantiateExampleProject(ProjectRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.createProject = new CreateProject(repository);
    }

    public record Request(
            Path parentDirectory,
            String baseProjectName,
            String exampleCode,
            String targetProfile,
            String cpu,
            String toolchainId
    ) {
        public Request {
            Objects.requireNonNull(parentDirectory, "parentDirectory cannot be null");
            Objects.requireNonNull(baseProjectName, "baseProjectName cannot be null");
            Objects.requireNonNull(exampleCode, "exampleCode cannot be null");
        }
    }

    public record Result(
            Project project,
            Path projectDirectory,
            Path mainSourceFile
    ) {}

    public Result execute(Request request) {
        Path parent = request.parentDirectory().toAbsolutePath().normalize();
        if (!Files.exists(parent) || !Files.isDirectory(parent)) {
            throw new DomainException("project.parent.invalid",
                    "Parent directory does not exist or is not a directory: " + parent, parent);
        }

        String safeBaseName = sanitizeProjectName(request.baseProjectName());
        String finalName = resolveUniqueProjectName(parent, safeBaseName);

        String profile = (request.targetProfile() != null && !request.targetProfile().isBlank())
                ? request.targetProfile().trim() : "dos-exe-16";
        String cpu = (request.cpu() != null && !request.cpu().isBlank())
                ? request.cpu().trim() : "8086";
        String toolchain = (request.toolchainId() != null && !request.toolchainId().isBlank())
                ? request.toolchainId().trim() : ("dos-exe-16".equals(profile) ? "borland-tasm" : "nasm");

        // 1. Scaffold project structure with CreateProject
        Project project = createProject.execute(
                parent,
                finalName,
                profile,
                cpu,
                toolchain,
                null,
                null
        );

        Path projectDir = parent.resolve(finalName);
        Path mainAsm = projectDir.resolve("src").resolve("main.asm");

        // 2. Wrap example snippet if needed into a complete runnable assembly source
        String completeSource = formatRunnableSource(finalName, profile, cpu, request.exampleCode());

        try {
            Files.writeString(mainAsm, completeSource, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DomainException("project.create.io_error",
                    "Failed to write example assembly code to: " + mainAsm, e, mainAsm);
        }

        return new Result(project, projectDir, mainAsm);
    }

    private static String sanitizeProjectName(String input) {
        String clean = input.replaceAll("[^a-zA-Z0-9_.-]", "_");
        if (clean.isBlank()) clean = "Example_Project";
        if (clean.startsWith(".")) clean = "Project_" + clean;
        return clean;
    }

    private static String resolveUniqueProjectName(Path parent, String baseName) {
        String name = baseName;
        int counter = 1;
        while (Files.exists(parent.resolve(name))) {
            name = baseName + "_" + counter++;
        }
        return name;
    }

    public static String formatRunnableSource(String projectName, String profile, String cpu, String code) {
        String trimmed = code.trim();
        boolean hasStructure = trimmed.contains(".MODEL")
                || trimmed.contains(".model")
                || trimmed.contains("PROC")
                || trimmed.contains("proc")
                || trimmed.contains("section .text")
                || trimmed.contains("global ");

        if (hasStructure) {
            return trimmed + "\n";
        }

        if ("win-pe64-console".equals(profile) || "linux-elf64".equals(profile)) {
            return """
                    ; ==============================================================================
                    ; Academic Example Project: %s
                    ; Target: %s (%s)
                    ; ==============================================================================

                    default rel
                    global main

                    section .text
                    main:
                        ; --- Example Code ---
                    %s

                        xor eax, eax
                        ret
                    """.formatted(projectName, profile, cpu, indent(trimmed, 4));
        }

        return """
                ; ==============================================================================
                ; Academic Example Project: %s
                ; Target: %s (%s)
                ; ==============================================================================

                .MODEL small
                .STACK 100h

                .DATA

                .CODE
                MAIN PROC
                    mov ax, @data
                    mov ds, ax

                    ; --- Academic Example Code ---
                %s

                    ; Return to DOS cleanly
                    mov ax, 4C00h
                    int 21h
                MAIN ENDP
                END MAIN
                """.formatted(projectName, profile, cpu, indent(trimmed, 4));
    }

    private static String indent(String text, int spaces) {
        String prefix = " ".repeat(spaces);
        return prefix + text.replace("\n", "\n" + prefix);
    }
}
