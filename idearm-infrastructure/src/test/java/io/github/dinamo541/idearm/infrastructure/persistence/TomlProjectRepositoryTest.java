package io.github.dinamo541.idearm.infrastructure.persistence;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TomlProjectRepositoryTest {

    private final TomlProjectRepository repository = new TomlProjectRepository();

    @Test
    void loadsValidProject(@TempDir Path tempDir) throws IOException {
        Path toml = tempDir.resolve("idearm.toml");
        Files.writeString(toml, """
            schema = 1
            [project]
            name = "TEST"
            version = "1.0.0"
            [target]
            profile = "dos-exe-16"
            cpu = "8086"
            [toolchain]
            id = "borland-tasm"
            version = ">=3.2"
            [sources]
            entry = "src/MAIN.ASM"
            """);

        Project project = repository.load(tempDir);
        assertNotNull(project);
        assertEquals(1, project.schema());
        assertEquals("TEST", project.info().name());
        assertEquals("1.0.0", project.info().version());
        assertEquals("dos-exe-16", project.target().profile());
        assertEquals("8086", project.target().cpu());
        assertEquals("borland-tasm", project.toolchain().id());
        assertEquals("src/MAIN.ASM", project.sources().entry());
    }

    @Test
    void roundTripSaveAndLoad(@TempDir Path tempDir) {
        Path toml = tempDir.resolve("idearm.toml");
        assertDoesNotThrow(() -> {
            Files.writeString(toml, """
                schema = 1
                [project]
                name = "ROUNDTRIP"
                version = "0.2.0"
                [target]
                profile = "dos-exe-16"
                cpu = "8086"
                [toolchain]
                id = "borland-tasm"
                version = ">=3.2"
                [sources]
                entry = "src/MAIN.ASM"
                """);
        });

        Project loaded = repository.load(tempDir);
        assertDoesNotThrow(() -> repository.save(tempDir, loaded));

        Project reloaded = repository.load(tempDir);
        assertEquals(loaded.info().name(), reloaded.info().name());
        assertEquals(loaded.target().profile(), reloaded.target().profile());
        assertEquals(loaded.toolchain().id(), reloaded.toolchain().id());
    }

    @Test
    void rejectsUnsupportedSchema(@TempDir Path tempDir) throws IOException {
        Path toml = tempDir.resolve("idearm.toml");
        Files.writeString(toml, """
            schema = 999
            [project]
            name = "FAIL"
            version = "1.0.0"
            [target]
            profile = "dos-exe-16"
            [toolchain]
            id = "borland-tasm"
            [sources]
            entry = "src/MAIN.ASM"
            """);

        assertThrows(DomainException.class, () -> repository.load(tempDir));
    }

    @Test
    void rejectsUnknownFields(@TempDir Path tempDir) throws IOException {
        Path toml = tempDir.resolve("idearm.toml");
        Files.writeString(toml, """
            schema = 1
            unknown_root_key = "bad"
            [project]
            name = "FAIL"
            version = "1.0.0"
            [target]
            profile = "dos-exe-16"
            [toolchain]
            id = "borland-tasm"
            [sources]
            entry = "src/MAIN.ASM"
            """);

        assertThrows(DomainException.class, () -> repository.load(tempDir));
    }
}
