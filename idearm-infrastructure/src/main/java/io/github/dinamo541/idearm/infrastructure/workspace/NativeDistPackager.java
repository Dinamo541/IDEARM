package io.github.dinamo541.idearm.infrastructure.workspace;

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
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Packages a standalone distribution for native 32-bit and 64-bit Windows PE or Linux ELF executables: the release
 * program and its resources, laid out as Run lays them out, and optionally a zip of both inside {@code dist}.
 */
public final class NativeDistPackager implements DistPackager {

    private static final String MARKER_NAME = BuildWorkspace.GENERATED_MARKER;
    private static final String MARKER_CONTENT = BuildWorkspace.GENERATED_MARKER_CONTENT;

    @Override
    public boolean supports(TargetProfile profile) {
        if (profile == null) return false;
        return ("Windows".equalsIgnoreCase(profile.platform()) || "Linux".equalsIgnoreCase(profile.platform()))
                && ("PE32+".equalsIgnoreCase(profile.executableFormat()) ||
                    "PE32".equalsIgnoreCase(profile.executableFormat()) ||
                    "ELF64".equalsIgnoreCase(profile.executableFormat()) ||
                    "ELF32".equalsIgnoreCase(profile.executableFormat()));
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

            // 1. Copy release binary
            String execName = FileNames.withLowerCaseExtension(releaseExecutable.getFileName().toString());
            Path targetExe = distDirectory.resolve(execName);
            Files.copy(releaseExecutable, targetExe, StandardCopyOption.REPLACE_EXISTING);
            packagedFiles.add(targetExe);

            // 2. Copy resources where Run puts them: at their project path, next to the program
            packagedFiles.addAll(copyResources(distDirectory.toAbsolutePath().normalize().getParent(),
                    distDirectory, resources));

            // 3. Optional ZIP, inside dist so Clean removes it with the rest
            if (config != null && config.zip()) {
                String zipName = project.info().name().toLowerCase(java.util.Locale.ROOT)
                        + "-" + project.info().version() + ".zip";
                Path zipBundle = distDirectory.resolve(zipName);
                createZip(distDirectory, zipBundle);
                packagedFiles.add(zipBundle);
            }

            return new DistResult(distDirectory, targetExe, null, List.copyOf(packagedFiles), List.copyOf(warnings));
        } catch (IOException e) {
            throw new DomainException("dist.package-failed", "Failed packaging project distribution: " + e.getMessage(), e, e.getMessage());
        }
    }

    private static List<Path> copyResources(Path projectRoot, Path destination, List<Path> resources)
            throws IOException {
        var copied = new ArrayList<Path>();
        for (Path resource : resources) {
            Path absolute = resource.toAbsolutePath().normalize();
            if (projectRoot == null || !absolute.startsWith(projectRoot)) {
                throw new DomainException("dist.resource.traversal",
                        "Resource attempts directory traversal: " + resource, resource);
            }
            Path target = destination.resolve(projectRoot.relativize(absolute).toString());
            List<Path> files;
            if (Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
                try (var tree = Files.walk(absolute)) {
                    files = tree.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList();
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

    private void resetGeneratedDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            return;
        }
        Path marker = directory.resolve(MARKER_NAME);
        if (!Files.exists(marker)) {
            throw new DomainException("workspace.dist.foreign",
                    "Directory " + directory + " was not generated by IDEARM (marker missing). Refusing to overwrite.", directory);
        }
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(directory))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new DomainException("workspace.delete.failed", "Cannot clean " + path, e, path);
                        }
                    });
        }
    }

    private void createZip(Path sourceDir, Path zipFile) throws IOException {
        Files.deleteIfExists(zipFile);
        try (var zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()));
             var stream = Files.walk(sourceDir)) {
            // The zip itself and the IDE's marker file stay out of the archive.
            List<Path> files = stream.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(zipFile) && !path.getFileName().toString().equals(MARKER_NAME))
                    .toList();
            for (Path file : files) {
                String entryName = sourceDir.relativize(file).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(file, zos);
                zos.closeEntry();
            }
        }
    }
}
