package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.FileNameRules;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.language.model.IncludeNode;
import io.github.dinamo541.idearm.language.parser.AssemblyParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Follows the INCLUDE graph of the planned sources, so a build can stage the included files and point the
 * assembler at the folders that hold them.
 *
 * <p>Neither MASM nor TASM looks beside the file that includes: they search the current directory, which is the
 * output drive inside the emulator, and then the folders given with /I or /i. Without this pass an
 * {@code INCLUDE macros.inc} sitting right next to {@code main.asm} fails, because the file is neither copied into
 * the emulated drive nor searched for. Resolution therefore happens here, once, against a single flat search path
 * that the adapters hand to the tool unchanged, so the IDE and the assembler always agree on which file wins.
 *
 * <p>The search path is the folder of every planned source, then every declared include folder, first match wins.
 * Planning never touches the file system, so this lives in the application layer next to {@link SourceSet}.
 */
final class IncludeResolver {

    /** How many files one project may pull in before the walk is treated as runaway. */
    private static final int MAX_FILES = 512;

    private IncludeResolver() {
    }

    /**
     * @param dependencies included files as project-relative paths, sorted so a plan stays reproducible
     * @param searchPath   folders to search, in order; the project root is spelled {@code "."}
     * @param problems     includes that cannot be found, or whose names DOS tools cannot open
     */
    record Resolution(List<String> dependencies, List<String> searchPath, List<Diagnostic> problems) {
        Resolution {
            dependencies = List.copyOf(dependencies);
            searchPath = List.copyOf(searchPath);
            problems = List.copyOf(problems);
        }

        boolean hasErrors() {
            return problems.stream().anyMatch(p -> p.severity() == Severity.ERROR || p.severity() == Severity.FATAL);
        }
    }

    /**
     * Resolves every INCLUDE reachable from the planned sources.
     *
     * @param project  a project whose module patterns {@link SourceSet} has already expanded
     * @param dosNames whether the target runs tools that only open 8.3 names
     */
    static Resolution resolve(Project project, Path projectRoot, boolean dosNames) {
        Path root = projectRoot.toAbsolutePath().normalize();
        var sources = new ArrayList<String>();
        sources.add(project.sources().entry());
        sources.addAll(project.sources().modules());

        List<String> searchPath = searchPath(sources, project.sources().include());
        var parser = new AssemblyParser();
        var dependencies = new TreeSet<String>();
        var problems = new ArrayList<Diagnostic>();
        var planned = new HashSet<String>();
        var visited = new HashSet<String>();
        var queue = new ArrayDeque<String>();

        for (String source : sources) {
            planned.add(fold(source));
            if (visited.add(fold(source))) {
                queue.add(source);
            }
        }

        int seen = 0;
        while (!queue.isEmpty() && seen++ < MAX_FILES) {
            String current = queue.poll();
            for (IncludeNode include : includesOf(parser, root.resolve(current))) {
                if (isAbsolute(include.path())) {
                    // INCLUDE \MASM\INCLUDE\DOS.INC names a file of the tool's installation, never one of the
                    // project: there is nothing to stage, and the assembler finds it or reports it itself.
                    continue;
                }
                String resolved = locate(root, searchPath, include.path());
                if (resolved == null) {
                    problems.add(missing(root, current, include, searchPath));
                    continue;
                }
                if (dosNames) {
                    invalidDosName(root, current, include, resolved).ifPresent(problems::add);
                }
                if (!visited.add(fold(resolved))) {
                    continue;
                }
                queue.add(resolved);
                if (!planned.contains(fold(resolved))) {
                    dependencies.add(resolved);
                }
            }
        }
        if (!queue.isEmpty()) {
            // Said out loud: files past the limit are neither staged nor checked, which would otherwise look like
            // a missing include to the assembler.
            problems.add(problem("build.include.too-many",
                    "The project includes more than " + MAX_FILES + " files; the rest are not staged.",
                    null, List.of(String.valueOf(MAX_FILES)), Severity.WARNING));
        }
        return new Resolution(List.copyOf(dependencies), searchPath, problems);
    }

    /** A path from the root of a drive: {@code \MASM\DOS.INC}, {@code /usr/include/x.inc} or {@code C:\X.INC}. */
    static boolean isAbsolute(String included) {
        String path = included == null ? "" : included.strip();
        return path.startsWith("\\") || path.startsWith("/")
                || (path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':');
    }

    /** The folder of every source, then the declared include folders; a repeat keeps its first spelling. */
    private static List<String> searchPath(List<String> sources, List<String> declared) {
        var folders = new LinkedHashMap<String, String>();
        for (String source : sources) {
            String folder = folderOf(source);
            folders.putIfAbsent(fold(folder), folder);
        }
        for (String declaredFolder : declared) {
            String folder = normalize(declaredFolder);
            folders.putIfAbsent(fold(folder), folder);
        }
        return List.copyOf(folders.values());
    }

