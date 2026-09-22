package io.github.dinamo541.idearm.domain.port;
import io.github.dinamo541.idearm.domain.build.*;
import io.github.dinamo541.idearm.domain.model.ResolvedToolchain;
import java.nio.file.Path;
import java.time.Duration;
public interface ToolRunner {
    ToolRunResult run(BuildPlan plan, Path projectRoot, ResolvedToolchain toolchain,
                      CancellationToken cancellation, Duration timeout);
    /** Release only the staging owned by this result, after publication or failure. */
    default void release(ToolRunResult result) {}
}
