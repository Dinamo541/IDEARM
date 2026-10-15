package io.github.dinamo541.idearm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.Sources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceSetTest {

    @TempDir
    Path projectRoot;

    @BeforeEach
    void createSources() throws IOException {
        Path src = Files.createDirectories(projectRoot.resolve("src"));
        for (String name : List.of("MAIN.ASM", "UTILS.ASM", "VIDEO.ASM", "NOTES.TXT")) {
            Files.writeString(src.resolve(name), "; " + name);
        }
        // A stale copy under build/ must never be assembled a second time.
        Files.createDirectories(projectRoot.resolve("build/release/SRC"));
        Files.writeString(projectRoot.resolve("build/release/SRC/UTILS.ASM"), "; copy");
    }

    @Test
    void expandsAModulePatternIntoSortedFiles() {
        Project expanded = SourceSet.expand(project(List.of("src/*.ASM"), List.of()), projectRoot);

        assertEquals(List.of("src/UTILS.ASM", "src/VIDEO.ASM"), expanded.sources().modules());
    }

    @Test
    void appliesExcludePatterns() {
        Project expanded = SourceSet.expand(project(List.of("src/*.ASM"), List.of("src/VIDEO.ASM")), projectRoot);

        assertEquals(List.of("src/UTILS.ASM"), expanded.sources().modules());
        assertEquals(List.of(), expanded.sources().exclude());
    }

    @Test
    void keepsExplicitModulesUntouched() {
        Project project = project(List.of("src/UTILS.ASM"), List.of());

        assertEquals(project, SourceSet.expand(project, projectRoot));
    }

    @Test
    void rejectsPatternsThatLeaveTheProject() {
        Project project = project(List.of("../*.ASM"), List.of());

        assertThrows(DomainException.class, () -> SourceSet.expand(project, projectRoot));
    }

    @Test
    void leavesOutAMatchedFileThatStartsAProgramOfItsOwn() throws IOException {
        // The Mastermind layout: a sprite test with its own MAIN beside the real program. Assembled into main it
        // failed with "CS unreachable from current segment" in an include the main program uses correctly.
        Files.writeString(projectRoot.resolve("src/MAIN.ASM"), ".CODE\nmain PROC\nmain ENDP\nEND main\n");
        Files.writeString(projectRoot.resolve("src/VIDEO.ASM"), ".CODE\nMAIN PROC FAR\nMAIN ENDP\n\nEND MAIN\n");
        Files.writeString(projectRoot.resolve("src/UTILS.ASM"), ".CODE\nPUBLIC Beep\nBeep PROC\nBeep ENDP\nEND\n");

        SourceSet.Expansion expansion = SourceSet.expansion(project(List.of("src/*.ASM"), List.of()), projectRoot);

        assertEquals(List.of("src/UTILS.ASM"), expansion.project().sources().modules());
        Diagnostic note = expansion.otherPrograms().getFirst();
        assertEquals(1, expansion.otherPrograms().size());
        assertEquals(Severity.INFO, note.severity());
        assertEquals("build.module.other-program", note.code());
        assertEquals(List.of("src/VIDEO.ASM", "src/MAIN.ASM", "END MAIN"), note.arguments());
        assertEquals(projectRoot.resolve("src/VIDEO.ASM").toAbsolutePath().normalize().toString(),
                note.location().path());
        assertEquals(5, note.location().line());
    }

    @Test
    void buildsAFileWithItsOwnStartWhenItIsListedByName() throws IOException {
        Files.writeString(projectRoot.resolve("src/MAIN.ASM"), "END main\n");
        Files.writeString(projectRoot.resolve("src/VIDEO.ASM"), "END start\n");

        SourceSet.Expansion expansion = SourceSet.expansion(
                project(List.of("src/*.ASM", "src/VIDEO.ASM"), List.of()), projectRoot);

        assertEquals(List.of("src/UTILS.ASM", "src/VIDEO.ASM"), expansion.project().sources().modules());
        assertTrue(expansion.otherPrograms().isEmpty());
    }

    @Test
    void keepsTheModuleThatStartsTheProgramWhenTheMainFileDoesNot() throws IOException {
        Files.writeString(projectRoot.resolve("src/MAIN.ASM"), ".CODE\nEND\n");
        Files.writeString(projectRoot.resolve("src/VIDEO.ASM"), ".STARTUP\n.EXIT\nEND\n");

        SourceSet.Expansion expansion = SourceSet.expansion(project(List.of("src/*.ASM"), List.of()), projectRoot);

        assertEquals(List.of("src/UTILS.ASM", "src/VIDEO.ASM"), expansion.project().sources().modules());
        assertTrue(expansion.otherPrograms().isEmpty());
    }

    private static Project project(List<String> modules, List<String> exclude) {
        Project hello = Project.hello("GLOB");
        return new Project(hello.schema(), hello.info(), hello.target(), hello.toolchain(),
                new Sources("src/MAIN.ASM", modules, List.of(), exclude),
                hello.resources(), hello.build(), hello.run(), hello.debug(), hello.dist());
    }
}
