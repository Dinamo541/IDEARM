package io.github.dinamo541.idearm.integration;

import io.github.dinamo541.idearm.cli.Main;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("requires-tasm")
class DistIntegrationTest {

    @Test
    void packagesHelloWorldProjectSuccessfully() {
        Path baseDir = Path.of(System.getProperty("user.dir"));
        Path helloDir = baseDir.resolve("examples/hello");
        if (!Files.exists(helloDir.resolve("idearm.toml"))) {
            helloDir = baseDir.getParent().resolve("examples/hello");
        }

        assertTrue(Files.exists(helloDir.resolve("idearm.toml")),
                "examples/hello/idearm.toml must exist");

        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int exitCode = Main.run(new String[]{"dist", helloDir.toString()}, new PrintStream(out), new PrintStream(err));
        assertEquals(0, exitCode, "Packaging hello project should exit with 0. Out: " + out + " Err: " + err);

        Path distDir = helloDir.resolve("dist");
        assertTrue(Files.isDirectory(distDir), "dist/ directory must be generated");
        // Compared as stored on disk: Windows would find MAIN.EXE under any spelling.
        try (var entries = Files.list(distDir)) {
            var names = entries.map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(java.util.Set.of(".idearm-generated", "main.exe", "dosbox.conf", "run.bat", "readme.txt"),
                    names);
        } catch (java.io.IOException unreadable) {
            fail(unreadable);
        }

        // Clean up dist and build afterwards
        Main.run(new String[]{"clean", helloDir.toString()}, new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));
        assertFalse(Files.exists(distDir), "Clean must remove dist/ directory");
        assertFalse(Files.exists(helloDir.resolve("build")), "Clean must remove build/ directory");
    }
}
