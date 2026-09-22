package io.github.dinamo541.idearm.domain.execution;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.model.RunConfiguration;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks the {@code [run]} values that end up inside a generated DOSBox configuration and batch file.
 *
 * <p>Those files are written from {@code idearm.toml}, which may come from anyone: a line break in {@code cycles} or
 * in an argument would add commands of its own. Values are therefore checked against what DOSBox accepts before
 * anything is written, and a DOS program's arguments must fit the 126-character command tail.
 */
public final class RunSettings {

    /** DOSBox 0.74-3 clamps larger values to 63 MB; every dialect accepts 1-63 (spike S7). */
    public static final int MIN_MEMSIZE = 1;
    public static final int MAX_MEMSIZE = 63;
    /** A DOS program receives at most 126 characters after its name. */
    public static final int MAX_DOS_ARGUMENTS = 126;

    private static final Pattern CYCLES = Pattern.compile("(?i)auto|max|max \\d{1,3}%|fixed \\d{1,7}|\\d{1,7}");
    private static final String UNSAFE = "&|<>^%\"";

    private RunSettings() {
    }

    /** The problems that prevent running this configuration; empty when it may run. */
    public static List<Diagnostic> problems(RunConfiguration run, TargetProfile target) {
        var problems = new ArrayList<Diagnostic>();
        if (target == null) {
            return problems;
        }
        if (!target.isDos()) {
            // A native program's arguments reach a batch file (Windows) or GDB's command channel; a line break
            // would start a command of its own there. Emulator settings do not apply.
            for (String argument : run.args()) {
                if (argument.chars().anyMatch(c -> c < 32)) {
                    problems.add(problem("run.argument.unsafe",
                            "Program arguments cannot contain line breaks or control characters: " + printable(argument),
                            printable(argument)));
                }
            }
            return List.copyOf(problems);
        }
        if (!CYCLES.matcher(run.cycles()).matches()) {
            problems.add(problem("run.cycles.invalid",
                    "cycles must be auto, max, max N%, fixed N or a number: " + printable(run.cycles()),
                    printable(run.cycles())));
        }
        if (run.memsize() < MIN_MEMSIZE || run.memsize() > MAX_MEMSIZE) {
            problems.add(problem("run.memsize.invalid",
                    "memsize must be between " + MIN_MEMSIZE + " and " + MAX_MEMSIZE + " MB: " + run.memsize(),
                    run.memsize(), MIN_MEMSIZE, MAX_MEMSIZE));
        }
        for (String argument : run.args()) {
            if (argument.chars().anyMatch(c -> c < 32 || c > 126 || UNSAFE.indexOf(c) >= 0)) {
                problems.add(problem("run.argument.unsafe",
                        "Program arguments cannot contain & | < > ^ % or quotes: " + printable(argument),
                        printable(argument)));
            }
        }
        if (String.join(" ", run.args()).length() > MAX_DOS_ARGUMENTS) {
            problems.add(problem("run.arguments.tooLong",
                    "A DOS program receives at most " + MAX_DOS_ARGUMENTS + " characters of arguments.",
                    MAX_DOS_ARGUMENTS));
        }
        return List.copyOf(problems);
    }

    /** Control characters are shown as \n, \r or \xNN, so the message itself stays on one line. */
    private static String printable(String value) {
        var text = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\n' -> text.append("\\n");
                case '\r' -> text.append("\\r");
                default -> {
                    if (c < 32) text.append(String.format("\\x%02X", (int) c));
                    else text.append(c);
                }
            }
        }
        return text.toString();
    }

    private static Diagnostic problem(String code, String message, Object... arguments) {
        var values = new ArrayList<String>();
        for (Object argument : arguments) {
            values.add(String.valueOf(argument));
        }
        return new Diagnostic(Severity.ERROR, code, message, null, "idearm", "", values);
    }
}
