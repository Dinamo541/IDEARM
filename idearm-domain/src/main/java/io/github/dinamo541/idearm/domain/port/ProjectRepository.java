package io.github.dinamo541.idearm.domain.port;
import io.github.dinamo541.idearm.domain.model.Project;
import java.nio.file.Path;

/** Reads and writes the project description. How it is stored is an infrastructure decision. */
public interface ProjectRepository {

    Project load(Path projectRoot);

    void save(Path projectRoot, Project project);

    /**
     * Whether this folder holds a project this repository can read.
     *
     * <p>It lets a caller tell "this is not a project yet" from "this project is broken" without knowing which
     * file holds the description. The default answer costs a full load; adapters that can check cheaply override it.
     */
    default boolean exists(Path projectRoot) {
        try {
            return load(projectRoot) != null;
        } catch (RuntimeException notAProject) {
            return false;
        }
    }
}
