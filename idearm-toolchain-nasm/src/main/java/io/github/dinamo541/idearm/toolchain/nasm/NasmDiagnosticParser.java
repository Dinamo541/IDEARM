package io.github.dinamo541.idearm.toolchain.nasm;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.port.DiagnosticParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses compiler output from the Netwide Assembler (NASM).
 * Typical formats:
 *   file.asm:12: error: expression syntax error
 *   file.asm:24: warning: user warning [-w+user]
 *   file.asm: fatal: unable to open input file
 */
public final class NasmDiagnosticParser implements DiagnosticParser {

    private static final Pattern WITH_LINE = Pattern.compile("^(.+?):(\\d+):\\s*(error|fatal|warning):\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WITHOUT_LINE = Pattern.compile("^(.+?):\\s*(error|fatal|warning):\\s*(.*)$", Pattern.CASE_INSENSITIVE);

    @Override
    public List<Diagnostic> parse(String output, Function<String, String> sourcePaths) {
        if (output == null || output.isBlank()) {
            return List.of();
        }

        var result = new ArrayList<Diagnostic>();
        for (String raw : output.lines().toList()) {
            String line = raw.strip();
            Matcher withLine = WITH_LINE.matcher(line);
            if (withLine.matches()) {
                String path = sourcePaths.apply(withLine.group(1).trim());
                Integer lineNum = parseLineNumber(withLine.group(2));
                Severity sev = parseSeverity(withLine.group(3));
                String message = withLine.group(4).trim();
                result.add(new Diagnostic(sev, "", message, new Location(path, lineNum, null), "nasm", raw));
                continue;
            }

            Matcher withoutLine = WITHOUT_LINE.matcher(line);
            if (withoutLine.matches()) {
                String rawPath = withoutLine.group(1).trim();
                Location loc = rawPath.equalsIgnoreCase("nasm") ? null : new Location(sourcePaths.apply(rawPath), null, null);
                Severity sev = parseSeverity(withoutLine.group(2));
                String message = withoutLine.group(3).trim();
                result.add(new Diagnostic(sev, "", message, loc, "nasm", raw));
            }
        }
        return List.copyOf(result);
    }

    private static Integer parseLineNumber(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Severity parseSeverity(String word) {
        String upper = word.toUpperCase(Locale.ROOT);
        if ("WARNING".equals(upper)) {
            return Severity.WARNING;
        }
        return Severity.ERROR;
    }
}
