package io.github.dinamo541.idearm.cli;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("requires-tasm")
class BuildIntegrationTest {

    @Test
    void buildsAndCleansHelloProjectEndToEnd() throws Exception {
        Path baseDir = Path.of("").toAbsolutePath();
        Path repoRoot = Files.exists(baseDir.resolve("examples")) ? baseDir : baseDir.getParent();
        Path helloDir = repoRoot.resolve("examples").resolve("hello");
        assertTrue(Files.isRegularFile(helloDir.resolve("idearm.toml")), "examples/hello/idearm.toml must exist at: " + helloDir);

        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int buildCode = Main.run(new String[]{"build", helloDir.toString(), "--config", "release"}, new PrintStream(out), new PrintStream(err));
        assertEquals(0, buildCode, "Build must succeed with exit code 0. Err: " + err);

        Path exe = helloDir.resolve("build").resolve("release").resolve("bin").resolve("main.exe");
        assertTrue(Files.isRegularFile(exe), "Built binary main.exe must exist");
        assertTrue(Files.size(exe) > 0, "Built binary main.exe must not be empty");
        // DOS writes MAIN.EXE inside the emulator; the project must receive the planned lower-case names.
        try (var published = Files.walk(helloDir.resolve("build").resolve("release"))) {
            var names = published.filter(Files::isRegularFile)
                    .map(path -> helloDir.resolve("build").resolve("release").relativize(path).toString().replace('\\', '/'))
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(java.util.Set.of("obj/main.obj", "lst/main.lst", "bin/main.exe", "map/main.map"), names);
        }

        // Clean up
        out.reset();
        err.reset();
        int cleanCode = Main.run(new String[]{"clean", helloDir.toString()}, new PrintStream(out), new PrintStream(err));
        assertEquals(0, cleanCode, "Clean must succeed with exit code 0");
        assertFalse(Files.exists(exe), "Binary must be removed after clean");
    }
}
