package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.files.EntryNames;
import io.github.dinamo541.idearm.domain.model.BuildConfiguration;
import io.github.dinamo541.idearm.domain.model.DebugConfiguration;
import io.github.dinamo541.idearm.domain.model.DistConfiguration;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.ProjectInfo;
import io.github.dinamo541.idearm.domain.model.Resources;
import io.github.dinamo541.idearm.domain.model.RunConfiguration;
import io.github.dinamo541.idearm.domain.model.Sources;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.model.TargetProfileCatalog;
import io.github.dinamo541.idearm.domain.model.TargetSelection;
import io.github.dinamo541.idearm.domain.model.ToolchainSelection;
import io.github.dinamo541.idearm.domain.port.ProjectRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Use case: Scaffolds a new IDEARM project directory structure, starter assembly
 * template, standard {@code .gitignore}, and persists {@code idearm.toml}.
 */
public final class CreateProject {

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    private final ProjectRepository repository;

    public CreateProject(ProjectRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    public record Request(
            Path parentDirectory,
            String projectName,
            String targetProfile,
            String cpu,
            String toolchainId,
            String toolchainVersion
    ) {
        public Request {
            Objects.requireNonNull(parentDirectory, "parentDirectory cannot be null");
            Objects.requireNonNull(projectName, "projectName cannot be null");
        }
    }

    public Project execute(Request request) {
        Objects.requireNonNull(request, "request cannot be null");
        return execute(
                request.parentDirectory(),
                request.projectName(),
                request.targetProfile(),
                request.cpu(),
                request.toolchainId(),
                request.toolchainVersion()
        );
    }

    public Project execute(
            Path parentDirectory,
            String projectName,
            String targetProfile,
            String cpu,
            String toolchainId,
            String toolchainVersion) {

        Objects.requireNonNull(parentDirectory, "parentDirectory cannot be null");
        Objects.requireNonNull(projectName, "projectName cannot be null");

        String name = requireValidName(projectName);

        Path parent = parentDirectory.toAbsolutePath().normalize();
        if (!Files.exists(parent) || !Files.isDirectory(parent)) {
            throw new DomainException("project.parent.invalid",
                    "Parent directory does not exist or is not a directory: " + parent, parent);
        }

        Path projectRoot = parent.resolve(name).normalize();
        if (Files.exists(projectRoot.resolve("idearm.toml"))) {
            throw new DomainException("project.already.exists",
                    "Project already exists at: " + projectRoot, projectRoot);
        }

        String profile = (targetProfile == null || targetProfile.isBlank()) ? "dos-exe-16" : targetProfile.trim();
        String targetCpu = (cpu == null || cpu.isBlank()) ? "8086" : cpu.trim();
        String toolchain = (toolchainId == null || toolchainId.isBlank()) ? "borland-tasm" : toolchainId.trim();
        String version = (toolchainVersion == null || toolchainVersion.isBlank())
                ? defaultVersion(toolchain) : toolchainVersion.trim();
        TargetProfile target = TargetProfileCatalog.require(profile);

        try {
            Path srcDir = projectRoot.resolve("src");
            Files.createDirectories(srcDir);

            // The starter source; names the IDE writes never have an upper-case extension.
            Path mainAsm = srcDir.resolve("main.asm");
            if (!Files.exists(mainAsm)) {
                String starterCode = generateStarterAssembly(name, profile, targetCpu);
                Files.writeString(mainAsm, starterCode, StandardCharsets.UTF_8);
            }

            // Write .gitignore if not present
            Path gitignore = projectRoot.resolve(".gitignore");
            if (!Files.exists(gitignore)) {
                String gitignoreContent = """
                        # IDEARM generated build outputs
                        build/
                        dist/
                        .idearm/
                        *.obj
                        *.exe
                        *.map
                        *.lst
                        """;
                Files.writeString(gitignore, gitignoreContent, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new DomainException("project.create.io_error",
                    "Failed to create project files at: " + projectRoot, e, projectRoot);
        }

        Project project = new Project(
                1,
                new ProjectInfo(name, "0.1.0"),
                new TargetSelection(profile, targetCpu),
                new ToolchainSelection(toolchain, version),
                // Every .asm file added to src/ joins the build, the way an IDE source folder works.
                new Sources("src/main.asm", List.of("src/*.asm"), List.of(), List.of()),
                new Resources(List.of()),
                Map.of("debug", BuildConfiguration.debug(), "release", BuildConfiguration.release()),
                // A DOS program runs in DOSBox and debugs in TD/CodeView; a native one runs on the host under GDB.
                target.isDos() ? RunConfiguration.defaults()
                        : new RunConfiguration("host", "native", true, "auto", 16, List.of()),
                new DebugConfiguration(target.isDos() ? "external" : "gdb"),
                new DistConfiguration(true, false)
        );

        repository.save(projectRoot, project);
        return project;
    }

    /**
     * A project name becomes a folder. Besides the safe characters, it must be a folder name of its own: ".." or "."
     * would put the project in the parent folder, and Windows refuses names such as "CON" or ones ending in a dot.
     */
    static String requireValidName(String projectName) {
        String name = projectName.trim();
        if (name.isEmpty() || !VALID_NAME_PATTERN.matcher(name).matches()
                || EntryNames.check(name, EntryNames.Kind.FOLDER, false).blocking()) {
            throw new DomainException("project.name.invalid",
                    "Project name contains invalid characters: " + name, name);
        }
        return name;
    }

    private static String defaultVersion(String toolchain) {
        return switch (toolchain) {
            case "microsoft-masm" -> ">=6.11";
            case "nasm" -> ">=2.14";
            default -> ">=3.2";
        };
    }

    /** A first program that prints a greeting and ends cleanly, in the calling convention of its target. */
    private static String generateStarterAssembly(String name, String profile, String cpu) {
        if ("win-pe64-console".equals(profile)) {
            return """
                    ; ==============================================================================
                    ; Project: %s
                    ; Target:  %s (%s)
                    ; Generated by IDEARM
                    ; ==============================================================================

                    default rel

                    global main
                    extern GetStdHandle
                    extern WriteFile
                    extern ExitProcess

                    section .data
                        message     db "Hello from 64-bit Assembly!", 13, 10
                        message_len equ $ - message

                    section .bss
                        written     resd 1

                    section .text
                    main:
                        sub rsp, 40                 ; shadow space for the callee, keeps the stack aligned
                        mov ecx, -11                ; STD_OUTPUT_HANDLE
                        call GetStdHandle
                        mov rcx, rax                ; handle
                        lea rdx, [message]          ; text
                        mov r8d, message_len        ; length
                        lea r9, [written]           ; receives the number of bytes written
                        mov qword [rsp + 32], 0     ; fifth argument: no overlapped structure
                        call WriteFile
                        xor ecx, ecx                ; exit code 0
                        call ExitProcess
                    """.formatted(name, profile, cpu);
        } else if ("linux-elf64".equals(profile)) {
            return """
                    ; ==============================================================================
                    ; Project: %s
                    ; Target:  %s (%s)
                    ; Generated by IDEARM
                    ; ==============================================================================

                    default rel

                    global main

                    section .data
                        message     db "Hello from 64-bit Linux Assembly!", 10
                        message_len equ $ - message

                    section .text
                    main:
                        mov eax, 1                  ; sys_write
                        mov edi, 1                  ; stdout
                        lea rsi, [message]
                        mov edx, message_len
                        syscall
                        mov eax, 60                 ; sys_exit
                        xor edi, edi                ; status 0
                        syscall
                    """.formatted(name, profile, cpu);
        } else if ("win-pe32-console".equals(profile)) {
            return """
                    ; ==============================================================================
                    ; Project: %s
                    ; Target:  %s (%s)
                    ; Generated by IDEARM
                    ; ==============================================================================

                    global _main
                    extern _GetStdHandle@4
                    extern _WriteFile@20
                    extern _ExitProcess@4

                    section .data
                        message     db "Hello from 32-bit Assembly!", 13, 10
                        message_len equ $ - message

                    section .bss
                        written     resd 1

                    section .text
                    _main:
                        push -11                    ; STD_OUTPUT_HANDLE
                        call _GetStdHandle@4
                        push 0                      ; no overlapped structure
                        push written                ; receives the number of bytes written
                        push message_len            ; length
                        push message                ; text
                        push eax                    ; handle
                        call _WriteFile@20
                        push 0                      ; exit code 0
                        call _ExitProcess@4
                    """.formatted(name, profile, cpu);
        }

        return """
                ; ==============================================================================
                ; Project: %s
                ; Target:  %s (%s)
                ; Generated by IDEARM
                ; ==============================================================================

                .MODEL small
                .STACK 100h

                .DATA
                    hello_msg DB 'Hello, %s!', 13, 10, '$'

                .CODE
                MAIN PROC
                    mov ax, @data
                    mov ds, ax

                    lea dx, hello_msg
                    mov ah, 09h
                    int 21h

                    ; Terminate program cleanly to DOS
                    mov ax, 4C00h
                    int 21h
                MAIN ENDP
                END MAIN
                """.formatted(name, profile, cpu, name);
    }
}
