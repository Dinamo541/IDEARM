package io.github.dinamo541.idearm.domain.build;
import io.github.dinamo541.idearm.domain.model.HostKind;
import java.util.List;
import java.util.Objects;

/** Arguments are adapter-formatted. Outputs are relative to the session output root. */
public record ToolInvocation(String toolId, BuildPhase phase, HostKind hostKind, List<String> arguments,
                             String responseFileName, String responseFileContents, List<String> outputs) {
    public ToolInvocation {
        Objects.requireNonNull(toolId, "toolId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(hostKind, "hostKind");
        arguments = List.copyOf(arguments);
        outputs = List.copyOf(outputs);
    }
}
