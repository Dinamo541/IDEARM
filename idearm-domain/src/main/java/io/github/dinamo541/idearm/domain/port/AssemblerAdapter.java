package io.github.dinamo541.idearm.domain.port;
import io.github.dinamo541.idearm.domain.build.AssembleRequest;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.model.HostKind;
public interface AssemblerAdapter {
    ToolInvocation assemble(AssembleRequest request);
    DiagnosticParser diagnostics();
    HostKind host();
}
