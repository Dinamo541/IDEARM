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

    @Test
    void createsNewProjectWithDefaults(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        var proj = tempDir.resolve("DefaultApp");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code = Main.run(new String[]{"new", proj.toString()}, new PrintStream(out), new PrintStream(err));
        assertEquals(0, code);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Successfully created project: DefaultApp"));
        assertTrue(java.nio.file.Files.exists(proj.resolve("idearm.toml")));
        assertTrue(java.nio.file.Files.exists(proj.resolve("src").resolve("main.asm")));
        assertTrue(java.nio.file.Files.exists(proj.resolve(".gitignore")));

        String toml = java.nio.file.Files.readString(proj.resolve("idearm.toml"));
        assertTrue(toml.contains("dos-exe-16"));
        assertTrue(toml.contains("8086"));
        assertTrue(toml.contains("borland-tasm"));
    }

    @Test
    void createsNewProjectWithTargetDerivedDefaultsAndCustomName(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        var proj = tempDir.resolve("win64-folder");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code = Main.run(new String[]{
                "new", proj.toString(),
                "--name", "CustomWinApp",
                "--target", "win-pe64-console"
        }, new PrintStream(out), new PrintStream(err));

        assertEquals(0, code);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Successfully created project: CustomWinApp"));
        assertTrue(java.nio.file.Files.exists(proj.resolve("idearm.toml")));

        String toml = java.nio.file.Files.readString(proj.resolve("idearm.toml"));
        assertTrue(toml.contains("CustomWinApp"));
        assertTrue(toml.contains("win-pe64-console"));
        assertTrue(toml.contains("x86-64"));
        assertTrue(toml.contains("nasm"));

        String asm = java.nio.file.Files.readString(proj.resolve("src").resolve("main.asm"));
        assertTrue(asm.contains("default rel"));
    }

    @Test
    void createsNewProjectWithExplicitToolchainAndCpu(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws java.io.IOException {
        var proj = tempDir.resolve("MasmApp");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code = Main.run(new String[]{
                "new", proj.toString(),
                "--target", "dos-exe-16",
                "--cpu", "80286",
                "--toolchain", "microsoft-masm"
        }, new PrintStream(out), new PrintStream(err));

        assertEquals(0, code);
        String toml = java.nio.file.Files.readString(proj.resolve("idearm.toml"));
        assertTrue(toml.contains("80286"));
        assertTrue(toml.contains("microsoft-masm"));
    }

    @Test
    void createsNewProjectWithJsonOutput(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        var proj = tempDir.resolve("JsonNativeApp");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code = Main.run(new String[]{
                "new", proj.toString(),
                "--target", "win-pe64-console",
                "--json"
        }, new PrintStream(out), new PrintStream(err));

        assertEquals(0, code);
        String jsonText = out.toString(StandardCharsets.UTF_8).trim();

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = mapper.readTree(jsonText);
        assertEquals("CREATED", root.get("status").asText());
        assertEquals("JsonNativeApp", root.get("project").asText());
        assertEquals("win-pe64-console", root.get("target").asText());
        assertEquals("x86-64", root.get("cpu").asText());
        assertEquals("nasm", root.get("toolchain").asText());
        assertEquals("gdb", root.get("debugBackend").asText());
    }

    @Test
    void rejectsNewProjectWhenFolderMissing() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code = Main.run(new String[]{"new"}, new PrintStream(out), new PrintStream(err));
        assertEquals(2, code);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Project folder must be specified"));

        out.reset();
        err.reset();
        int codeJson = Main.run(new String[]{"new", "--json"}, new PrintStream(out), new PrintStream(err));
        assertEquals(2, codeJson);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("\"status\":\"FAILED\""));
    }

    @Test
    void rejectsNewProjectWhenAlreadyExists(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) {
        var proj = tempDir.resolve("ExistingApp");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code1 = Main.run(new String[]{"new", proj.toString()}, new PrintStream(out), new PrintStream(err));
        assertEquals(0, code1);

        out.reset();
        err.reset();
        int code2 = Main.run(new String[]{"new", proj.toString()}, new PrintStream(out), new PrintStream(err));
        assertEquals(1, code2);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Project already exists"));

        out.reset();
        err.reset();
        int code3 = Main.run(new String[]{"new", proj.toString(), "--json"}, new PrintStream(out), new PrintStream(err));
        assertEquals(1, code3);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("\"status\":\"FAILED\""));
    }

    @Test
    void buildRejectsMissingProjectWithValidJsonOutput() throws Exception {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        int code = Main.run(new String[]{"build", "non-existent-dir-9999", "--json"}, new PrintStream(out), new PrintStream(err));
        assertEquals(2, code);

        String jsonText = out.toString(StandardCharsets.UTF_8).trim();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = mapper.readTree(jsonText);
        assertEquals("FAILED", root.get("status").asText());
        assertTrue(root.has("diagnostics"));
        assertTrue(root.get("diagnostics").isArray());
        assertEquals("ERROR", root.get("diagnostics").get(0).get("severity").asText());
    }

    @Test
    void buildDefaultsToDebugConfigurationAndAcceptsReleaseFlag(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        var proj = tempDir.resolve("BuildApp");
        Main.run(new String[]{"new", proj.toString()}, new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));

        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        Main.run(new String[]{"build", proj.toString(), "--json"}, new PrintStream(out), new PrintStream(err));

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var debugRoot = mapper.readTree(out.toString(StandardCharsets.UTF_8).trim());
        assertEquals("debug", debugRoot.get("configuration").asText());

        out.reset();
        err.reset();
        Main.run(new String[]{"build", proj.toString(), "--release", "--json"}, new PrintStream(out), new PrintStream(err));
        var releaseRoot = mapper.readTree(out.toString(StandardCharsets.UTF_8).trim());
        assertEquals("release", releaseRoot.get("configuration").asText());
    }
}
