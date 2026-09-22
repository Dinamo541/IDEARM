package io.github.dinamo541.idearm.toolchain.dos.dist;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.dist.DistResult;
import io.github.dinamo541.idearm.domain.files.FileNames;
import io.github.dinamo541.idearm.domain.model.DistConfiguration;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.port.BuildWorkspace;
import io.github.dinamo541.idearm.domain.port.DistPackager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Packages a standalone distribution of a DOS MZ or COM project into dist/.
 * Produces an isolated runnable bundle containing the program executable, runtime
 * resources, a relative dosbox.conf, and a smart Windows launcher script (run.bat).
 */
public final class DosDistPackager implements DistPackager {

    private static final String MARKER_NAME = BuildWorkspace.GENERATED_MARKER;
    private static final String MARKER_CONTENT = BuildWorkspace.GENERATED_MARKER_CONTENT;

    @Override
    public boolean supports(TargetProfile profile) {
        return "DOS".equalsIgnoreCase(profile.platform())
                && ("MZ".equalsIgnoreCase(profile.executableFormat()) || "COM".equalsIgnoreCase(profile.executableFormat()));
    }

    @Override
    public DistResult packageProject(Project project,
                                     Path releaseExecutable,
                                     Path distDirectory,
                                     List<Path> resources,
                                     DistConfiguration config) {
        try {
            resetGeneratedDirectory(distDirectory);
            Path marker = distDirectory.resolve(MARKER_NAME);
            Files.writeString(marker, MARKER_CONTENT, StandardCharsets.UTF_8);

            List<Path> packagedFiles = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            // 1. Copy release executable
            Path mainExe = distDirectory.resolve(
                    FileNames.withLowerCaseExtension(releaseExecutable.getFileName().toString()));
            Files.copy(releaseExecutable, mainExe, StandardCopyOption.REPLACE_EXISTING);
            packagedFiles.add(mainExe);

            // 2. Copy runtime resources where Run puts them: at their project path, next to the program
            Path projectRoot = distDirectory.toAbsolutePath().normalize().getParent();
            for (Path copied : copyResources(projectRoot, distDirectory, resources)) {
                packagedFiles.add(copied);
                for (Path part : distDirectory.relativize(copied)) {
                    String name = part.toString();
                    if (!isDos83(name)) {
                        warnings.add("Resource '" + name + "' exceeds DOS 8.3 naming and requires an LFN-enabled emulator.");
                    }
                }
            }

            // 3. Generate dosbox.conf
            Path confFile = distDirectory.resolve("dosbox.conf");
            String confContent = buildDosBoxConf(mainExe.getFileName().toString());
            Files.writeString(confFile, confContent, StandardCharsets.UTF_8);
            packagedFiles.add(confFile);

            // 4. Generate run.bat
            Path launcherBat = distDirectory.resolve("run.bat");
            String batContent = buildRunBat(mainExe.getFileName().toString());
            Files.writeString(launcherBat, batContent, StandardCharsets.UTF_8);
            packagedFiles.add(launcherBat);

            // 5. Generate readme.txt
            Path readme = distDirectory.resolve("readme.txt");
            String readmeContent = buildReadme(project, mainExe.getFileName().toString());
            Files.writeString(readme, readmeContent, StandardCharsets.UTF_8);
            packagedFiles.add(readme);

            // 6. Optional ZIP packaging, inside dist so Clean removes it with the rest
            if (config != null && config.zip()) {
                Path zipPath = distDirectory.resolve(
                        project.info().name().toLowerCase(java.util.Locale.ROOT) + "-" + project.info().version() + ".zip");
                createZip(distDirectory, packagedFiles, zipPath);
                packagedFiles.add(zipPath);
            }

            return new DistResult(distDirectory, mainExe, launcherBat, packagedFiles, warnings);
        } catch (IOException ex) {
            throw new DomainException("dist.packaging.failed", "Failed to package project: " + ex.getMessage(), ex, ex.getMessage());
        }
    }

