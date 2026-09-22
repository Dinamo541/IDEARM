package io.github.dinamo541.idearm.infrastructure.tools;

import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolDetectorTest {

    @Test
    void detectsTurboDebuggerAndCompanions(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("TD.EXE"), "MZfake");
        Files.writeString(tempDir.resolve("TDHELP.TDH"), "fake help");
        Files.writeString(tempDir.resolve("TDMEM.EXE"), "fake mem");

        Map<String, List<ToolInstallation>> detected = ToolDetector.detectInDirectory(tempDir);
        assertTrue(detected.containsKey("td"));
        assertTrue(detected.containsKey("turbo-debugger"));

        ToolInstallation td = detected.get("td").getFirst();
        assertEquals("td", td.toolId());
        assertEquals(tempDir.resolve("TD.EXE"), td.executable());
        assertTrue(td.companions().containsKey("TDHELP.TDH"));
        assertTrue(td.companions().containsKey("TDMEM.EXE"));
    }

    @Test
    void detectsCodeViewAndCompanions(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("CV.EXE"), "MZfake");
        Files.writeString(tempDir.resolve("CVPACK.EXE"), "fake cvpack");

        Map<String, List<ToolInstallation>> detected = ToolDetector.detectInDirectory(tempDir);
        assertTrue(detected.containsKey("cv"));
        assertTrue(detected.containsKey("codeview"));

        ToolInstallation cv = detected.get("cv").getFirst();
        assertEquals("cv", cv.toolId());
        assertEquals(tempDir.resolve("CV.EXE"), cv.executable());
        assertTrue(cv.companions().containsKey("CVPACK.EXE"));
    }

    @Test
    void readsTheVersionFromTheFirstLineToolsPrint() {
        assertEquals("3.01", ToolDetector.parseVersion("NASM version 3.01 compiled on Dec  3 2025").orElseThrow());
        assertEquals("2.46", ToolDetector.parseVersion("GNU ld (GNU Binutils) 2.46").orElseThrow());
        assertEquals("17.2", ToolDetector.parseVersion("GNU gdb (GDB) 17.2").orElseThrow());
        assertEquals("16.1.0", ToolDetector.parseVersion("gcc.exe (Rev5, Built by MSYS2 project) 16.1.0").orElseThrow());
        assertTrue(ToolDetector.parseVersion("usage: tool [options]").isEmpty());
    }

    @Test
    void findsToolsBelowAPickedFolder(@TempDir Path tempDir) throws IOException {
        Path bin = Files.createDirectories(tempDir.resolve("TASM/BIN"));
        Files.writeString(bin.resolve("TASM.EXE"), "MZ Turbo Assembler Version 3.2 ");

        assertTrue(ToolDetector.detectInDirectory(tempDir).isEmpty(), "Only the folder itself is searched");
        ToolInstallation tasm = ToolDetector.detectUnder(tempDir).get("tasm").getFirst();
        assertEquals("3.2", tasm.version());
        assertEquals(bin.resolve("TASM.EXE"), tasm.executable());
    }

    @Test
    void aDosBoxWhoseVersionCannotBeReadIsNotGivenOne(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("dosbox-x.exe"), "MZ no version text here");

        Map<String, List<ToolInstallation>> detected = ToolDetector.detectInDirectory(tempDir);

        assertEquals(ToolDetector.UNKNOWN_VERSION, detected.get("dosbox-x").getFirst().version());
        assertEquals(detected.get("dosbox-x"), detected.get("dosbox"));
    }

    @Test
    void classicDosBoxGetsItsOwnDialectKeyNotJustTheGenericAlias(@TempDir Path tempDir) throws IOException {
        // Classic 0.74 ships as dosbox.exe; it must still be selectable by a distinct id, not only fill "dosbox".
        Path dir = Files.createDirectories(tempDir.resolve("DOSBox-0.74-3"));
        Files.writeString(dir.resolve("dosbox.exe"), "MZ no version text");

        Map<String, List<ToolInstallation>> detected = ToolDetector.detectInDirectory(dir);

        assertTrue(detected.containsKey("dosbox-0.74"), "Classic 0.74 must be selectable by its own id");
        assertEquals("dosbox-0.74", detected.get("dosbox-0.74").getFirst().toolId());
        assertEquals(detected.get("dosbox-0.74"), detected.get("dosbox"), "It also fills the generic auto-pick alias");
    }

    @Test
    void dosBoxStagingIsRecognizedByItsFolderEvenThoughItsBinaryIsNamedDosbox(@TempDir Path tempDir) throws IOException {
        // Staging's binary is also named dosbox.exe; only the install folder tells it apart from classic 0.74.
        Path dir = Files.createDirectories(tempDir.resolve("DOSBox Staging"));
        Files.writeString(dir.resolve("dosbox.exe"), "MZ no version text");

        Map<String, List<ToolInstallation>> detected = ToolDetector.detectInDirectory(dir);

        assertTrue(detected.containsKey("dosbox-staging"), "A dosbox.exe under a Staging folder is Staging");
        assertFalse(detected.containsKey("dosbox-0.74"), "It must not be mistaken for the classic 0.74");
        assertEquals("dosbox-staging", detected.get("dosbox-staging").getFirst().toolId());
    }

    @Test
    void aFileNamedLikeANativeToolThatIsNotAProgramIsIgnored(@TempDir Path tempDir) throws IOException {
        // A text file cannot be asked for its version; running it would fail or, worse, do something else.
        Files.writeString(tempDir.resolve("nasm.exe"), "MZ");

        assertFalse(ToolDetector.detectInDirectory(tempDir).containsKey("nasm"));
    }

    @Test
    void searchingBelowAFolderStaysBounded(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("a/b/c/d/e/f"));

        List<Path> folders = ToolDetector.foldersUnder(tempDir, ToolDetector.PICKED_FOLDER_DEPTH);

        assertEquals(tempDir, folders.getFirst());
        assertFalse(folders.contains(tempDir.resolve("a/b/c/d")));
        assertTrue(ToolDetector.foldersUnder(tempDir.resolve("missing"), 2).isEmpty());
    }
}
