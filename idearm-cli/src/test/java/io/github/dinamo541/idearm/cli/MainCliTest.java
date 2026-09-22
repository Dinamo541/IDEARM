package io.github.dinamo541.idearm.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MainCliTest {

    @Test
    void printsHelpWhenNoArgsOrHelpFlag() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code1 = Main.run(new String[]{}, new PrintStream(out), new PrintStream(err));
        assertEquals(0, code1);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Usage:"));

        out.reset();
        int code2 = Main.run(new String[]{"--help"}, new PrintStream(out), new PrintStream(err));
        assertEquals(0, code2);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Usage:"));
    }

    @Test
    void printsVersion() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code = Main.run(new String[]{"--version"}, new PrintStream(out), new PrintStream(err));
        assertEquals(0, code);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("IDEARM"));
    }

    @Test
    void rejectsUnknownCommand() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code = Main.run(new String[]{"invalid-cmd"}, new PrintStream(out), new PrintStream(err));
        assertEquals(2, code);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Unknown command"));
    }

    @Test
    void rejectsBuildWhenProjectFileMissing() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code = Main.run(new String[]{"build", "non-existent-folder-12345"}, new PrintStream(out), new PrintStream(err));
        assertEquals(2, code);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("No idearm.toml found"));
    }

    @Test
    void rejectsRunWhenProjectFileMissing() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code = Main.run(new String[]{"run", "non-existent-folder-12345"}, new PrintStream(out), new PrintStream(err));
        assertEquals(2, code);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("No idearm.toml found"));
    }

    @Test
    void rejectsDistWhenProjectFileMissing() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code = Main.run(new String[]{"dist", "non-existent-folder-12345"}, new PrintStream(out), new PrintStream(err));
        assertEquals(2, code);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("No idearm.toml found"));
    }

    @Test
    void runsDoctorCommandSuccessfully() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code = Main.run(new String[]{"doctor"}, new PrintStream(out), new PrintStream(err));
        assertTrue(code == 0 || code == 1);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Tool Doctor"));
    }

    @Test
    void importsExistingProjectDirectory(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        var proj = java.nio.file.Files.createDirectory(tempDir.resolve("MyImportApp"));
        java.nio.file.Files.writeString(proj.resolve("MAIN.ASM"), ".model small\n.code\nmain proc\nret\nmain endp\nend main\n");

        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code = Main.run(new String[]{"import", proj.toString()}, new PrintStream(out), new PrintStream(err));
        assertEquals(0, code);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Successfully imported project"));
        assertTrue(java.nio.file.Files.exists(proj.resolve("idearm.toml")));
    }
}
