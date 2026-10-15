package io.github.dinamo541.idearm.infrastructure.tools;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultToolRegistryTest {

    @TempDir
    Path temp;

    @Test
    void aRegisteredFolderIsRememberedWithItsRoles() throws IOException {
        Path tasm = Files.createDirectories(temp.resolve("tasm/bin"));
        Files.writeString(tasm.resolve("TASM.EXE"), "MZ Turbo Assembler Version 4.1 ");
        Files.writeString(tasm.resolve("TD.EXE"), "MZ");
        Path config = temp.resolve("config/tools.toml");

        var registry = new DefaultToolRegistry(false, config);
        List<ToolInstallation> added = registry.registerFolder(temp.resolve("tasm"));

        assertEquals(2, added.size(), "TD is registered once even though it fills two roles");
        assertEquals("4.1", registry.find("tasm").orElseThrow().version());
        assertTrue(Files.isRegularFile(config));

        var reloaded = new DefaultToolRegistry(false, config);
        reloaded.loadFromGlobalConfig();
        assertEquals(tasm.resolve("TASM.EXE"), reloaded.find("tasm").orElseThrow().executable());
        assertEquals(tasm.resolve("TD.EXE"), reloaded.find("turbo-debugger").orElseThrow().executable());
    }

    @Test
    void anyDosBoxMeansTheClassicOneFirst() {
        var registry = new DefaultToolRegistry(false, null);
        var dosboxX = new ToolInstallation("dosbox-x", "2026.08.31", temp.resolve("dosbox-x.exe"), HostKind.WIN64, Map.of());
        var classic = new ToolInstallation("dosbox-0.74", "0.74-3", temp.resolve("dosbox.exe"), HostKind.WIN32_CONSOLE, Map.of());
        // Detection puts whatever it finds first in the "dosbox" role; the preference must still win.
        registry.register("dosbox", dosboxX);
        registry.register("dosbox-x", dosboxX);
        registry.register("dosbox-0.74", classic);

        assertEquals(classic, registry.find("dosbox").orElseThrow());
        assertEquals(classic, registry.all().get("dosbox"), "Tool Doctor shows the DOSBox the IDE will use");
        assertEquals(dosboxX, registry.find("dosbox-x").orElseThrow());
    }

    @Test
    void theClassicDosBoxAndStagingAreToldApartByTheirOwnText() throws IOException {
        Path classic = Files.write(temp.resolve("dosbox.exe"), ("MZ padding DOSBox 0.74-3 padding").getBytes());
        Path staging = Files.write(temp.resolve("staging.exe"), ("MZ padding dosbox-staging 0.82.2 padding").getBytes());

        assertEquals(new BinaryScanner.DosBoxIdentity("dosbox-0.74", "0.74-3"), BinaryScanner.identifyDosBox(classic).orElseThrow());
        assertEquals(new BinaryScanner.DosBoxIdentity("dosbox-staging", "0.82.2"), BinaryScanner.identifyDosBox(staging).orElseThrow());
    }

    @Test
    void detectedToolsAreNotWrittenDown() throws IOException {
        Path config = temp.resolve("tools.toml");
        var registry = new DefaultToolRegistry(false, config);
        registry.register(new ToolInstallation("nasm", "3.01", temp.resolve("nasm.exe"), HostKind.WIN64, Map.of(),
                null, "detected"));
        registry.register(new ToolInstallation("tasm", "4.1", temp.resolve("TASM.EXE"), HostKind.DOS_REAL, Map.of()));

        registry.saveToGlobalConfig();

        String saved = Files.readString(config);
        assertTrue(saved.contains("tasm"));
        assertFalse(saved.contains("nasm"), saved);
        assertFalse(Files.exists(temp.resolve("tools.toml.tmp")));
    }

    @Test
    void aDamagedFileDoesNotStopTheRegistry() throws IOException {
        Path config = temp.resolve("tools.toml");
        Files.writeString(config, "tools = [ { id = \"tasm\", host = \"NOT_A_HOST\", executable = \"x\" }, broken");

        var registry = new DefaultToolRegistry(false, config);
        registry.loadFromGlobalConfig();

        assertTrue(registry.find("tasm").isEmpty());
    }

    @Test
    void aMissingFolderIsReported() {
        var registry = new DefaultToolRegistry(false, temp.resolve("tools.toml"));

        DomainException error = assertThrows(DomainException.class,
                () -> registry.registerFolder(temp.resolve("nowhere")));
        assertEquals("tools.folder.missing", error.code());
        assertEquals(List.of(temp.resolve("nowhere").toString()), error.arguments());
    }

    @Test
    void aFolderWithoutToolsRegistersNothingAndWritesNothing() throws IOException {
        Path config = temp.resolve("tools.toml");
        Files.createDirectories(temp.resolve("empty"));

        assertEquals(List.of(), new DefaultToolRegistry(false, config).registerFolder(temp.resolve("empty")));
        assertFalse(Files.exists(config));
    }

    @Test
    void fallsBackToDetectionWhenRegisteredExecutableNoLongerExists() throws IOException {
        Path existingExe = Files.createFile(temp.resolve("detected-tasm.exe"));
        Path missingExe = temp.resolve("old-tasm.exe"); // does not exist

        var registry = new DefaultToolRegistry(false, null);
        var registeredTool = new ToolInstallation("tasm", "4.1", missingExe, HostKind.DOS_REAL, Map.of(), null, DefaultToolRegistry.SOURCE_REGISTERED);
        var detectedTool = new ToolInstallation("tasm", "3.2", existingExe, HostKind.DOS_REAL, Map.of(), null, DefaultToolRegistry.SOURCE_DETECTED);

        registry.register("tasm", registeredTool);
        registry.register("tasm", detectedTool);

        // find("tasm") falls back to detected tool
        ToolInstallation found = registry.find("tasm").orElseThrow();
        assertEquals(detectedTool, found, "Must fall back to detected tool when registered executable is missing");
        assertEquals(existingExe, found.executable());

        // all() also prefers the detected tool
        assertEquals(detectedTool, registry.all().get("tasm"));
    }

    @Test
    void refreshClearsDetectedCacheAndReloadsConfig() throws IOException {
        Path config = temp.resolve("tools.toml");
        Path exe1 = Files.createFile(temp.resolve("tasm1.exe"));
        Path exe2 = Files.createFile(temp.resolve("tasm2.exe"));

        // Save config with tasm1
        var reg1 = new DefaultToolRegistry(false, config);
        reg1.register(new ToolInstallation("tasm", "4.1", exe1, HostKind.DOS_REAL, Map.of()));
        reg1.saveToGlobalConfig();

        var registry = new DefaultToolRegistry(false, config);
        registry.loadFromGlobalConfig();
        assertEquals(exe1, registry.find("tasm").orElseThrow().executable());

        // Now update config externally with tasm2
        var reg2 = new DefaultToolRegistry(false, config);
        reg2.register(new ToolInstallation("tasm", "4.1", exe2, HostKind.DOS_REAL, Map.of()));
        reg2.saveToGlobalConfig();

        // Before refresh, still has old
        assertEquals(exe1, registry.find("tasm").orElseThrow().executable());

        // After refresh, reloads new config
        registry.refresh();
        assertEquals(exe2, registry.find("tasm").orElseThrow().executable());
    }
}
