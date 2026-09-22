package io.github.dinamo541.idearm.domain.port;

import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import java.nio.file.Path;
import java.util.List;

/**
 * Persists and retrieves project breakpoints.
 */
public interface BreakpointStore {
    List<Breakpoint> loadBreakpoints(Path projectRoot);
    void saveBreakpoints(Path projectRoot, List<Breakpoint> breakpoints);
}
