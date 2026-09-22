package io.github.dinamo541.idearm.domain.port;

import io.github.dinamo541.idearm.domain.execution.EnvCapability;
import io.github.dinamo541.idearm.domain.execution.ExecutionSession;
import io.github.dinamo541.idearm.domain.execution.IsolationLevel;
import io.github.dinamo541.idearm.domain.execution.LaunchSpec;
import io.github.dinamo541.idearm.domain.model.TargetProfile;

import java.util.Set;

/**
 * Port for execution environments (e.g. DOSBox, emulators, containers) where the user's program runs.
 */
public interface ExecutionEnvironmentProvider {

    String id();

    IsolationLevel isolation();

    Set<EnvCapability> capabilities();

    boolean supports(TargetProfile profile);

    ExecutionSession launch(LaunchSpec spec);
}
