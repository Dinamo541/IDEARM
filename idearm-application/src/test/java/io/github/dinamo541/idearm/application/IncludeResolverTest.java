package io.github.dinamo541.idearm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.model.BuildConfiguration;
import io.github.dinamo541.idearm.domain.model.DebugConfiguration;
import io.github.dinamo541.idearm.domain.model.DistConfiguration;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.ProjectInfo;
import io.github.dinamo541.idearm.domain.model.Resources;
import io.github.dinamo541.idearm.domain.model.RunConfiguration;
import io.github.dinamo541.idearm.domain.model.Sources;
import io.github.dinamo541.idearm.domain.model.TargetSelection;
import io.github.dinamo541.idearm.domain.model.ToolchainSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IncludeResolverTest {

    @TempDir
    Path project;

    // ---------------------------------------------------------------- resolution

    @Test
    void findsAnIncludeBesideTheSourceThatIncludesIt() throws IOException {
        write("src/main.asm", ".DATA", "    INCLUDE manzana.inc", ".CODE");
        write("src/manzana.inc", "MANZANA DB 'x'");

        var resolution = resolve(sources("src/main.asm"), true);

        assertEquals(List.of("src/manzana.inc"), resolution.dependencies());
        assertEquals(List.of("src"), resolution.searchPath());
        assertEquals(List.of(), resolution.problems());
    }

    @Test
    void findsAnIncludeInADeclaredFolder() throws IOException {
        write("src/main.asm", "    INCLUDE manzana.inc");
        write("sprite/manzana.inc", "MANZANA DB 'x'");

        var resolution = resolve(withInclude(sources("src/main.asm"), "sprite"), true);

        assertEquals(List.of("sprite/manzana.inc"), resolution.dependencies());
        assertEquals(List.of("src", "sprite"), resolution.searchPath());
        assertFalse(resolution.hasErrors());
    }

    @Test
    void searchesTheSourceFolderBeforeADeclaredOne() throws IOException {
        write("src/main.asm", "    INCLUDE manzana.inc");
        write("src/manzana.inc", "NEAR DB 1");
        write("sprite/manzana.inc", "FAR DB 1");

        var resolution = resolve(withInclude(sources("src/main.asm"), "sprite"), true);

        assertEquals(List.of("src/manzana.inc"), resolution.dependencies());
    }

    @Test
    void followsIncludesInsideIncludedFiles() throws IOException {
        write("src/main.asm", "    INCLUDE first.inc");
        write("src/first.inc", "    INCLUDE second.inc");
        write("src/second.inc", "VALUE EQU 1");

        var resolution = resolve(sources("src/main.asm"), true);

        assertEquals(List.of("src/first.inc", "src/second.inc"), resolution.dependencies());
    }

    @Test
    void stopsOnACycleInsteadOfLoopingForever() throws IOException {
        write("src/main.asm", "    INCLUDE a.inc");
        write("src/a.inc", "    INCLUDE b.inc");
        write("src/b.inc", "    INCLUDE a.inc");

        var resolution = resolve(sources("src/main.asm"), true);

        assertEquals(List.of("src/a.inc", "src/b.inc"), resolution.dependencies());
        assertFalse(resolution.hasErrors());
    }

    @Test
    void theProjectRootIsSpelledAsADot() throws IOException {
        write("main.asm", "    INCLUDE manzana.inc");
        write("manzana.inc", "MANZANA DB 1");

        var resolution = resolve(sources("main.asm"), true);

        assertEquals(List.of("."), resolution.searchPath());
        assertEquals(List.of("manzana.inc"), resolution.dependencies());
    }

    @Test
    void anIncludedSourceThatIsAlreadyAModuleIsNotAlsoADependency() throws IOException {
        write("src/main.asm", "    INCLUDE video.asm");
        write("src/video.asm", "VIDEO PROC");

        var resolution = resolve(withModules(sources("src/main.asm"), "src/video.asm"), true);

        assertEquals(List.of(), resolution.dependencies());
        assertFalse(resolution.hasErrors());
    }

    @Test
    void eachSourceFolderJoinsTheSearchPathOnce() throws IOException {
        write("src/main.asm", "NOP");
        write("src/video.asm", "NOP");
        write("audio/sound.asm", "NOP");

        var resolution = resolve(withModules(sources("src/main.asm"), "src/video.asm", "audio/sound.asm"), true);

        assertEquals(List.of("src", "audio"), resolution.searchPath());
    }

    // ---------------------------------------------------------------- problems

    @Test
    void reportsAMissingIncludeAtItsOwnLine() throws IOException {
        write("src/main.asm", ".DATA", "", "    INCLUDE manzana.inc", ".CODE");

        var resolution = resolve(sources("src/main.asm"), true);

        assertTrue(resolution.hasErrors());
        assertEquals(1, resolution.problems().size());
        Diagnostic problem = resolution.problems().getFirst();
        assertEquals("build.include.missing", problem.code());
        assertEquals(3, problem.location().line());
        assertEquals(project.resolve("src/main.asm").toString(), problem.location().path());
        assertEquals("manzana.inc", problem.arguments().getFirst());
    }

    @Test
    void namesTheFolderTheFileIsActuallyIn() throws IOException {
        write("src/main.asm", "    INCLUDE manzana.inc");
        write("sprite/manzana.inc", "MANZANA DB 1");

        var resolution = resolve(sources("src/main.asm"), true);

        Diagnostic problem = resolution.problems().getFirst();
        assertEquals("build.include.elsewhere", problem.code());
        assertEquals(List.of("manzana.inc", "src", "sprite"), problem.arguments());
    }

    @Test
    void ignoresBuildOutputsWhenLookingForTheFileElsewhere() throws IOException {
        write("src/main.asm", "    INCLUDE manzana.inc");
        write("build/debug/manzana.inc", "stale copy");

        var resolution = resolve(sources("src/main.asm"), true);

        assertEquals("build.include.missing", resolution.problems().getFirst().code());
    }

    @Test
    void reportsANameDosToolsCannotOpen() throws IOException {
        write("src/main.asm", "    INCLUDE manzanas-grandes.inc");
        write("src/manzanas-grandes.inc", "MANZANA DB 1");

        var resolution = resolve(sources("src/main.asm"), true);

        assertTrue(resolution.hasErrors());
        assertEquals("path.dos.invalid", resolution.problems().getFirst().code());
        assertEquals(1, resolution.problems().getFirst().location().line());
        // The file is still staged, so the tool reports whatever else is wrong with it.
        assertEquals(List.of("src/manzanas-grandes.inc"), resolution.dependencies());
    }

    @Test
    void aNativeTargetAcceptsALongName() throws IOException {
        write("src/main.asm", "    INCLUDE manzanas-grandes.inc");
        write("src/manzanas-grandes.inc", "MANZANA DB 1");

        var resolution = resolve(sources("src/main.asm"), false);

        assertFalse(resolution.hasErrors());
        assertEquals(List.of("src/manzanas-grandes.inc"), resolution.dependencies());
    }

    @Test
    void anIncludeReachingOutsideTheProjectIsNotResolved() throws IOException {
        write("src/main.asm", "    INCLUDE ../../secrets.inc");
        Files.writeString(project.getParent().resolve("secrets.inc"), "PASSWORD DB 1", StandardCharsets.UTF_8);

        var resolution = resolve(sources("src/main.asm"), true);

        assertTrue(resolution.hasErrors());
        assertEquals(List.of(), resolution.dependencies());
    }

    /** An include from the tool's own folders stopped the build before the assembler, which would have found it. */
    @Test
    void anAbsoluteIncludeIsLeftToTheAssembler() throws IOException {
        write("src/main.asm", "    INCLUDE \\MASM\\INCLUDE\\DOS.INC", "    INCLUDE C:\\TASM\\MACROS.INC");

        var resolution = resolve(sources("src/main.asm"), true);

        assertFalse(resolution.hasErrors(), resolution.problems().toString());
        assertEquals(List.of(), resolution.dependencies());
    }

    // ---------------------------------------------------------------- fixture

    private IncludeResolver.Resolution resolve(Sources sources, boolean dosNames) {
        return IncludeResolver.resolve(project(sources), project, dosNames);
    }

    private void write(String relative, String... lines) throws IOException {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    private static Sources sources(String entry) {
        return new Sources(entry, List.of(), List.of(), List.of());
    }

    private static Sources withInclude(Sources sources, String... folders) {
        return new Sources(sources.entry(), sources.modules(), List.of(folders), sources.exclude());
    }

    private static Sources withModules(Sources sources, String... modules) {
        return new Sources(sources.entry(), List.of(modules), sources.include(), sources.exclude());
    }

    private static Project project(Sources sources) {
        return new Project(1, new ProjectInfo("HELLO", "0.1.0"), new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection("borland-tasm", ">=3.2"), sources, new Resources(List.of()),
                Map.of("debug", BuildConfiguration.debug(), "release", BuildConfiguration.release()),
                RunConfiguration.defaults(), new DebugConfiguration(DebugConfiguration.EMULATOR),
                new DistConfiguration(true, false));
    }
}
