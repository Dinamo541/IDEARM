package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.port.BuildWorkspace;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CleanProjectTest {

    @Test
    void invokesCleanOnWorkspaceUnderLock() {
        AtomicBoolean cleaned = new AtomicBoolean(false);
        AtomicBoolean locked = new AtomicBoolean(false);
        AtomicBoolean released = new AtomicBoolean(false);

        BuildWorkspace workspace = new BuildWorkspace() {
            @Override
            public WorkspaceLock lock(Path projectRoot) {
                locked.set(true);
                return () -> released.set(true);
            }

            @Override public void validateSources(Path projectRoot, io.github.dinamo541.idearm.domain.model.Project project) {}
            @Override public void invalidate(Path projectRoot, String configuration) {}
            @Override public void publish(Path projectRoot, String configuration, Path stagedOutput, java.util.List<String> outputs) {}

            @Override
            public void clean(Path projectRoot) {
                if (!locked.get()) fail("Clean invoked without lock!");
                cleaned.set(true);
            }
        };

        CleanProject cleanProject = new CleanProject(workspace);
        cleanProject.execute(Path.of("my-project"));

        assertTrue(locked.get(), "Workspace was locked");
        assertTrue(cleaned.get(), "Workspace was cleaned");
        assertTrue(released.get(), "Lock was released");
    }
}
