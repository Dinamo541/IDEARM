package io.github.dinamo541.idearm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.dinamo541.idearm.domain.DomainException;
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

    private static Project project(List<String> modules, List<String> exclude) {
        Project hello = Project.hello("GLOB");
        return new Project(hello.schema(), hello.info(), hello.target(), hello.toolchain(),
                new Sources("src/MAIN.ASM", modules, List.of(), exclude),
                hello.resources(), hello.build(), hello.run(), hello.debug(), hello.dist());
    }
}
