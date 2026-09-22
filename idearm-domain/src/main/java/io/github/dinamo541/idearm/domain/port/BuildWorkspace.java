package io.github.dinamo541.idearm.domain.port;
import io.github.dinamo541.idearm.domain.model.Project;
import java.nio.file.Path;
import java.util.List;

/** Boundary checks, locking and generated-file ownership are enforced by the implementation. */
public interface BuildWorkspace {
    String GENERATED_MARKER = ".idearm-generated";
    String GENERATED_MARKER_CONTENT = "IDEARM generated directory\nschema=1\n";

    WorkspaceLock lock(Path projectRoot);
    void validateSources(Path projectRoot, Project project);
    void invalidate(Path projectRoot, String configuration);
    void publish(Path projectRoot, String configuration, Path stagedOutput, List<String> outputs);
    void clean(Path projectRoot);
    interface WorkspaceLock extends AutoCloseable { @Override void close(); }
}
