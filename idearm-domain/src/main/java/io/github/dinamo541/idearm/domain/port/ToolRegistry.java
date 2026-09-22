package io.github.dinamo541.idearm.domain.port;
import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The tool installations known on this machine. */
@FunctionalInterface
public interface ToolRegistry {

    Optional<ToolInstallation> find(String toolId);

    /**
     * Every registered installation, keyed by the role it fills.
     *
     * <p>A doctor screen reports what the IDE will actually use, so it must read the same registry the build
     * reads instead of detecting tools a second time. Registries that only resolve on demand report nothing.
     */
    default Map<String, ToolInstallation> all() {
        return Map.of();
    }

    /**
     * Registers the tools found in a folder the user picked (and its subfolders) and remembers them on this
     * machine, so a copy of TASM or MASM in any folder can be used. Returns what was registered.
     */
    default List<ToolInstallation> registerFolder(Path folder) {
        throw new DomainException("tools.register.unsupported", "This tool registry cannot register folders.");
    }
}
