package io.github.dinamo541.idearm.infrastructure.workspace;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.*;
import io.github.dinamo541.idearm.domain.port.BuildWorkspace.WorkspaceLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileBuildWorkspaceTest {

    private final FileBuildWorkspace workspace = new FileBuildWorkspace();

    @Test
    void acquiresAndReleasesWorkspaceLock(@TempDir Path tempDir) {
        try (WorkspaceLock first = workspace.lock(tempDir)) {
            assertNotNull(first);
        }

        // After releasing, locking should succeed again
        assertDoesNotThrow(() -> {
            try (WorkspaceLock second = workspace.lock(tempDir)) {
                assertNotNull(second);
            }
        });
    }

    @Test
    void theOwningThreadMayLockAgainWhileDelegating(@TempDir Path tempDir) {
        // Run and Package hold the project and then delegate to a build, which locks it again.
        try (WorkspaceLock outer = workspace.lock(tempDir)) {
            assertNotNull(outer);
            try (WorkspaceLock inner = workspace.lock(tempDir)) {
                assertNotNull(inner);
            }
            // Releasing the nested level must not free the project while the outer operation runs.
            assertThrows(DomainException.class, () -> lockFromAnotherThread(tempDir));
        }
        assertDoesNotThrow(() -> workspace.lock(tempDir).close());
    }

    @Test
    void anotherThreadIsRefusedWhileAnOperationRuns(@TempDir Path tempDir) {
        try (WorkspaceLock held = workspace.lock(tempDir)) {
            assertNotNull(held);
            assertThrows(DomainException.class, () -> lockFromAnotherThread(tempDir));
        }
    }

    private void lockFromAnotherThread(Path projectRoot) {
        var failure = new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
        Thread other = new Thread(() -> {
            try (WorkspaceLock lock = workspace.lock(projectRoot)) {
                assertNotNull(lock);
            } catch (RuntimeException refused) {
                failure.set(refused);
            }
        });
        other.start();
        try {
            other.join(5000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    @Test
    void validatesMissingEntryThrowsDomainException(@TempDir Path tempDir) {
        Project p = new Project(
                1,
                new ProjectInfo("TEST", "0.1.0"),
                new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection("borland-tasm", ">=3.2"),
                new Sources("src/MAIN.ASM", List.of(), List.of(), List.of()),
                new Resources(List.of()),
                Map.of("release", BuildConfiguration.release()),
                new RunConfiguration("dosbox", "required", true, "auto", 16, List.of()),
                new DebugConfiguration("external"),
                new DistConfiguration(true, false)
        );

        assertThrows(DomainException.class, () -> workspace.validateSources(tempDir, p));
    }

    @Test
    void publishesArtifactsAndCleansWorkspace(@TempDir Path tempDir, @TempDir Path staged) throws IOException {
        Path artifact = staged.resolve("HELLO.EXE");
        Files.writeString(artifact, "EXE_BINARY_DATA");

        workspace.invalidate(tempDir, "release");
        workspace.publish(tempDir, "release", staged, List.of("HELLO.EXE"));

        Path published = tempDir.resolve("build").resolve("release").resolve("HELLO.EXE");
        assertTrue(Files.isRegularFile(published));
        assertEquals("EXE_BINARY_DATA", Files.readString(published));

        // Clean should delete the generated build directory
        workspace.clean(tempDir);
        assertFalse(Files.exists(published));
    }
}
