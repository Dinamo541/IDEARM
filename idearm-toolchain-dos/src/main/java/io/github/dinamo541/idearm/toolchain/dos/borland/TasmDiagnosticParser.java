package io.github.dinamo541.idearm.toolchain.dos.borland;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.diagnostic.Severity;
import io.github.dinamo541.idearm.domain.port.DiagnosticParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Parses only tool diagnostics; banners and localized DOS shell output are not diagnostics. */
public final class TasmDiagnosticParser implements DiagnosticParser {
    private static final Pattern WITH_LINE = Pattern.compile("^\\*+(Error|Fatal|Warning)\\*+\\s+(.+?)\\((\\d+)\\)\\s+(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WITHOUT_LINE = Pattern.compile("^\\*+(Error|Fatal|Warning)\\*+\\s+(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MISSING_FILE = Pattern.compile("(?i)Can't locate file:\\s*(.+)$");

    @Override
    public List<Diagnostic> parse(String output, Function<String, String> sourcePaths) {
        var result = new ArrayList<Diagnostic>();
        for (String raw : output.lines().toList()) {
            String line = raw.strip();
            var located = WITH_LINE.matcher(line);
            if (located.matches()) {
                Integer number;
                try { number = Integer.valueOf(located.group(3)); }
                catch (NumberFormatException ignored) { number = null; }
                result.add(new Diagnostic(severity(located.group(1)), "", located.group(4),
                    new Location(sourcePaths.apply(located.group(2)), number, null), "tasm", raw));
                continue;
            }
            var plain = WITHOUT_LINE.matcher(line);
            if (plain.matches()) {
                var missing = MISSING_FILE.matcher(plain.group(2));
                Location location = missing.find() ? new Location(sourcePaths.apply(missing.group(1)), null, null) : null;
                result.add(new Diagnostic(severity(plain.group(1)), "", plain.group(2), location, "tasm", raw));
            }
        }
        return List.copyOf(result);
    }

    static Severity severity(String word) {
        return Severity.valueOf(word.toUpperCase(Locale.ROOT));
    }
}
