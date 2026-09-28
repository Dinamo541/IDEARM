package io.github.dinamo541.idearm.domain.build;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import java.util.List;
import java.util.Map;

/**
 * Everything a runner needs to build one configuration.
 *
 * @param outputs      artifact paths relative to {@code build/<configuration>}, spelled as they are published
 * @param listings     for each source that produces a listing, the listing's path in {@code outputs}; a debugger
 *                     uses it to map addresses back to source lines; the entry source comes first
 * @param dependencies files the sources pull in with INCLUDE, as project-relative paths. They are not assembled
 *                     on their own, but a runner must place them where the assembler will find them, and a
 *                     freshness check must treat them as build inputs.
 */
public record BuildPlan(Project project, String configuration, TargetProfile target,
                        List<ToolInvocation> steps, String executable, List<String> outputs,
                        Map<String, String> listings, List<String> dependencies) {
    public BuildPlan {
        steps = List.copyOf(steps);
        outputs = List.copyOf(outputs);
        listings = listings == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(listings));
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    public BuildPlan(Project project, String configuration, TargetProfile target,
                     List<ToolInvocation> steps, String executable, List<String> outputs,
                     Map<String, String> listings) {
        this(project, configuration, target, steps, executable, outputs, listings, List.of());
    }

    public BuildPlan(Project project, String configuration, TargetProfile target,
                     List<ToolInvocation> steps, String executable, List<String> outputs) {
        this(project, configuration, target, steps, executable, outputs, Map.of(), List.of());
    }
}
