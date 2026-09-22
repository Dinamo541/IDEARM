package io.github.dinamo541.idearm.toolchain.dos.microsoft;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.port.DiagnosticParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Diagnostic parser for Microsoft 16-bit LINK (Version 5.31).
 *
 * <p>Parses diagnostics formatted like:
 * {@code C:\OBJ\EXTERN.OBJ(C:\SRC\EXTERN.ASM) : error L2029: 'printNumber' : unresolved external}
 * or {@code LINK : warning L4021: no stack segment}
 */
public final class MasmLinkerDiagnosticParser implements DiagnosticParser {

    private static final Pattern MODULE_LOCATED = Pattern.compile(
            "^(.+?)\\((.+?)\\)\\s*:\\s*(fatal error|error|warning)\\s+([A-Za-z0-9]+)\\s*:\\s*(.*)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GENERIC_LINK = Pattern.compile(
            "^(?:LINK(?:\\.EXE)?\\s*)?:?\\s*(fatal error|error|warning)\\s+([A-Za-z0-9]+)\\s*:\\s*(.*)$",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public List<Diagnostic> parse(String output, Function<String, String> sourcePaths) {
        var result = new ArrayList<Diagnostic>();
        for (String raw : output.lines().toList()) {
            String line = raw.strip();
            if (line.isEmpty()
                    || line.startsWith("Microsoft (R)")
                    || line.startsWith("Copyright (C)")
                    || line.startsWith("There was ")
                    || line.startsWith("There were ")) {
                continue;
            }

            var moduleMatch = MODULE_LOCATED.matcher(line);
            if (moduleMatch.matches()) {
                String sourceFile = moduleMatch.group(2).trim();
                Severity severity = parseSeverity(moduleMatch.group(3));
                String code = moduleMatch.group(4).trim();
                String message = moduleMatch.group(5).trim();

                Location location = new Location(sourcePaths.apply(sourceFile), null, null);
                result.add(new Diagnostic(severity, code, message, location, "link", raw));
                continue;
            }

            var genericMatch = GENERIC_LINK.matcher(line);
            if (genericMatch.matches()) {
                Severity severity = parseSeverity(genericMatch.group(1));
                String code = genericMatch.group(2).trim();
                String message = genericMatch.group(3).trim();

                result.add(new Diagnostic(severity, code, message, null, "link", raw));
            }
        }
        return List.copyOf(result);
    }

    private static Severity parseSeverity(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("fatal")) {
            return Severity.FATAL;
        } else if (lower.contains("error")) {
            return Severity.ERROR;
        } else {
            return Severity.WARNING;
        }
    }
}
