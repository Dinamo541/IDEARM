package io.github.dinamo541.idearm.infrastructure.workspace;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** The link checks refuse links and junctions, but not the 8.3 names of the staging folder. */
@EnabledOnOs(OS.WINDOWS)
class SafePathsTest {

    @TempDir
    Path tempDir;

    /** A user folder such as "José Pérez" is reached through its short name, which DOSBox can mount. */
    @Test
    void aShortNameIsNotALink() throws IOException {
        Path longFolder = Files.createDirectories(tempDir.resolve("José Pérez").resolve("staging"));
        Path out = Files.createDirectories(longFolder.resolve("out"));
        var shortName = WindowsShortPaths.shortName(longFolder);
        assumeTrue(shortName.isPresent() && !shortName.get().equals(longFolder.toString()),
                "8.3 names are disabled on this volume");
        Path shortFolder = Path.of(shortName.get());

        assertDoesNotThrow(() -> SafePaths.inspect(shortFolder.resolve("out")));
        assertDoesNotThrow(() -> SafePaths.inspectAncestors(shortFolder, shortFolder.resolve("out")));
        assertTrue(Files.isDirectory(out));
    }

    @Test
    void aJunctionOnTheWayIsStillRefused() throws Exception {
        Path target = Files.createDirectories(tempDir.resolve("target").resolve("out"));
        Path junction = tempDir.resolve("junction");
        int created = new ProcessBuilder("cmd", "/c", "mklink", "/J", junction.toString(), target.getParent().toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
        assumeTrue(created == 0 && Files.exists(junction, LinkOption.NOFOLLOW_LINKS), "mklink /J is not available");

        var failure = assertThrows(IOException.class, () -> SafePaths.inspect(junction.resolve("out")));
        assertTrue(failure.getMessage().startsWith("path.link.disallowed"), failure.getMessage());
    }
}