    private static List<IncludeNode> includesOf(AssemblyParser parser, Path file) {
        try {
            // DOS sources are usually CP437; this encoding never rejects a byte, and only INCLUDE lines matter.
            return parser.parse(Files.readString(file, StandardCharsets.ISO_8859_1)).includes();
        } catch (IOException | RuntimeException unreadable) {
            // A source that cannot be read or parsed is reported by the workspace check and by the assembler.
            return List.of();
        }
    }

    /** The first folder on the search path that holds the included name, as a project-relative path. */
    private static String locate(Path root, List<String> searchPath, String included) {
        String name = normalize(included);
        if (name.isBlank()) {
            return null;
        }
        for (String folder : searchPath) {
            String candidate = folder.equals(".") ? name : folder + "/" + name;
            Path file = root.resolve(candidate).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            return relative(root, file, candidate);
        }
        return null;
    }

    /**
     * The path as the file system spells it, so the Problems panel and staging name the real file even when the
     * INCLUDE line writes it in another case, which Windows and DOS both accept.
     */
    private static String relative(Path root, Path file, String fallback) {
        try {
            Path real = file.toRealPath();
            Path realRoot = root.toRealPath();
            return real.startsWith(realRoot) ? normalize(realRoot.relativize(real).toString()) : fallback;
        } catch (IOException unresolvable) {
            return fallback;
        }
    }

    private static Diagnostic missing(Path root, String includingFile, IncludeNode include, List<String> searchPath) {
        String searched = String.join(", ", searchPath);
        Location at = new Location(root.resolve(includingFile).toString(), include.line(), include.column());
        Optional<String> elsewhere = findElsewhere(root, include.path(), searchPath);
        if (elsewhere.isPresent()) {
            return problem("build.include.elsewhere",
                    "Cannot find " + include.path() + " in " + searched + "; it is in " + elsewhere.get()
                            + ". Add that folder to the include folders of the project.",
                    at, List.of(include.path(), searched, elsewhere.get()));
        }
        return problem("build.include.missing",
                "Cannot find included file " + include.path() + ". Searched: " + searched + ".",
                at, List.of(include.path(), searched));
    }

    private static Diagnostic problem(String code, String detail, Location at, List<String> arguments) {
        return problem(code, detail, at, arguments, Severity.ERROR);
    }

    private static Diagnostic problem(String code, String detail, Location at, List<String> arguments,
                                      Severity severity) {
        return new Diagnostic(severity, code, detail, at, "idearm", "", arguments);
    }

    /** Where the file actually sits, so the message can name the folder the user has to declare. */
    private static Optional<String> findElsewhere(Path root, String included, List<String> searchPath) {
        String wanted = fold(lastSegment(normalize(included)));
        var searched = new HashSet<String>();
        for (String folder : searchPath) {
            searched.add(fold(folder));
        }
        try (var tree = Files.walk(root, SourceSet.MAX_DEPTH)) {
            return tree.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> folderOf(normalize(root.relativize(path).toString())))
                    .filter(folder -> !searched.contains(fold(folder)) && !isSkipped(folder))
                    .filter(folder -> Files.isRegularFile(
                            root.resolve(folder.equals(".") ? wanted : folder + "/" + wanted),
                            LinkOption.NOFOLLOW_LINKS))
                    .findFirst();
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    private static boolean isSkipped(String folder) {
        for (String segment : folder.split("/")) {
            if (SourceSet.SKIPPED.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    /** DOS tools cannot open a name outside 8.3, and staging would refuse to copy it, so it is said here first. */
    private static Optional<Diagnostic> invalidDosName(Path root, String includingFile, IncludeNode include,
                                                       String resolved) {
        try {
            FileNameRules.validateRelativePath(resolved, true);
            return Optional.empty();
        } catch (DomainException invalid) {
            return Optional.of(new Diagnostic(Severity.ERROR, invalid.code(), invalid.getMessage(),
                    new Location(root.resolve(includingFile).toString(), include.line(), include.column()),
                    "idearm", "", List.of(resolved)));
        }
    }

    private static String folderOf(String relativeFile) {
        String path = normalize(relativeFile);
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "." : path.substring(0, slash);
    }

    private static String lastSegment(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String normalize(String path) {
        return path == null ? "" : path.replace('\\', '/').trim();
    }

    /** Windows and DOS both match names without regard to case, so the graph is walked that way too. */
    private static String fold(String path) {
        return normalize(path).toUpperCase(Locale.ROOT);
    }
}
