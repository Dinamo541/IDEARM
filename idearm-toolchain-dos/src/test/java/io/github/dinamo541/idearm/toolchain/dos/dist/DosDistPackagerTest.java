package io.github.dinamo541.idearm.toolchain.dos.dist;

import io.github.dinamo541.idearm.domain.dist.DistResult;
import io.github.dinamo541.idearm.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class DosDistPackagerTest {

    @TempDir
    Path tempDir;

    private Project createProject(List<String> resources) {
        return new Project(
                1,
                new ProjectInfo("GAME", "0.2.0"),
                new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection("borland-tasm", ">=3.2"),
                new Sources("src/MAIN.ASM", List.of(), List.of(), List.of()),
                new Resources(resources),
                Map.of("release", BuildConfiguration.release()),
                new RunConfiguration("dosbox", "required", true, "auto", 16, List.of()),
                new DebugConfiguration("external"),
                new DistConfiguration(true, false)
        );
    }

    @Test
    void packagesDosProjectCorrectly() throws IOException {
        Path projectRoot = tempDir.resolve("my-game");
        Files.createDirectories(projectRoot.resolve("src"));
        Files.createDirectories(projectRoot.resolve("build/release"));

        Path releaseExe = projectRoot.resolve("build/release/MAIN.EXE");
        Files.writeString(releaseExe, "MZ_HEADER_MOCK");

        Path resource = projectRoot.resolve("LEVEL.DAT");
        Files.writeString(resource, "LEVEL_DATA");

        Project project = createProject(List.of("LEVEL.DAT"));
        DosDistPackager packager = new DosDistPackager();

        TargetProfile profile = new TargetProfile("dos-exe-16", "x86", "8086", 16, "real", "DOS", "MZ", "tiny", "OMF");
        assertTrue(packager.supports(profile));

        Path distDir = projectRoot.resolve("dist");
        DistResult result = packager.packageProject(project, releaseExe, distDir, List.of(resource), new DistConfiguration(true, false));

        assertNotNull(result);
        assertEquals(distDir, result.distDirectory());
        // Names are compared as stored: Windows would find MAIN.EXE under any spelling. The user's resource keeps
        // its name, because the program opens it by that name.
        assertEquals(java.util.Set.of(".idearm-generated", "MAIN.exe", "LEVEL.DAT", "dosbox.conf", "run.bat",
                "readme.txt"), storedNames(distDir));

        String conf = Files.readString(distDir.resolve("dosbox.conf"));
        assertTrue(conf.contains("lfn=true"), "DOSBox-X needs long file names for non 8.3 resources: " + conf);
        assertTrue(conf.contains("mount C ."));
        assertTrue(conf.contains("MAIN.exe"));

        String bat = Files.readString(distDir.resolve("run.bat"));
        assertTrue(bat.contains("dosbox-x.exe"));
        assertTrue(bat.contains("-conf dosbox.conf"));

        String readme = Files.readString(distDir.resolve("readme.txt"));
        assertTrue(readme.contains("GAME version 0.2.0"));
        assertTrue(readme.contains("HOW TO RUN"));

        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void warnsWhenResourceExceedsDos83() throws IOException {
        Path projectRoot = tempDir.resolve("game-lfn");
        Files.createDirectories(projectRoot.resolve("build/release"));
        Path releaseExe = projectRoot.resolve("build/release/MAIN.EXE");
        Files.writeString(releaseExe, "MZ_HEADER_MOCK");

        Path lfnResource = projectRoot.resolve("inv_bottom.spr");
        Files.writeString(lfnResource, "SPRITE");

        Project project = createProject(List.of("inv_bottom.spr"));
        DosDistPackager packager = new DosDistPackager();

        Path distDir = projectRoot.resolve("dist");
        DistResult result = packager.packageProject(project, releaseExe, distDir, List.of(lfnResource), new DistConfiguration(true, false));

        assertFalse(result.warnings().isEmpty());
        assertTrue(result.warnings().getFirst().contains("inv_bottom.spr"));
        assertTrue(result.warnings().getFirst().contains("8.3"));
    }

    @Test
    void createsZipArchiveWhenRequested() throws IOException {
        Path projectRoot = tempDir.resolve("game-zip");
        Files.createDirectories(projectRoot.resolve("build/release"));
        Path releaseExe = projectRoot.resolve("build/release/MAIN.EXE");
        Files.writeString(releaseExe, "MZ_HEADER_MOCK");

        Project project = createProject(List.of());
        DosDistPackager packager = new DosDistPackager();

        Path distDir = projectRoot.resolve("dist");
        DistResult result = packager.packageProject(project, releaseExe, distDir, List.of(), new DistConfiguration(true, true));

        Path expectedZip = distDir.resolve("game-0.2.0.zip");
        assertTrue(Files.isRegularFile(expectedZip));

        try (var zip = new ZipFile(expectedZip.toFile())) {
            assertNotNull(zip.getEntry("MAIN.exe"));
            assertNotNull(zip.getEntry("run.bat"));
            assertNotNull(zip.getEntry("dosbox.conf"));
            assertNotNull(zip.getEntry("readme.txt"));
        }
    }

    @Test
    void repackagingReplacesAnOlderPackage() throws IOException {
        Path projectRoot = tempDir.resolve("game-again");
        Files.createDirectories(projectRoot.resolve("build/release/bin"));
        Path releaseExe = Files.writeString(projectRoot.resolve("build/release/bin/game.exe"), "MZ");
        Path distDir = Files.createDirectories(projectRoot.resolve("dist"));
        // What an older IDEARM left behind: upper-case names and a resource the project no longer declares.
        Files.writeString(distDir.resolve(".idearm-generated"), "IDEARM generated directory\nschema=1\n");
        Files.writeString(distDir.resolve("DOSBOX.CONF"), "old");
        Files.writeString(distDir.resolve("OLD.DAT"), "old");

        new DosDistPackager().packageProject(createProject(List.of()), releaseExe, distDir, List.of(),
                new DistConfiguration(true, false));

        assertEquals(java.util.Set.of(".idearm-generated", "game.exe", "dosbox.conf", "run.bat", "readme.txt"),
                storedNames(distDir));
    }

    @Test
    void refusesToOverwriteADistFolderTheUserOwns() throws IOException {
        Path projectRoot = tempDir.resolve("game-user");
        Files.createDirectories(projectRoot.resolve("build/release"));
        Path releaseExe = Files.writeString(projectRoot.resolve("build/release/main.exe"), "MZ");
        Path distDir = Files.createDirectories(projectRoot.resolve("dist"));
        Files.writeString(distDir.resolve("notes.txt"), "mine");

        assertThrows(io.github.dinamo541.idearm.domain.DomainException.class, () -> new DosDistPackager()
                .packageProject(createProject(List.of()), releaseExe, distDir, List.of(),
                        new DistConfiguration(true, false)));
        assertEquals("mine", Files.readString(distDir.resolve("notes.txt")));
    }

    private static java.util.Set<String> storedNames(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    @Test
    void resourceFoldersKeepTheLayoutTheProgramOpens() throws IOException {
        Path projectRoot = tempDir.resolve("game-tree");
        Path releaseExe = Files.createDirectories(projectRoot.resolve("build/release/bin")).resolve("game.exe");
        Files.writeString(releaseExe, "MZ");
        Files.createDirectories(projectRoot.resolve("sprites"));
        Files.writeString(projectRoot.resolve("sprites/hero.spr"), "S");
        Files.writeString(projectRoot.resolve("sprites/player_right.spr"), "S");

        Path distDir = projectRoot.resolve("dist");
        DistResult result = new DosDistPackager().packageProject(createProject(List.of("sprites")), releaseExe,
                distDir, List.of(projectRoot.resolve("sprites")), new DistConfiguration(true, false));

        assertTrue(Files.isRegularFile(distDir.resolve("sprites/hero.spr")));
        assertTrue(Files.isRegularFile(distDir.resolve("sprites/player_right.spr")));
        assertEquals(1, result.warnings().size(), result.warnings()::toString);
        assertTrue(result.warnings().getFirst().contains("player_right.spr"));
    }
}
