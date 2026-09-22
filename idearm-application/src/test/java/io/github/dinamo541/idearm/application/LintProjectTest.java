package io.github.dinamo541.idearm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.Sources;
import io.github.dinamo541.idearm.domain.model.TargetSelection;
import io.github.dinamo541.idearm.domain.model.ToolchainSelection;
import io.github.dinamo541.idearm.language.index.ProjectSymbolIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LintProjectTest {

    @TempDir
    Path projectRoot;

    private static final String NO_EXIT = """
            .MODEL small
            .STACK 100h
            .CODE
            main PROC
                mov ax, @data
                mov ds, ax
                push 5
            main ENDP
            END main
            """;

    @Test
    void warnsAboutEveryModuleOfADosProject() throws IOException {
        Files.createDirectories(projectRoot.resolve("src"));
        Files.writeString(projectRoot.resolve("src/main.asm"), NO_EXIT);
        Files.writeString(projectRoot.resolve("src/util.asm"), ".MODEL small\n.CODE\nshow PROC\n    shl ax, 4\n    ret\nshow ENDP\nEND\n");
        Project hello = Project.hello("LINT");
        Project project = new Project(hello.schema(), hello.info(), hello.target(), hello.toolchain(),
                new Sources("src/main.asm", List.of("src/*.asm"), List.of(), List.of()),
                hello.resources(), hello.build(), hello.run(), hello.debug(), hello.dist());

        List<Diagnostic> warnings = new LintProject().execute(projectRoot, project, new ProjectSymbolIndex());

        List<String> codes = warnings.stream().map(Diagnostic::code).toList();
        assertTrue(codes.contains("lint.missing-exit"), codes::toString);
        assertTrue(codes.contains("lint.cpu-baseline-push"), codes::toString);
        assertTrue(codes.contains("lint.cpu-baseline-shift"), codes::toString);
        // Paths are project-relative, like build diagnostics, so the Problems panel can open them.
        assertTrue(warnings.stream().allMatch(w -> w.location().path().startsWith("src/")), warnings::toString);
        assertTrue(warnings.stream().allMatch(w -> !w.arguments().isEmpty()));
    }

    @Test
    void aNativeProjectGetsNoDosWarnings() throws IOException {
        Files.createDirectories(projectRoot.resolve("src"));
        Files.writeString(projectRoot.resolve("src/main.asm"), NO_EXIT);
        Project hello = Project.hello("NATIVE");
        Project project = new Project(hello.schema(), hello.info(),
                new TargetSelection("win-pe64-console", "x86-64"), new ToolchainSelection("nasm", ">=2.14"),
                hello.sources(), hello.resources(), hello.build(), hello.run(), hello.debug(), hello.dist());

        assertEquals(List.of(), new LintProject().execute(projectRoot, project, new ProjectSymbolIndex()));
    }

    @Test
    void aMissingSourceIsLeftToTheBuild() {
        assertEquals(List.of(), new LintProject().execute(projectRoot, Project.hello("EMPTY"), null));
    }
}
