package io.github.dinamo541.idearm.toolchain.dos.borland;

import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import io.github.dinamo541.idearm.domain.diagnostic.Location;
import io.github.dinamo541.idearm.domain.port.DiagnosticParser;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class TlinkDiagnosticParser implements DiagnosticParser {
    private static final Pattern DIAGNOSTIC = Pattern.compile("^(Error|Fatal|Warning):\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MODULE = Pattern.compile("(?i)\\bin module\\s+([^\\s]+)");

    @Override
    public List<Diagnostic> parse(String output, Function<String, String> sourcePaths) {
        var result = new ArrayList<Diagnostic>();
        for (String raw : output.lines().toList()) {
            var diagnostic = DIAGNOSTIC.matcher(raw.strip());
            if (!diagnostic.matches()) continue;
            String message = diagnostic.group(2);
            var modules = new LinkedHashSet<String>();
            var matcher = MODULE.matcher(message);
            while (matcher.find()) modules.add(sourcePaths.apply(matcher.group(1)));
            // Both definitions in a duplicate-symbol error must be navigable in the Problems panel.
            if (modules.isEmpty()) {
                result.add(new Diagnostic(TasmDiagnosticParser.severity(diagnostic.group(1)), "", message, null, "tlink", raw));
            } else {
                for (String module : modules) {
                    result.add(new Diagnostic(TasmDiagnosticParser.severity(diagnostic.group(1)), "", message,
                        new Location(module, null, null), "tlink", raw));
                }
            }
        }
        return List.copyOf(result);
    }
}
