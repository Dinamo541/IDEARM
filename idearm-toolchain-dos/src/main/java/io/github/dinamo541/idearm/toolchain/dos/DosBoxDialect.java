package io.github.dinamo541.idearm.toolchain.dos;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.execution.DosBoxDialects;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The three DOSBox dialects proven in Phase 0 and S7: they share the batch protocol, but not their switches, their
 * isolation or whether a build can run without a window.
 *
 * <ul>
 *   <li>DOSBox-X has a silent mode.</li>
 *   <li>DOSBox 0.74-3 has none, but builds without any window when SDL is given its "dummy" video driver (S7);
 *       its {@code -noconsole} switch exists only on Windows.</li>
 *   <li>Staging crashes with the dummy driver (S7), so its builds show a window.</li>
 * </ul>
 */
public enum DosBoxDialect {
    DOSBOX_X(true, true, List.of("-silent", "-exit", "-fastlaunch"), List.of("-fastlaunch"), Map.of()),
    STAGING(true, false, List.of("-noprimaryconf", "-nolocal", "-exit"), List.of("-noprimaryconf", "-nolocal"), Map.of()),
    CLASSIC(false, true, List.of("-exit"), List.of(),
            Map.of("SDL_VIDEODRIVER", "dummy", "SDL_AUDIODRIVER", "dummy"));

    /**
     * The {@code [autoexec]} line that forbids MOUNT, IMGMOUNT and BOOT for the rest of the session, so a program
     * cannot reach a host folder; verified in all three dialects (S7). It must come after the IDE's own mounts.
     */
    public static final String SECURE_MODE = "config -securemode";

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");

    private final boolean readOnlyMounts;
    private final boolean invisibleBuild;
    private final List<String> buildOptions;
    private final List<String> sessionOptions;
    private final Map<String, String> buildEnvironment;

    DosBoxDialect(boolean readOnlyMounts, boolean invisibleBuild, List<String> buildOptions,
                  List<String> sessionOptions, Map<String, String> buildEnvironment) {
        this.readOnlyMounts = readOnlyMounts;
        this.invisibleBuild = invisibleBuild;
        this.buildOptions = buildOptions;
        this.sessionOptions = sessionOptions;
        this.buildEnvironment = buildEnvironment;
    }

    public boolean readOnlyMounts() { return readOnlyMounts; }

    /** Whether a build session runs without showing any window. */
    public boolean invisibleBuild() { return invisibleBuild; }

    /** Environment variables a build session needs, such as the SDL dummy driver that hides 0.74-3's window. */
    public Map<String, String> buildEnvironment() { return buildEnvironment; }

    /** A headless build session that exits when its batch ends. */
    public List<String> command(Path executable, Path configuration) {
        var command = new ArrayList<>(List.of(executable.toString()));
        command.addAll(buildOptions);
        command.addAll(windowsOnly());
        command.addAll(List.of("-conf", configuration.toString()));
        return List.copyOf(command);
    }

    /** A session the user sees and uses: the program's run, or a debugger. */
    public List<String> sessionCommand(Path executable, Path configuration) {
        var command = new ArrayList<>(List.of(executable.toString()));
        command.addAll(sessionOptions);
        command.addAll(windowsOnly());
        command.addAll(List.of("-conf", configuration.toString()));
        return List.copyOf(command);
    }

    /** 0.74-3 opens a second "status" console window on Windows unless told not to; the switch exists only there. */
    private List<String> windowsOnly() {
        return this == CLASSIC && WINDOWS ? List.of("-noconsole") : List.of();
    }

    public static DosBoxDialect forId(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case DosBoxDialects.X, "dosboxx" -> DOSBOX_X;
            case DosBoxDialects.STAGING, "staging" -> STAGING;
            case DosBoxDialects.CLASSIC, "dosbox074", DosBoxDialects.AUTO -> CLASSIC;
            default -> throw new DomainException("environment.unsupported", "Unknown DOSBox dialect: " + id, id);
        };
    }
}
