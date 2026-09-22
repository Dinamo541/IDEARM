package io.github.dinamo541.idearm.infrastructure.workspace;

import io.github.dinamo541.idearm.domain.execution.StagingPaths;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Where the IDE keeps its disposable build and run folders.
 *
 * <p>DOSBox mounts them, so they need an ASCII path without spaces ({@link StagingPaths}). The obvious place, the
 * user's local application data, fails that rule for a Windows account such as "Juan Perez" or "José", and using it
 * anyway stopped the IDE from starting. The first usable candidate wins:
 * <ol>
 *   <li>{@code IDEARM_STAGING_DIR}, when the user sets it (used as given);</li>
 *   <li>Windows: {@code %LOCALAPPDATA%\IDEARM\staging}, then its 8.3 short name, then
 *       {@code %ProgramData%\IDEARM\staging}, then {@code %SystemDrive%\IDEARM\staging};</li>
 *   <li>other systems: {@code $XDG_CACHE_HOME/idearm/staging} (or {@code ~/.cache/idearm/staging}), then
 *       {@code /tmp/idearm-<user>/staging}, private to the user.</li>
 * </ol>
 * When none is mountable the first candidate is returned: native builds still work there, and a DOS build reports
 * why it cannot.
 */
public final class StagingLocation {

    public static final String OVERRIDE = "IDEARM_STAGING_DIR";

    /** The machine facts the choice depends on, so tests can describe any machine. */
    record Host(Map<String, String> environment, boolean windows, Path home, Path temp, String user,
                Function<Path, Optional<String>> shortName, Predicate<Path> usable) {
    }

    private StagingLocation() {
    }

    /** The staging root for this machine. */
    public static Path resolve() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
        return resolve(new Host(System.getenv(), windows,
                Path.of(System.getProperty("user.home", ".")),
                Path.of(System.getProperty("java.io.tmpdir", ".")),
                System.getProperty("user.name", "user"),
                WindowsShortPaths::shortName,
                StagingLocation::prepare));
    }

    static Path resolve(Host host) {
        String custom = host.environment().get(OVERRIDE);
        if (custom != null && !custom.isBlank()) {
            return Path.of(custom.strip());
        }
        List<Path> candidates = host.windows() ? windowsCandidates(host) : unixCandidates(host);
        for (Path candidate : candidates) {
            if (StagingPaths.isMountable(candidate.toString()) && host.usable().test(candidate)) {
                return candidate;
            }
            // A long Windows path may still have an ASCII short name that DOSBox can mount.
            if (host.windows() && host.usable().test(candidate)) {
                Optional<Path> shortPath = host.shortName().apply(candidate)
                        .filter(StagingPaths::isMountable)
                        .map(Path::of);
                if (shortPath.isPresent()) {
                    return shortPath.get();
                }
            }
        }
        return candidates.isEmpty() ? host.temp().resolve("idearm-staging") : candidates.getFirst();
    }

    private static List<Path> windowsCandidates(Host host) {
        var candidates = new ArrayList<Path>();
        add(candidates, host.environment().get("LOCALAPPDATA"), "IDEARM", "staging");
        add(candidates, host.environment().get("ProgramData"), "IDEARM", "staging");
        String drive = host.environment().get("SystemDrive");
        if (drive != null && !drive.isBlank()) {
            candidates.add(Path.of(drive.strip() + "\\", "IDEARM", "staging"));
        }
        candidates.add(host.temp().resolve("idearm-staging"));
        return candidates;
    }

    private static List<Path> unixCandidates(Host host) {
        var candidates = new ArrayList<Path>();
        String cache = host.environment().get("XDG_CACHE_HOME");
        if (cache != null && !cache.isBlank()) {
            candidates.add(Path.of(cache.strip(), "idearm", "staging"));
        } else {
            candidates.add(host.home().resolve(".cache").resolve("idearm").resolve("staging"));
        }
        String user = host.user().replaceAll("[^A-Za-z0-9_-]", "_");
        candidates.add(Path.of("/tmp", "idearm-" + (user.isBlank() ? "user" : user), "staging"));
        return candidates;
    }

    private static void add(List<Path> candidates, String base, String... more) {
        if (base != null && !base.isBlank()) {
            candidates.add(Path.of(base.strip(), more));
        }
    }

    /** Creates the folder (private to the user where the file system supports it) and checks it is writable. */
    private static boolean prepare(Path folder) {
        try {
            if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(folder);
                if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                    Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwx------"));
                }
            }
            return Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS) && Files.isWritable(folder);
        } catch (IOException | RuntimeException unusable) {
            return false;
        }
    }
}
