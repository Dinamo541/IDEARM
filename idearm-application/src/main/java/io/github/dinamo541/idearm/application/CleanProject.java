package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.port.BuildWorkspace;
import java.nio.file.Path;
import java.util.Objects;

/** Shares the build lock so Clean cannot remove an active build's output. */
public final class CleanProject {
    private final BuildWorkspace workspace;
    public CleanProject(BuildWorkspace workspace) { this.workspace = Objects.requireNonNull(workspace); }
    public void execute(Path root) {
        try (var lock = workspace.lock(root)) {
            workspace.clean(root);
        }
    }
}
