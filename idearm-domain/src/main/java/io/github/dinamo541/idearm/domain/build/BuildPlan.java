package io.github.dinamo541.idearm.domain.build;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import java.util.List;
import java.util.Map;

/**
 * Everything a runner needs to build one configuration.
 *
 * @param outputs  artifact paths relative to {@code build/<configuration>}, spelled as they are published
 * @param listings for each source that produces a listing, the listing's path in {@code outputs}; a debugger
 *                 uses it to map addresses back to source lines; the entry source comes first
 */
public record BuildPlan(Project project, String configuration, TargetProfile target,
                        List<ToolInvocation> steps, String executable, List<String> outputs,
                        Map<String, String> listings) {
    public BuildPlan {
        steps = List.copyOf(steps);
        outputs = List.copyOf(outputs);
        listings = listings == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(listings));
    }

    public BuildPlan(Project project, String configuration, TargetProfile target,
                     List<ToolInvocation> steps, String executable, List<String> outputs) {
        this(project, configuration, target, steps, executable, outputs, Map.of());
    }
}
