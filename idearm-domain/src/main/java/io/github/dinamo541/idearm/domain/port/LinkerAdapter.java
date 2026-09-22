package io.github.dinamo541.idearm.domain.port;
import io.github.dinamo541.idearm.domain.build.LinkRequest;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.model.HostKind;
public interface LinkerAdapter {
    ToolInvocation link(LinkRequest request);
    DiagnosticParser diagnostics();
    HostKind host();
}
