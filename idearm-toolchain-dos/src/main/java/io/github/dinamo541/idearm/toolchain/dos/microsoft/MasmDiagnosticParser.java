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
 * Diagnostic parser for Microsoft MASM (ML 6.11).
 *
 * <p>Parses diagnostics formatted like:
 * {@code C:\PATH\FILE.ASM(12): error A2006: undefined symbol : printNumber}
 * or {@code C:\PATH\FILE.ASM(3): warning A4011: multiple .MODEL directives found}
 */
public final class MasmDiagnosticParser implements DiagnosticParser {

    private static final Pattern LOCATED_DIAGNOSTIC = Pattern.compile(
            "^(.+?)\\((\\d+)\\)\\s*:\\s*(fatal error|error|warning)\\s+([A-Za-z0-9]+)\\s*:\\s*(.*)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern UNLOCATED_DIAGNOSTIC = Pattern.compile(
            "^(?:(.+?)\\s*:\\s*)?(fatal error|error|warning)\\s+([A-Za-z0-9]+)\\s*:\\s*(.*)$",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public List<Diagnostic> parse(String output, Function<String, String> sourcePaths) {
        var result = new ArrayList<Diagnostic>();
        for (String raw : output.lines().toList()) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("Assembling:")) {
                continue;
            }

            var located = LOCATED_DIAGNOSTIC.matcher(line);
            if (located.matches()) {
                String file = located.group(1).trim();
                int lineNumber = Integer.parseInt(located.group(2));
                Severity severity = parseSeverity(located.group(3));
                String code = located.group(4).trim();
                String message = located.group(5).trim();

                Location location = new Location(sourcePaths.apply(file), lineNumber, null);
                result.add(new Diagnostic(severity, code, message, location, "ml", raw));
                continue;
            }

            var unlocated = UNLOCATED_DIAGNOSTIC.matcher(line);
            if (unlocated.matches()) {
                String file = unlocated.group(1);
                Severity severity = parseSeverity(unlocated.group(2));
                String code = unlocated.group(3).trim();
                String message = unlocated.group(4).trim();

                Location location = (file != null && !file.isBlank())
                        ? new Location(sourcePaths.apply(file.trim()), null, null)
                        : null;
                result.add(new Diagnostic(severity, code, message, location, "ml", raw));
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
