package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.Sources;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns the source patterns of a project into the concrete files a build plan can use.
 *
 * <p>{@code modules = ["src/*.ASM"]} is how the project format describes a multi-module build, and
 * {@code exclude} takes the same patterns; planning happens without touching the file system, so the expansion
 * belongs here. The result is sorted, which keeps the link order, and therefore the executable, reproducible.
 */
final class SourceSet {

    /** Folders that never hold sources; walking them would be slow and could match generated copies. */
    private static final Set<String> SKIPPED = Set.of("build", "dist", ".idearm", ".git", ".vscode", "target");
    private static final int MAX_DEPTH = 16;

    private SourceSet() {
    }

    /** The project with every module pattern replaced by the files it matches. */
    static Project expand(Project project, Path projectRoot) {
        Sources sources = project.sources();
        boolean hasPatterns = sources.modules().stream().anyMatch(SourceSet::isPattern)
                || !sources.exclude().isEmpty();
        if (!hasPatterns) {
            return project;
        }

        Path root = projectRoot.toAbsolutePath().normalize();
        var modules = new TreeSet<String>();
        for (String module : sources.modules()) {
            if (isPattern(module)) {
                modules.addAll(match(root, module));
            } else {
                modules.add(normalize(module));
            }
        }
        modules.remove(normalize(sources.entry()));

        for (String pattern : sources.exclude()) {
            if (isPattern(pattern)) {
                modules.removeAll(match(root, pattern));
            } else {
                modules.remove(normalize(pattern));
            }
        }

        return new Project(project.schema(), project.info(), project.target(), project.toolchain(),
                new Sources(sources.entry(), List.copyOf(modules), sources.include(), List.of()),
                project.resources(), project.build(), project.run(), project.debug(), project.dist());
    }

    private static boolean isPattern(String value) {
        return value.indexOf('*') >= 0 || value.indexOf('?') >= 0 || value.indexOf('[') >= 0;
    }

    private static List<String> match(Path root, String pattern) {
        String glob = normalize(pattern);
        if (glob.startsWith("/") || glob.contains("..")) {
            throw new DomainException("source.pattern.unsafe",
                    "Source patterns must stay inside the project: " + pattern, pattern);
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        var matches = new ArrayList<String>();
        try (var tree = Files.walk(root, MAX_DEPTH)) {
            tree.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !isSkipped(root, path))
                    .forEach(path -> {
                        String relative = normalize(root.relativize(path).toString());
                        if (matcher.matches(Path.of(relative))) {
                            matches.add(relative);
                        }
                    });
        } catch (IOException unreadable) {
            throw new UncheckedIOException("source.pattern.unreadable", unreadable);
        }
        return matches;
    }

    private static boolean isSkipped(Path root, Path file) {
        Path relative = root.relativize(file);
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            if (SKIPPED.contains(relative.getName(i).toString())) {
                return true;
            }
        }
        return false;
    }

    /** Project files always spell paths with forward slashes, whatever the host separator is. */
    private static String normalize(String path) {
        return path.replace('\\', '/');
    }
}
