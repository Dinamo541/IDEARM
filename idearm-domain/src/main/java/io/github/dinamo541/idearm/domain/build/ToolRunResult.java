package io.github.dinamo541.idearm.domain.build;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/** Staged output stays valid until ToolRunner.release is called. */
public record ToolRunResult(
        BuildStatus status,
        List<ToolResult> steps,
        Path outputDirectory,
        String output,
        Function<String, String> sourcePaths) {

    public ToolRunResult {
        steps = List.copyOf(steps);
        if (sourcePaths == null) {
            sourcePaths = Function.identity();
        }
    }

    public ToolRunResult(BuildStatus status, List<ToolResult> steps, Path outputDirectory, String output) {
        this(status, steps, outputDirectory, output, Function.identity());
    }
}
