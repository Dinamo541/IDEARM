package io.github.dinamo541.idearm.domain.port;
import io.github.dinamo541.idearm.domain.build.*;
@FunctionalInterface
public interface ProcessExecutor { ProcessResult run(ProcessRequest request, CancellationToken cancellation); }
