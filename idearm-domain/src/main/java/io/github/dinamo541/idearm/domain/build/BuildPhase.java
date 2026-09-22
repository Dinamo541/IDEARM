package io.github.dinamo541.idearm.domain.build;

/**
 * The role a build step plays in a plan.
 *
 * <p>Runners use the phase to decide what may still run after a failure (every source is assembled so the user
 * sees all errors at once, while linking is skipped), and the application uses it to pick the matching
 * diagnostic parser instead of assuming the last step is the linker.
 */
public enum BuildPhase { ASSEMBLE, LINK }
