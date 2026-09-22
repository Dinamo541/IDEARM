package io.github.dinamo541.idearm.toolchain.dos;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Reverses DOS drive paths to original project paths without depending on the staging folder's lifetime. */
public final class PathMapper implements Function<String, String> {
    private final Map<String, String> sources;

    public PathMapper(Path projectRoot, Iterable<String> logicalSources) {
        this(projectRoot, logicalSources, null);
    }

    public PathMapper(Path projectRoot, Iterable<String> logicalSources, Path driveS) {
        var values = new HashMap<String, String>();
        for (String source : logicalSources) {
            String target = projectRoot.resolve(source.replace('\\', '/')).normalize().toString();
            values.put(normalize("S:\\" + source), target);
            if (driveS != null) {
                values.put(normalize(driveS.resolve(source.replace('/', '\\')).toString()), target);
            }
        }
        sources = Map.copyOf(values);
    }

    @Override
    public String apply(String dosPath) {
        return sources.getOrDefault(normalize(dosPath), dosPath);
    }

    private static String normalize(String path) {
        return path.replace('/', '\\').toUpperCase(Locale.ROOT);
    }
}
