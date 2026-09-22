package io.github.dinamo541.idearm.app.bootstrap;

import io.github.dinamo541.idearm.app.viewmodel.WorkbenchServices;
import io.github.dinamo541.idearm.domain.port.BreakpointStore;
import io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider;
import io.github.dinamo541.idearm.domain.port.DistPackager;
import io.github.dinamo541.idearm.domain.port.ExecutionEnvironmentProvider;
import io.github.dinamo541.idearm.domain.port.ToolRunner;
import io.github.dinamo541.idearm.domain.port.ToolchainProvider;
import io.github.dinamo541.idearm.infrastructure.persistence.FileBreakpointStore;
import io.github.dinamo541.idearm.infrastructure.persistence.TomlProjectRepository;
import io.github.dinamo541.idearm.infrastructure.process.CompositeToolRunner;
import io.github.dinamo541.idearm.infrastructure.process.HostProcessToolRunner;
import io.github.dinamo541.idearm.infrastructure.process.ProcessService;
import io.github.dinamo541.idearm.infrastructure.tools.DefaultToolRegistry;
import io.github.dinamo541.idearm.infrastructure.workspace.FileBuildWorkspace;
import io.github.dinamo541.idearm.infrastructure.workspace.LocalProjectFiles;
import io.github.dinamo541.idearm.infrastructure.workspace.StagingLocation;
import io.github.dinamo541.idearm.toolchain.dos.HybridToolRunner;
import io.github.dinamo541.idearm.domain.port.TerminalRunner;
import io.github.dinamo541.idearm.infrastructure.process.HostTerminalRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * The composition root of the desktop application: the only place in the presentation layer allowed to name
 * infrastructure and toolchain classes (ADR-007). Everything else receives ports.
 */
public final class WorkbenchBootstrap {

    /** How long a single build may take before its DOSBox session is killed; see ADR-002. */
    private static final Duration DEFAULT_BUILD_TIMEOUT = Duration.ofSeconds(60);
    private static final String BUILD_TIMEOUT_SECONDS = "IDEARM_BUILD_TIMEOUT_SECONDS";

    private WorkbenchBootstrap() {
    }

    /** Wires the adapters installed on this machine. */
    public static WorkbenchServices system() {
        Path stagingRoot = stagingRoot();
        var processes = new ProcessService();
        ToolRunner toolRunner = new CompositeToolRunner(
                new HostProcessToolRunner(processes, stagingRoot),
                new HybridToolRunner(processes, stagingRoot));
        return new WorkbenchServices(
                new TomlProjectRepository(),
                DefaultToolRegistry.system(),
                new FileBuildWorkspace(),
                toolRunner,
                stagingRoot,
                discover(ToolchainProvider.class),
                discover(ExecutionEnvironmentProvider.class),
                discover(DebugEnvironmentProvider.class),
                discover(DistPackager.class),
                ServiceLoader.load(BreakpointStore.class).findFirst().orElseGet(FileBreakpointStore::new),
                buildTimeout(),
                new LocalProjectFiles(),
                ServiceLoader.load(TerminalRunner.class).findFirst().orElseGet(HostTerminalRunner::new),
                new io.github.dinamo541.idearm.infrastructure.persistence.FileRecentItemsStore(
                        userDataDirectory().resolve("recent.json")));
    }

    /**
     * Staging must sit outside the project and on an ASCII path: DOSBox cannot mount a directory with accents or
     * spaces, which is why a project in a folder such as "Mi unidad" still builds (Phase 0, S4). The same rule
     * applies to the user's own folder ("Juan Perez", "José"), so {@link StagingLocation} picks a usable one.
     */
    static Path stagingRoot() {
        return StagingLocation.resolve();
    }

    /** A slow machine, or a large multi-module project, can need more than the default minute. */
    static Duration buildTimeout() {
        String configured = System.getenv(BUILD_TIMEOUT_SECONDS);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_BUILD_TIMEOUT;
        }
        try {
            long seconds = Long.parseLong(configured.strip());
            return seconds > 0 ? Duration.ofSeconds(seconds) : DEFAULT_BUILD_TIMEOUT;
        } catch (NumberFormatException notANumber) {
            return DEFAULT_BUILD_TIMEOUT;
        }
    }

    static Path userDataDirectory() {
        String custom = System.getenv("IDEARM_USER_DATA_DIR");
        if (custom != null && !custom.isBlank()) return Path.of(custom);
        String appData = System.getenv("APPDATA");
        return appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".idearm") : Path.of(appData, "IDEARM");
    }

    private static <T> List<T> discover(Class<T> service) {
        var found = new ArrayList<T>();
        ServiceLoader.load(service).forEach(found::add);
        return List.copyOf(found);
    }
}
