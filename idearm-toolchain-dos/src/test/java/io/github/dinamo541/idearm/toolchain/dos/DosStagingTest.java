package io.github.dinamo541.idearm.toolchain.dos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DosStagingTest {

    @TempDir
    Path temp;

    @Test
    void buildSessionsLockTheDrivesAfterMountingThem(@org.junit.jupiter.api.io.TempDir Path session) throws IOException {
        for (DosBoxDialect dialect : DosBoxDialect.values()) {
            String conf = Files.readString(DosStaging.writeConfiguration(session, dialect));
            int lastMount = conf.lastIndexOf("mount C");
            int secure = conf.indexOf(DosBoxDialect.SECURE_MODE);
            assertTrue(lastMount >= 0 && secure > lastMount && secure < conf.indexOf("build.bat"), dialect + ": " + conf);
            assertTrue(conf.contains("nosound=true"), dialect + ": a build needs no sound device");
        }
    }

    @Test
    void anIncludeFolderMayBeTheSourceFolder() throws IOException {
        Path project = Files.createDirectories(temp.resolve("project"));
        Files.createDirectories(project.resolve("src"));
        Files.writeString(project.resolve("src/main.asm"), "INCLUDE common.inc");
        Files.writeString(project.resolve("src/common.inc"), "DOS_EXIT EQU 4Ch");
        Path driveS = Files.createDirectories(temp.resolve("staging/S"));

        DosStaging.copySources(project, driveS, List.of("src/main.asm"));
        DosStaging.copyIncludes(project, driveS, List.of("src"));

        // DOS sees upper-case names inside the emulator; the staging copy follows that convention.
        assertTrue(Files.isRegularFile(driveS.resolve("SRC/MAIN.ASM")));
        assertEquals("DOS_EXIT EQU 4Ch", Files.readString(driveS.resolve("SRC/COMMON.INC")));
    }

    /**
     * A resolved INCLUDE is staged as one more source file, under the folder it has in the project, so the /i the
     * assembler receives points at the same place the IDE resolved the file from.
     */
    @Test
    void includedFilesAreStagedUnderTheirOwnProjectFolder() throws IOException {
        Path project = Files.createDirectories(temp.resolve("project"));
        Files.createDirectories(project.resolve("src"));
        Files.createDirectories(project.resolve("sprite"));
        Files.writeString(project.resolve("src/main.asm"), "INCLUDE manzana.inc");
        Files.writeString(project.resolve("sprite/manzana.inc"), "MANZANA DB 'x'");
        Path driveS = Files.createDirectories(temp.resolve("staging/S"));

        DosStaging.copySources(project, driveS, List.of("src/main.asm", "sprite/manzana.inc"));

        assertTrue(Files.isRegularFile(driveS.resolve("SRC/MAIN.ASM")));
        assertEquals("MANZANA DB 'x'", Files.readString(driveS.resolve("SPRITE/MANZANA.INC")));
    }
}
