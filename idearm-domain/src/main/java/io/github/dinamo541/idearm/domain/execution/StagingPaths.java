package io.github.dinamo541.idearm.domain.execution;

/**
 * The folders DOSBox can mount reliably.
 *
 * <p>Phase 0 (S4) showed that a host path with spaces or non-ASCII characters cannot be mounted by every dialect, and
 * the generated configuration and batch files must not contain characters the DOS shell interprets. Every staging
 * folder the IDE mounts, and the place it creates them in, must pass this one rule.
 */
public final class StagingPaths {

    private static final String FORBIDDEN = "&|<>%\"";

    private StagingPaths() {
    }

    /** Whether the path is printable ASCII without spaces or batch metacharacters. */
    public static boolean isMountable(String path) {
        return path != null && !path.isEmpty()
                && path.chars().allMatch(c -> c > 32 && c <= 126 && FORBIDDEN.indexOf(c) < 0);
    }
}
