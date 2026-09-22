package io.github.dinamo541.idearm.domain.port;

import io.github.dinamo541.idearm.domain.debug.DebugLaunchSpec;
import io.github.dinamo541.idearm.domain.model.TargetProfile;

/**
 * SPI port for execution environments capable of hosting debugging sessions.
 */
public interface DebugEnvironmentProvider {

    String id();

    boolean supports(TargetProfile profile);

    DebugSession launchDebug(DebugLaunchSpec spec);

    default DebugSession launchDebug(DebugLaunchSpec spec, java.util.function.Consumer<io.github.dinamo541.idearm.domain.debug.DebugEvent> events) {
        return launchDebug(spec);
    }
}
