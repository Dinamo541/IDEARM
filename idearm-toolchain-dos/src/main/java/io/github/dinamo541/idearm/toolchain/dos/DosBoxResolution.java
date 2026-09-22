package io.github.dinamo541.idearm.toolchain.dos;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.execution.DosBoxDialects;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.domain.port.ToolRegistry;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;

/**
 * Which installed DOSBox a DOS build runs in.
 *
 * <p>A dialect the project names is used when it is installed. Otherwise the automatic order applies
 * ({@link DosBoxDialects#PREFERENCE}, DOSBox 0.74-3 first), restricted to dialects that build without showing a
 * window; only when none of those is installed does a build fall back to one that shows a window.
 */
public final class DosBoxResolution {

    private DosBoxResolution() {
    }

    public static ToolInstallation forBuild(ToolRegistry registry, Project project) {
        String choice = project.run().environment() == null ? "" : project.run().environment().toLowerCase(Locale.ROOT);
        var installed = new ArrayList<ToolInstallation>();
        var missing = new ArrayList<ToolInstallation>();
        for (String dialect : DosBoxDialects.candidates(choice)) {
            registry.find(dialect).ifPresent(tool -> (exists(tool) ? installed : missing).add(tool));
        }
        Optional<ToolInstallation> chosen = installed.stream()
                .filter(tool -> DosBoxDialects.PREFERENCE.contains(choice) && dialectOf(tool).equals(choice))
                .findFirst()
                .or(() -> installed.stream().filter(tool -> DosBoxDialect.forId(dialectOf(tool)).invisibleBuild()).findFirst())
                .or(() -> installed.stream().findFirst())
                .or(() -> registry.find(DosBoxDialects.AUTO))
                // A registered DOSBox whose program was moved or deleted is reported as such, not as "not found".
                .or(() -> missing.stream().findFirst());
        ToolInstallation installation = chosen.orElseThrow(() -> new DomainException("toolchain.not-found",
                "Register your own dosbox installation before building.", DosBoxDialects.AUTO));
        if (!exists(installation)) {
            throw new DomainException("toolchain.invalid-path", "The registered dosbox executable does not exist: "
                    + installation.executable(), DosBoxDialects.AUTO, installation.executable());
        }
        return installation;
    }

    /** The dialect an installation belongs to; a tool registered simply as "dosbox" is the classic one. */
    static String dialectOf(ToolInstallation tool) {
        String id = tool.toolId().toLowerCase(Locale.ROOT);
        if (DosBoxDialects.PREFERENCE.contains(id)) {
            return id;
        }
        try {
            return switch (DosBoxDialect.forId(id)) {
                case DOSBOX_X -> DosBoxDialects.X;
                case STAGING -> DosBoxDialects.STAGING;
                case CLASSIC -> DosBoxDialects.CLASSIC;
            };
        } catch (DomainException unknownDialect) {
            return DosBoxDialects.CLASSIC;
        }
    }

    private static boolean exists(ToolInstallation tool) {
        return Files.isRegularFile(tool.executable());
    }
}
