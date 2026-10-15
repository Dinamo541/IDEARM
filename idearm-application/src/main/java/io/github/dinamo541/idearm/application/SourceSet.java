package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.Sources;
import io.github.dinamo541.idearm.domain.model.TargetProfileCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns the source patterns of a project into the concrete files a build plan can use.
 *
 * <p>{@code modules = ["src/*.ASM"]} is how the project format describes a multi-module build, and
 * {@code exclude} takes the same patterns; planning happens without touching the file system, so the expansion
 * belongs here. The result is sorted, which keeps the link order, and therefore the executable, reproducible.
 *
 * <p>A pattern gathers the modules of the main program, never other programs. A folder of DOS sources often holds
 * a test or an older exercise next to the real program; when such a file declares its own start
 * ({@link ProgramStart}) and the main file declares one too, assembling it into the main program can only fail or
 * produce an executable that carries two programs. It is left out and reported as a note instead. A file listed by
 * name is always built, because the user asked for exactly that file.
 */
final class SourceSet {

    /** Folders that never hold sources; walking them would be slow and could match generated copies. */
    static final Set<String> SKIPPED = Set.of("build", "dist", ".idearm", ".git", ".vscode", "target");
    static final int MAX_DEPTH = 16;

    /**
     * @param project       the project with its patterns replaced by files
     * @param otherPrograms one note per file a pattern matched but that was left out as a program of its own
     */
    record Expansion(Project project, List<Diagnostic> otherPrograms) {
        Expansion {
            otherPrograms = List.copyOf(otherPrograms);
        }
    }

    private SourceSet() {
    }

    /** The project with every module pattern replaced by the files it matches. */
    static Project expand(Project project, Path projectRoot) {
        return expansion(project, projectRoot).project();
    }

    /** {@link #expand}, plus the notes that say which matched files were left out and why. */
    static Expansion expansion(Project project, Path projectRoot) {
        Sources sources = project.sources();
        boolean hasPatterns = sources.modules().stream().anyMatch(SourceSet::isPattern)
                || !sources.exclude().isEmpty();
        if (!hasPatterns) {
            return new Expansion(project, List.of());
        }

        Path root = projectRoot.toAbsolutePath().normalize();
        var modules = new TreeSet<String>();
        var named = new HashSet<String>();
        for (String module : sources.modules()) {
            if (isPattern(module)) {
                modules.addAll(match(root, module));
            } else {
                modules.add(normalize(module));
                named.add(normalize(module));
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
        List<Diagnostic> otherPrograms = leaveOutOtherPrograms(project, root, modules, named);

        Project expanded = new Project(project.schema(), project.info(), project.target(), project.toolchain(),
                new Sources(sources.entry(), List.copyOf(modules), sources.include(), List.of()),
                project.resources(), project.build(), project.run(), project.debug(), project.dist());
        return new Expansion(expanded, otherPrograms);
    }

    /** Removes from {@code modules} each matched file that starts a program of its own, with a note for each. */
    private static List<Diagnostic> leaveOutOtherPrograms(Project project, Path root, Set<String> modules,
                                                          Set<String> named) {
        if (!usesEndDirective(project)) {
            return List.of();
        }
        String entry = normalize(project.sources().entry());
        Optional<ProgramStart> mainStart = ProgramStart.in(root.resolve(entry));
        if (mainStart.isEmpty()) {
            // A main file without a start leaves the start to one of the modules, which is a legitimate layout.
            return List.of();
        }
        var notes = new ArrayList<Diagnostic>();
        for (var modulesLeft = modules.iterator(); modulesLeft.hasNext(); ) {
            String module = modulesLeft.next();
            if (named.contains(module)) {
                continue;
            }
            Optional<ProgramStart> start = ProgramStart.in(root.resolve(module));
            if (start.isEmpty()) {
                continue;
            }
            modulesLeft.remove();
            notes.add(problem("build.module.other-program",
                    module + " was not built into " + entry + ": it declares its own start (" + start.get().declaration()
                            + "), so it is a separate program. Make it the main file to build it.",
                    new Location(root.resolve(module).toString(), start.get().line(), start.get().column()),
                    List.of(module, entry, start.get().declaration())));
        }
        return notes;
    }

    /** Leaving a file out is a decision the IDE took, not a fault in the project, hence INFO. */
    private static Diagnostic problem(String code, String detail, Location at, List<String> arguments) {
        return new Diagnostic(Severity.INFO, code, detail, at, "idearm", "", arguments);
    }

    /** Only MASM and TASM, the DOS toolchains, name the start in END; NASM has no such directive. */
    private static boolean usesEndDirective(Project project) {
        try {
            return TargetProfileCatalog.require(project.target().profile()).isDos();
        } catch (DomainException unsupported) {
            // Planning reports an unknown target; leaving the modules alone keeps that the only complaint.
            return false;
        }
    }

    /** A glob already covers whatever it matches, so the entry never has to be listed beside it. */
    static boolean isPattern(String value) {
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
