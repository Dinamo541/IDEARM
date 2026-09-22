package io.github.dinamo541.idearm.domain.port;
import io.github.dinamo541.idearm.domain.diagnostic.Diagnostic;
import java.util.List;
import java.util.function.Function;
@FunctionalInterface
public interface DiagnosticParser { List<Diagnostic> parse(String output, Function<String, String> sourcePaths); }
