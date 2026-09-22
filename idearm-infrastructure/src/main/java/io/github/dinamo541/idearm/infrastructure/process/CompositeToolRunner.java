package io.github.dinamo541.idearm.infrastructure.process;

import io.github.dinamo541.idearm.domain.build.BuildPlan;
import io.github.dinamo541.idearm.domain.build.CancellationToken;
import io.github.dinamo541.idearm.domain.build.ToolRunResult;
import io.github.dinamo541.idearm.domain.model.ResolvedToolchain;
import io.github.dinamo541.idearm.domain.port.ToolRunner;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Dispatches a BuildPlan to either HostProcessToolRunner (if all steps run natively on host)
 * or to a fallback runner (e.g. HybridToolRunner for DOSBox/DOS builds).
 */
public final class CompositeToolRunner implements ToolRunner {

    private final HostProcessToolRunner hostRunner;
    private final ToolRunner fallbackRunner;

    public CompositeToolRunner(HostProcessToolRunner hostRunner, ToolRunner fallbackRunner) {
        this.hostRunner = Objects.requireNonNull(hostRunner, "hostRunner");
        this.fallbackRunner = Objects.requireNonNull(fallbackRunner, "fallbackRunner");
    }

    @Override
    public ToolRunResult run(BuildPlan plan, Path projectRoot, ResolvedToolchain toolchain,
                             CancellationToken cancellation, Duration timeout) {
        if (HostProcessToolRunner.canRun(plan)) {
            return hostRunner.run(plan, projectRoot, toolchain, cancellation, timeout);
        }
        return fallbackRunner.run(plan, projectRoot, toolchain, cancellation, timeout);
    }

    @Override
    public void release(ToolRunResult result) {
        hostRunner.release(result);
        fallbackRunner.release(result);
    }
}