    /**
     * Empties a dist folder the IDE generated before, so files from an older package (a resource that was
     * removed, an old launcher name) never linger. A folder without the marker belongs to the user and is
     * refused instead.
     */
    private static void resetGeneratedDirectory(Path distDirectory) throws IOException {
        if (!Files.exists(distDirectory, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(distDirectory);
            return;
        }
        if (!Files.isDirectory(distDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new DomainException("dist.directory.invalid", "dist is not a folder: " + distDirectory, distDirectory);
        }
        boolean empty;
        try (var entries = Files.list(distDirectory)) {
            empty = entries.findAny().isEmpty();
        }
        if (empty) {
            return;
        }
        if (!Files.isRegularFile(distDirectory.resolve(MARKER_NAME), LinkOption.NOFOLLOW_LINKS)) {
            throw new DomainException("dist.directory.foreign",
                    "dist contains files the IDE did not generate; move them before packaging: " + distDirectory, distDirectory);
        }
        try (var tree = Files.walk(distDirectory)) {
            for (Path path : tree.sorted(java.util.Comparator.reverseOrder()).toList()) {
                if (!path.equals(distDirectory)) {
                    Files.delete(path);
                }
            }
        }
    }

    private boolean isDos83(String name) {
        String[] parts = name.split("\\.", -1);
        if (parts.length > 2) return false;
        if (parts[0].length() > 8 || parts[0].isEmpty()) return false;
        return parts.length != 2 || parts[1].length() <= 3;
    }

    /**
     * The configuration shipped next to the program.
     *
     * <p>{@code lfn=true} lets DOSBox-X open resources whose names are not 8.3, such as inv_bottom.spr; older
     * DOSBox builds do not know the setting, log that it is unknown and run exactly as before.
     */
    private String buildDosBoxConf(String exeName) {
        return """
                [sdl]
                fullscreen=false
                autolock=false

                [dos]
                lfn=true

                [autoexec]
                @echo off
                mount C .
                C:
                %s
                exit
                """.formatted(exeName);
    }

    private String buildRunBat(String exeName) {
        String template = """
                @echo off
                setlocal enabledelayedexpansion

                cd /d "%~dp0"

                :: 1. Search in PATH for dosbox-x or dosbox
                where dosbox-x.exe >nul 2>&1
                if %ERRORLEVEL% equ 0 (
                    dosbox-x.exe -fastlaunch -conf dosbox.conf
                    exit /b %ERRORLEVEL%
                )

                where dosbox.exe >nul 2>&1
                if %ERRORLEVEL% equ 0 (
                    dosbox.exe -conf dosbox.conf
                    exit /b %ERRORLEVEL%
                )

                :: 2. Check IDEARM local AppData tools
                for /d %%D in ("%LOCALAPPDATA%\\IDEARM\\tools\\dosbox-x-*") do (
                    if exist "%%D\\bin\\x64\\Release\\dosbox-x.exe" (
                        "%%D\\bin\\x64\\Release\\dosbox-x.exe" -fastlaunch -conf dosbox.conf
                        exit /b !ERRORLEVEL!
                    )
                    if exist "%%D\\dosbox-x.exe" (
                        "%%D\\dosbox-x.exe" -fastlaunch -conf dosbox.conf
                        exit /b !ERRORLEVEL!
                    )
                )

                :: 3. Check standard installation directories
                if exist "%ProgramFiles%\\DOSBox-X\\dosbox-x.exe" (
                    "%ProgramFiles%\\DOSBox-X\\dosbox-x.exe" -fastlaunch -conf dosbox.conf
                    exit /b %ERRORLEVEL%
                )
                if exist "%ProgramFiles(x86)%\\DOSBox-0.74-3\\dosbox.exe" (
                    "%ProgramFiles(x86)%\\DOSBox-0.74-3\\dosbox.exe" -conf dosbox.conf
                    exit /b %ERRORLEVEL%
                )
                if exist "%ProgramFiles%\\DOSBox-Staging\\dosbox.exe" (
                    "%ProgramFiles%\\DOSBox-Staging\\dosbox.exe" -conf dosbox.conf
                    exit /b %ERRORLEVEL%
                )

                echo.
                echo =======================================================================
                echo ERROR: DOSBox or DOSBox-X was not found on your system!
                echo.
                echo This application is a 16-bit DOS program ({{EXE_NAME}}) and requires an
                echo emulator to run on 64-bit Windows.
                echo.
                echo Please install DOSBox-X (https://dosbox-x.com/) or DOSBox 0.74-3,
                echo or place dosbox.exe into this directory or your system PATH.
                echo =======================================================================
                echo.
                pause
                exit /b 1
                """;
        return template.replace("{{EXE_NAME}}", exeName);
    }

    private String buildReadme(Project project, String exeName) {
        return """
                =======================================================================
                %s version %s
                Target: 16-bit DOS Executable (x86 8086)
                Packaged by IDEARM (IDE Specialized in x86 Assembly)
                =======================================================================

                HOW TO RUN:

                1. On 64-bit Windows (Windows 10 / 11):
                   - Double-click run.bat.
                   - run.bat will automatically detect DOSBox-X, DOSBox-Staging, or
                     DOSBox 0.74 on your system and launch the program.

                2. On Real DOS / FreeDOS / Virtual Machine:
                   - Boot your machine into MS-DOS or FreeDOS.
                   - Copy this folder or %s to your drive.
                   - Run: %s

                3. Manual DOSBox execution:
                   - Open your DOSBox emulator.
                   - Mount this folder as drive C:
                       mount c .
                       c:
                       %s

                =======================================================================
                """.formatted(project.info().name(), project.info().version(), exeName, exeName, exeName);
    }

    /**
     * Copies each resource file, or every file of a resource folder, to its project-relative path under
     * {@code destination}; the same layout Run gives a program. Returns the copied files.
     */
    static List<Path> copyResources(Path projectRoot, Path destination, List<Path> resources) throws IOException {
        var copied = new ArrayList<Path>();
        for (Path resource : resources) {
            Path absolute = resource.toAbsolutePath().normalize();
            if (projectRoot == null || !absolute.startsWith(projectRoot)) {
                throw new DomainException("dist.resource.traversal",
                        "Resource attempts directory traversal: " + resource, resource);
            }
            Path target = destination.resolve(projectRoot.relativize(absolute).toString());
            List<Path> files;
            if (Files.isDirectory(absolute, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                try (var tree = Files.walk(absolute)) {
                    files = tree.filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)).toList();
                }
            } else {
                files = List.of(absolute);
            }
            for (Path file : files) {
                Path copy = file.equals(absolute) ? target : target.resolve(absolute.relativize(file).toString());
                Files.createDirectories(copy.getParent());
                Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
                copied.add(copy);
            }
        }
        return copied;
    }

    private void createZip(Path distDir, List<Path> files, Path zipPath) throws IOException {
        try (var fos = new FileOutputStream(zipPath.toFile());
             var zos = new ZipOutputStream(fos)) {
            for (Path file : files) {
                if (Files.isRegularFile(file) && !file.equals(zipPath)) {
                    String entryName = distDir.relativize(file).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();
                }
            }
        }
    }
}
