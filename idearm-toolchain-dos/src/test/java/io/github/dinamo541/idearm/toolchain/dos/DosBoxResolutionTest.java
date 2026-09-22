package io.github.dinamo541.idearm.toolchain.dos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dinamo541.idearm.domain.model.BuildConfiguration;
import io.github.dinamo541.idearm.domain.model.DebugConfiguration;
import io.github.dinamo541.idearm.domain.model.DistConfiguration;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.ProjectInfo;
import io.github.dinamo541.idearm.domain.model.Resources;
import io.github.dinamo541.idearm.domain.model.RunConfiguration;
import io.github.dinamo541.idearm.domain.model.Sources;
import io.github.dinamo541.idearm.domain.model.TargetSelection;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.domain.model.ToolchainSelection;
import io.github.dinamo541.idearm.domain.port.ToolRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DosBoxResolutionTest {

    @TempDir
    Path tools;

    @Test
    void buildsUseTheClassicDosBoxFirst() throws IOException {
        var registry = registry("dosbox-0.74", "dosbox-x", "dosbox-staging");

        assertEquals("dosbox-0.74", DosBoxResolution.forBuild(registry, project("dosbox")).toolId());
    }

    @Test
    void withoutTheClassicDosBoxBuildsStayInvisibleWithDosBoxX() throws IOException {
        var registry = registry("dosbox-x", "dosbox-staging");

        assertEquals("dosbox-x", DosBoxResolution.forBuild(registry, project("dosbox")).toolId());
    }

    @Test
    void stagingAloneStillBuildsWithItsWindow() throws IOException {
        var registry = registry("dosbox-staging");

        assertEquals("dosbox-staging", DosBoxResolution.forBuild(registry, project("dosbox")).toolId());
    }

    @Test
    void aDialectTheProjectNamesIsHonoured() throws IOException {
        var registry = registry("dosbox-0.74", "dosbox-x", "dosbox-staging");

        assertEquals("dosbox-staging", DosBoxResolution.forBuild(registry, project("dosbox-staging")).toolId());
    }

    @Test
    void aMissingChoiceFallsBackToTheAutomaticOrder() throws IOException {
        var registry = registry("dosbox-0.74");

        assertEquals("dosbox-0.74", DosBoxResolution.forBuild(registry, project("dosbox-x")).toolId());
    }

    @Test
    void theClassicDialectHidesItsWindowOnlyThroughSdl() {
        assertTrue(DosBoxDialect.CLASSIC.invisibleBuild());
        assertEquals("dummy", DosBoxDialect.CLASSIC.buildEnvironment().get("SDL_VIDEODRIVER"));
        assertTrue(DosBoxDialect.DOSBOX_X.buildEnvironment().isEmpty());
        assertTrue(!DosBoxDialect.STAGING.invisibleBuild(), "Staging crashes with the dummy driver (S7)");
    }

    private ToolRegistry registry(String... dialects) throws IOException {
        Map<String, ToolInstallation> installed = new HashMap<>();
        for (String dialect : dialects) {
            Path exe = Files.createFile(tools.resolve(dialect + ".exe"));
            installed.put(dialect, new ToolInstallation(dialect, "1", exe, HostKind.WIN64, Map.of(), null, "test"));
        }
        return id -> Optional.ofNullable(installed.get(id));
    }

    private static Project project(String environment) {
        return new Project(1, new ProjectInfo("APP", "1.0.0"), new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection("borland-tasm", "*"), new Sources("src/main.asm", List.of(), List.of(), List.of()),
                new Resources(List.of()), Map.of("debug", BuildConfiguration.debug()),
                new RunConfiguration(environment, "required", true, "auto", 16, List.of()),
                new DebugConfiguration("external"), new DistConfiguration(true, false));
    }
}
