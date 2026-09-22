package io.github.dinamo541.idearm.domain.port;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.ResolvedToolchain;
import io.github.dinamo541.idearm.domain.model.TargetSupport;
import java.util.Set;
public interface ToolchainProvider {
    String id();
    Set<TargetSupport> supports();
    AssemblerAdapter assembler();
    LinkerAdapter linker();
    ResolvedToolchain resolve(Project project, ToolRegistry registry);
}
