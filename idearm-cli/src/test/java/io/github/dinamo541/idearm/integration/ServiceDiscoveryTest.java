package io.github.dinamo541.idearm.integration;

import io.github.dinamo541.idearm.domain.port.DistPackager;
import io.github.dinamo541.idearm.domain.port.ExecutionEnvironmentProvider;
import io.github.dinamo541.idearm.domain.port.ProcessLauncher;
import io.github.dinamo541.idearm.domain.port.ToolchainProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every port the application resolves at runtime must be discoverable, both as a module declaration and through
 * {@code META-INF/services}: the IDE and the CLI can run either way, and a missing registration only shows up as
 * "no provider" the first time a user presses Run.
 */
class ServiceDiscoveryTest {

    @Test
    void toolchainsAreDiscoverable() {
        List<String> ids = load(ToolchainProvider.class).stream().map(ToolchainProvider::id).toList();

        assertTrue(ids.contains("borland-tasm"), ids.toString());
        assertTrue(ids.contains("microsoft-masm"), ids.toString());
    }

    @Test
    void executionEnvironmentsAreDiscoverable() {
        List<String> ids = load(ExecutionEnvironmentProvider.class).stream()
                .map(ExecutionEnvironmentProvider::id).toList();

        assertTrue(ids.contains("dosbox"), ids.toString());
    }

    @Test
    void debugEnvironmentsAreDiscoverable() {
        List<String> ids = load(io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider.class).stream()
                .map(io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider::id).toList();

        assertTrue(ids.contains("gdb"), ids.toString());
        assertTrue(ids.contains("emu8086"), ids.toString());
    }

    @Test
    void distPackagersAreDiscoverable() {
        assertFalse(load(DistPackager.class).isEmpty(), "Packaging a project needs at least one packager.");
    }

    @Test
    void aProcessLauncherIsRegisteredSoRunsStayIsolated() {
        assertFalse(load(ProcessLauncher.class).isEmpty(),
                "Without a launcher the emulator would outlive the IDE (ADR-002).");
    }

    private static <T> List<T> load(Class<T> service) {
        return ServiceLoader.load(service).stream().map(ServiceLoader.Provider::get).toList();
    }
}
