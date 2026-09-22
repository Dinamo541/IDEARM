package io.github.dinamo541.idearm.domain.port;

import io.github.dinamo541.idearm.domain.dist.DistResult;
import io.github.dinamo541.idearm.domain.model.DistConfiguration;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.TargetProfile;

import java.nio.file.Path;
import java.util.List;

/**
 * Port for creating standalone, portable distributions of assembled projects.
 */
public interface DistPackager {

    boolean supports(TargetProfile profile);

    DistResult packageProject(Project project, Path releaseExecutable, Path distDirectory,
                              List<Path> resources, DistConfiguration config);
}
