package io.github.dinamo541.idearm.toolchain.dos.borland;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.*;
import io.github.dinamo541.idearm.domain.port.ToolRegistry;
import io.github.dinamo541.idearm.domain.port.ToolchainProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

class BorlandToolchainProviderTest {

    private final BorlandToolchainProvider provider = new BorlandToolchainProvider();

    @Test
    void declaresCorrectIdAndTargetSupport() {
        assertEquals("borland-tasm", provider.id());
        assertTrue(provider.supports().contains(new TargetSupport(16, "OMF", "MZ", "DOS")));
        assertNotNull(provider.assembler());
        assertNotNull(provider.linker());
    }

    @Test
    void isDiscoverableViaServiceLoader() {
        var providers = ServiceLoader.load(ToolchainProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> p.id().equals("borland-tasm"))
                .toList();
        assertEquals(1, providers.size(), "BorlandToolchainProvider should be registered via SPI");
    }

    @Test
    void resolvesSuccessfullyWithValidRegistry(@TempDir Path tempDir) throws IOException {
        Path tasmExe = Files.createFile(tempDir.resolve("TASM.EXE"));
        Path tlinkExe = Files.createFile(tempDir.resolve("TLINK.EXE"));
        Path dbxExe = Files.createFile(tempDir.resolve("dosbox.exe"));
        ToolRegistry registry = id -> switch (id) {
            case "tasm" -> Optional.of(new ToolInstallation("tasm", "4.1", tasmExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "tlink" -> Optional.of(new ToolInstallation("tlink", "7.1", tlinkExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "dosbox" -> Optional.of(new ToolInstallation("dosbox-0.74", "0.74", dbxExe, HostKind.WIN64, Map.of(), null, "test"));
            default -> Optional.empty();
        };

        ResolvedToolchain resolved = provider.resolve(sampleProject(">=3.2", "dosbox"), registry);

        assertEquals("borland-tasm", resolved.providerId());
        assertEquals(tasmExe, resolved.tools().get("tasm").executable());
        assertEquals(tlinkExe, resolved.tools().get("tlink").executable());
        assertEquals(dbxExe, resolved.environment().executable());
    }

    @Test
    void rejectsMismatchedToolchainId() {
        assertThrows(DomainException.class,
                () -> provider.resolve(sampleProject("microsoft-masm", ">=3.2", "dosbox"), id -> Optional.empty()));
    }

    @Test
    void rejectsMissingDosBox(@TempDir Path tempDir) throws IOException {
        Path tasmExe = Files.createFile(tempDir.resolve("TASM.EXE"));
        Path tlinkExe = Files.createFile(tempDir.resolve("TLINK.EXE"));
        ToolRegistry registry = id -> switch (id) {
            case "tasm" -> Optional.of(new ToolInstallation("tasm", "4.1", tasmExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "tlink" -> Optional.of(new ToolInstallation("tlink", "7.1", tlinkExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            default -> Optional.empty();
        };

        assertThrows(DomainException.class, () -> provider.resolve(sampleProject(">=3.2", "dosbox"), registry));
    }

    @Test
    void honoursTheProjectsDialectPreference(@TempDir Path tempDir) throws IOException {
        Path tasmExe = Files.createFile(tempDir.resolve("TASM.EXE"));
        Path tlinkExe = Files.createFile(tempDir.resolve("TLINK.EXE"));
        Path classicExe = Files.createFile(tempDir.resolve("dosbox.exe"));
        Path autoExe = Files.createFile(tempDir.resolve("dosbox-x.exe"));
        ToolRegistry registry = id -> switch (id) {
            case "tasm" -> Optional.of(new ToolInstallation("tasm", "4.1", tasmExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "tlink" -> Optional.of(new ToolInstallation("tlink", "7.1", tlinkExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "dosbox-0.74" -> Optional.of(new ToolInstallation("dosbox-0.74", "0.74", classicExe, HostKind.WIN64, Map.of(), null, "test"));
            case "dosbox" -> Optional.of(new ToolInstallation("dosbox-x", "auto", autoExe, HostKind.WIN64, Map.of(), null, "test"));
            default -> Optional.empty();
        };

        ResolvedToolchain resolved = provider.resolve(sampleProject(">=3.2", "dosbox-0.74"), registry);

        assertEquals("dosbox-0.74", resolved.environment().toolId(), "The build must use the selected dialect");
        assertEquals(classicExe, resolved.environment().executable());
    }

    @Test
    void fallsBackToGenericDosboxWhenPreferredDialectMissing(@TempDir Path tempDir) throws IOException {
        Path tasmExe = Files.createFile(tempDir.resolve("TASM.EXE"));
        Path tlinkExe = Files.createFile(tempDir.resolve("TLINK.EXE"));
        Path autoExe = Files.createFile(tempDir.resolve("dosbox-x.exe"));
        ToolRegistry registry = id -> switch (id) {
            case "tasm" -> Optional.of(new ToolInstallation("tasm", "4.1", tasmExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "tlink" -> Optional.of(new ToolInstallation("tlink", "7.1", tlinkExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "dosbox" -> Optional.of(new ToolInstallation("dosbox-x", "auto", autoExe, HostKind.WIN64, Map.of(), null, "test"));
            default -> Optional.empty();
        };

        ResolvedToolchain resolved = provider.resolve(sampleProject(">=3.2", "dosbox-0.74"), registry);

        assertEquals(autoExe, resolved.environment().executable(), "A missing preferred dialect falls back to the auto-pick");
    }

    @Test
    void stillValidatesTheChosenDosboxExecutableExists(@TempDir Path tempDir) throws IOException {
        Path tasmExe = Files.createFile(tempDir.resolve("TASM.EXE"));
        Path tlinkExe = Files.createFile(tempDir.resolve("TLINK.EXE"));
        Path ghost = tempDir.resolve("gone").resolve("dosbox.exe"); // never created
        ToolRegistry registry = id -> switch (id) {
            case "tasm" -> Optional.of(new ToolInstallation("tasm", "4.1", tasmExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "tlink" -> Optional.of(new ToolInstallation("tlink", "7.1", tlinkExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "dosbox-0.74" -> Optional.of(new ToolInstallation("dosbox-0.74", "0.74", ghost, HostKind.WIN64, Map.of(), null, "test"));
            default -> Optional.empty();
        };

        DomainException ex = assertThrows(DomainException.class,
                () -> provider.resolve(sampleProject(">=3.2", "dosbox-0.74"), registry));
        assertEquals("toolchain.invalid-path", ex.code());
    }

    /** TLINK 4.x and later are DPMI programs: without RTM.EXE and DPMI16BI.OVL they cannot start. */
    @Test
    void aDpmiTlinkWithoutItsRuntimeNamesTheMissingFile(@TempDir Path tempDir) throws IOException {
        Path tasmExe = Files.createFile(tempDir.resolve("TASM.EXE"));
        Path tlinkExe = Files.createFile(tempDir.resolve("TLINK.EXE"));
        Path dbxExe = Files.createFile(tempDir.resolve("dosbox.exe"));
        ToolRegistry registry = id -> switch (id) {
            case "tasm" -> Optional.of(new ToolInstallation("tasm", "4.1", tasmExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "tlink" -> Optional.of(new ToolInstallation("tlink", "7.1", tlinkExe, HostKind.DOS_DPMI, Map.of(), null, "test"));
            case "dosbox" -> Optional.of(new ToolInstallation("dosbox-0.74", "0.74", dbxExe, HostKind.WIN64, Map.of(), null, "test"));
            default -> Optional.empty();
        };

        DomainException ex = assertThrows(DomainException.class,
                () -> provider.resolve(sampleProject(">=3.2", "dosbox"), registry));

        assertEquals("toolchain.missing-companion", ex.code());
        assertTrue(List.of("RTM.EXE", "DPMI16BI.OVL").contains(ex.arguments().getFirst()), ex.arguments().toString());
        assertEquals("TLINK", ex.arguments().get(1));
    }

    private static Project sampleProject(String version, String environment) {
        return sampleProject("borland-tasm", version, environment);
    }

    private static Project sampleProject(String toolchainId, String version, String environment) {
        return new Project(
                1,
                new ProjectInfo("APP", "1.0.0"),
                new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection(toolchainId, version),
                new Sources("src/MAIN.ASM", List.of(), List.of(), List.of()),
                new Resources(List.of()),
                Map.of("release", BuildConfiguration.release()),
                new RunConfiguration(environment, "required", true, "auto", 16, List.of()),
                new DebugConfiguration("external"),
                new DistConfiguration(true, false)
        );
    }
}
