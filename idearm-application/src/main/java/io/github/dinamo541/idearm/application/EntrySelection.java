package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.model.Sources;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Works out the sources of a project after the user picks a different main file.
 *
 * <p>The main file is {@code [sources] entry}: the module assembled first, the one that names the executable, and
 * the one the program starts in. Changing it is not only a matter of writing the new path, because {@code modules}
 * has to keep making sense next to it:
 *
 * <ul>
 *   <li>A literal module list that also names the new entry would make the planner see the same file twice and
 *       refuse the project with {@code path.collision}.</li>
 *   <li>A literal module list names every file that is built, so dropping the previous main from it would quietly
 *       stop compiling a file the user never asked to remove; the two are swapped instead.</li>
 *   <li>A pattern such as {@code src/*.asm} already covers both files, and {@link SourceSet} takes the entry back
 *       out when it expands, so it is left exactly as it is.</li>
 * </ul>
 *
 * <p>"Build only this file" is the same setting seen from the other side: an empty {@code modules} means the entry
 * and whatever it INCLUDEs are the whole program, which is what a folder of one-file exercises needs. Linked
 * together instead, each executable carries the code of every other exercise, the program starts in whichever
 * module came first, and the link fails outright as soon as two of them export the same name (verified with
 * TLINK 3.01: {@code Error: MAIN defined in module EJERCIC2.ASM is duplicated in module EJERCIC1.ASM}).
 */
public final class EntrySelection {

    private EntrySelection() {
    }

    /**
     * @param entry        the new main file, as a project-relative path
     * @param onlyThisFile whether the entry alone is the program, rather than one module among several
     */
    public static Sources choose(Sources current, String entry, boolean onlyThisFile) {
        String chosen = normalize(entry);
        if (onlyThisFile) {
            return new Sources(chosen, List.of(), current.include(), current.exclude());
        }
        if (current.modules().isEmpty()) {
            // Leaving "build only this file" behind: every .asm beside the main file joins in, the way a project
            // created by the IDE is set up.
            return new Sources(chosen, List.of(defaultPattern(chosen)), current.include(), current.exclude());
        }

        var modules = new ArrayList<String>();
        boolean patterns = false;
        for (String module : current.modules()) {
            if (SourceSet.isPattern(module)) {
                patterns = true;
                modules.add(module);
            } else if (!fold(module).equals(fold(chosen))) {
                modules.add(module);
            }
        }
        if (!patterns && !fold(current.entry()).equals(fold(chosen)) && !contains(modules, current.entry())) {
            modules.add(current.entry());
            modules.sort(String::compareTo);
        }
        return new Sources(chosen, List.copyOf(modules), current.include(), current.exclude());
    }

    /** Whether the project builds the main file on its own, which is how the IDE shows the choice as a switch. */
    public static boolean buildsOnlyTheEntry(Sources sources) {
        return sources.modules().isEmpty();
    }

    /** The modules a project would go back to, so the user can be told what "build only this file" replaces. */
    private static String defaultPattern(String entry) {
        String folder = folderOf(entry);
        return folder.isEmpty() ? "*.asm" : folder + "/*.asm";
    }

    private static boolean contains(List<String> modules, String path) {
        return modules.stream().anyMatch(module -> fold(module).equals(fold(path)));
    }

    private static String folderOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static String normalize(String path) {
        return path.replace('\\', '/').trim();
    }

    /** Windows and DOS both match names without regard to case, so a duplicate is one there too. */
    private static String fold(String path) {
        return normalize(path).toUpperCase(Locale.ROOT);
    }
}
