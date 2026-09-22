package io.github.dinamo541.idearm.toolchain.nasm;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.port.DiagnosticParser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads GNU ld output. ld names itself by the path it was started with, so on Windows a line starts with
 * {@code C:/msys64/ucrt64/bin/ld.exe:}. Observed with ld 2.46:
 * <pre>
 *   .../ld.exe: obj/main.obj: in function `main':
 *   C:/project//src/main.asm:6:(.text+0x1): undefined reference to `WriteFileX'      (debug build, DWARF)
 *   .../ld.exe: obj/main.obj:src/main.asm:(.text+0x1): undefined reference to `WriteFileX'   (release build)
 *   .../ld.exe: warning: cannot find entry symbol main; defaulting to 0000000140001000
 *   .../ld.exe: cannot find -lkernel33: No such file or directory
 *   .../ld.exe: i386 architecture of input file `w32.obj' is incompatible with i386:x86-64 output
 * </pre>
 * A missing entry label is only a warning to ld, but the program it writes starts at an arbitrary instruction,
 * so it is reported as an error and the build fails.
 */
public final class GnuLinkerDiagnosticParser implements DiagnosticParser {

    /** "ld:", "ld.exe:" or a path ending in one of them; a drive letter's colon is not the separator. */
    private static final Pattern FROM_LD = Pattern.compile(
            "^(?:[A-Za-z]:)?[^:]*?\\bld(?:\\.bfd)?(?:\\.exe)?:\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WARNING = Pattern.compile("^warning:\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern IN_FUNCTION = Pattern.compile("^.*:\\s*in function .*$", Pattern.CASE_INSENSITIVE);
    /** object:source:(section+offset): message */
    private static final Pattern OBJECT_SOURCE = Pattern.compile("^(?:[A-Za-z]:)?[^:]+:((?:[A-Za-z]:)?[^:()]+):\\(.+?\\):\\s*(.*)$");
    /** object:(section+offset): message */
    private static final Pattern OBJECT_ONLY = Pattern.compile("^((?:[A-Za-z]:)?[^:]+):\\(.+?\\):\\s*(.*)$");
    /** source:line:(section+offset): message, or source:line: message */
    private static final Pattern SOURCE_LINE = Pattern.compile("^((?:[A-Za-z]:)?[^:]+):(\\d+):(?:\\(.+?\\):)?\\s*(.*)$");

    @Override
    public List<Diagnostic> parse(String output, Function<String, String> sourcePaths) {
        if (output == null || output.isBlank()) {
            return List.of();
        }

        var result = new ArrayList<Diagnostic>();
        for (String raw : output.lines().toList()) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }

            Matcher sourceLine = SOURCE_LINE.matcher(line);
            if (sourceLine.matches() && !FROM_LD.matcher(line).matches()) {
                result.add(located(Severity.ERROR, sourceLine.group(3),
                        sourcePaths.apply(sourceName(sourceLine.group(1).strip())), Integer.valueOf(sourceLine.group(2)), raw));
                continue;
            }

            // Older ld versions print object lines without their own name in front.
            Matcher fromLd = FROM_LD.matcher(line);
            boolean fromLinker = fromLd.matches();
            String message = fromLinker ? fromLd.group(1).strip() : line;
            if (IN_FUNCTION.matcher(message).matches()) {
                // Context for the next line, which carries the error itself.
                continue;
            }

            Matcher warning = WARNING.matcher(message);
            if (fromLinker && warning.matches()) {
                String text = warning.group(1).strip();
                Severity severity = text.startsWith("cannot find entry symbol") ? Severity.ERROR : Severity.WARNING;
                result.add(new Diagnostic(severity, "", text, null, "ld", raw));
                continue;
            }

            Matcher objectSource = OBJECT_SOURCE.matcher(message);
            if (objectSource.matches()) {
                result.add(located(Severity.ERROR, objectSource.group(2),
                        sourcePaths.apply(objectSource.group(1).strip()), null, raw));
                continue;
            }
            Matcher objectOnly = OBJECT_ONLY.matcher(message);
            if (objectOnly.matches()) {
                result.add(located(Severity.ERROR, objectOnly.group(2),
                        sourcePaths.apply(objectOnly.group(1).strip()), null, raw));
                continue;
            }
            if (fromLinker) {
                result.add(new Diagnostic(Severity.ERROR, "", message, null, "ld", raw));
            }
        }
        return List.copyOf(result);
    }

    /**
     * With debug information ld joins NASM's compilation folder and the source name as NASM received it:
     * {@code C:/project/src/ + / + src/main.asm}. The name after the join is the project-relative path.
     */
    static String sourceName(String reported) {
        String path = reported.replace('\\', '/');
        int join = path.indexOf("//", 1);
        return join >= 0 ? path.substring(join + 2) : reported;
    }

    private static Diagnostic located(Severity severity, String message, String path, Integer line, String raw) {
        return new Diagnostic(severity, "", message.strip(), new Location(path, line, null), "ld", raw);
    }
}
