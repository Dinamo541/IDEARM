package io.github.dinamo541.idearm.infrastructure.workspace;

import io.github.dinamo541.idearm.domain.dist.DistResult;
import io.github.dinamo541.idearm.domain.model.DistConfiguration;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.ProjectInfo;
import io.github.dinamo541.idearm.domain.model.TargetProfileCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeDistPackagerTest {

    @TempDir
    Path tempDir;

    private final NativeDistPackager packager = new NativeDistPackager();

    @Test
    void supportsWindowsPeAndLinuxElf() {
        assertTrue(packager.supports(TargetProfileCatalog.WIN_PE64_CONSOLE));
        assertTrue(packager.supports(TargetProfileCatalog.WIN_PE32_CONSOLE));
        assertTrue(packager.supports(TargetProfileCatalog.LINUX_ELF64));
        assertFalse(packager.supports(TargetProfileCatalog.DOS_EXE_16));
    }

    @Test
    void packagesNativeExecutableAndResources() throws IOException {
        Path releaseExe = tempDir.resolve("program.exe");
        Files.writeString(releaseExe, "binary content");

        Path resourceFile = tempDir.resolve("config.json");
        Files.writeString(resourceFile, "{}");

        Path distDir = tempDir.resolve("dist");
        // dist lives in the project, so resources are laid out relative to its parent.

        Project project = Project.hello("demo");
        DistResult result = packager.packageProject(project, releaseExe, distDir, List.of(resourceFile), new DistConfiguration(false, false));

        assertTrue(Files.exists(distDir.resolve("program.exe")));
        assertTrue(Files.exists(distDir.resolve("config.json")));
        assertTrue(Files.exists(distDir.resolve(".idearm-generated")));
        assertEquals(2, result.packagedFiles().size());
    }

    @Test
    void resourcesKeepTheirProjectLayoutAndTheZipStaysInDist() throws IOException {
        Path project = tempDir.resolve("proj");
        Path releaseExe = Files.createDirectories(project.resolve("build/release/bin")).resolve("main.exe");
        Files.writeString(releaseExe, "MZ");
        Files.createDirectories(project.resolve("data/levels"));
        Files.writeString(project.resolve("data/levels/one.txt"), "1");
        Files.writeString(project.resolve("data/levels/two.txt"), "2");
        Files.writeString(project.resolve("font.bin"), "f");

        Path distDir = project.resolve("dist");
        DistResult result = packager.packageProject(Project.hello("demo"), releaseExe, distDir,
                List.of(project.resolve("data"), project.resolve("font.bin")), new DistConfiguration(false, true));

        assertTrue(Files.isRegularFile(distDir.resolve("data/levels/one.txt")));
        assertTrue(Files.isRegularFile(distDir.resolve("data/levels/two.txt")));
        assertTrue(Files.isRegularFile(distDir.resolve("font.bin")));
        Path zip = distDir.resolve("demo-0.1.0.zip");
        assertTrue(Files.isRegularFile(zip), "The zip is created inside dist, where Clean removes it");
        assertFalse(Files.exists(project.resolve("demo-0.1.0.zip")));
        try (var archive = new java.util.zip.ZipFile(zip.toFile())) {
            assertNotNull(archive.getEntry("main.exe"));
            assertNotNull(archive.getEntry("data/levels/one.txt"));
            assertNull(archive.getEntry(".idearm-generated"));
            assertNull(archive.getEntry("demo-0.1.0.zip"));
        }
        assertTrue(result.packagedFiles().contains(zip));
    }
}
