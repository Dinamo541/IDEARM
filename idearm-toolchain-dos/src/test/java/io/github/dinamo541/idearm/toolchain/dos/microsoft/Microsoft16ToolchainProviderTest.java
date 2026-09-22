package io.github.dinamo541.idearm.toolchain.dos.microsoft;

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

class Microsoft16ToolchainProviderTest {

    private final Microsoft16ToolchainProvider provider = new Microsoft16ToolchainProvider();

    @Test
    void declaresCorrectIdAndTargetSupport() {
        assertEquals("microsoft-masm", provider.id());
        var supports = provider.supports();
        assertTrue(supports.contains(new TargetSupport(16, "OMF", "MZ", "DOS")));
        assertNotNull(provider.assembler());
        assertNotNull(provider.linker());
    }

    @Test
    void isDiscoverableViaServiceLoader() {
        var providers = ServiceLoader.load(ToolchainProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> p.id().equals("microsoft-masm"))
                .toList();
        assertEquals(1, providers.size(), "Microsoft16ToolchainProvider should be registered via SPI");
    }

    @Test
    void resolvesSuccessfullyWithValidRegistry(@TempDir Path tempDir) throws IOException {
        Path mlExe = Files.createFile(tempDir.resolve("ML.EXE"));
        Files.createFile(tempDir.resolve("DOSXNT.EXE"));
        Path linkExe = Files.createFile(tempDir.resolve("LINK.EXE"));
        Path dbxExe = Files.createFile(tempDir.resolve("DOSBOX.EXE"));

        ToolRegistry registry = id -> switch (id) {
            case "ml" -> Optional.of(new ToolInstallation("ml", "6.11", mlExe, HostKind.WIN32_CONSOLE, Map.of(), null, "test"));
            case "link" -> Optional.of(new ToolInstallation("link", "5.31", linkExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "dosbox" -> Optional.of(new ToolInstallation("dosbox", "0.74-3", dbxExe, HostKind.WIN64, Map.of(), null, "test"));
            default -> Optional.empty();
        };

        Project project = sampleProject("microsoft-masm", ">=6.11");
        ResolvedToolchain resolved = provider.resolve(project, registry);

        assertNotNull(resolved);
        assertEquals("microsoft-masm", resolved.providerId());
        assertEquals(mlExe, resolved.tools().get("ml").executable());
        assertEquals(linkExe, resolved.tools().get("link").executable());
        assertEquals(dbxExe, resolved.environment().executable());
    }

    @Test
    void rejectsMismatchedToolchainId() {
        Project project = sampleProject("borland-tasm", ">=3.2");
        assertThrows(DomainException.class, () -> provider.resolve(project, id -> Optional.empty()));
    }

    @Test
    void rejectsMissingTools(@TempDir Path tempDir) throws IOException {
        Path mlExe = Files.createFile(tempDir.resolve("ML.EXE"));
        ToolRegistry registry = id -> id.equals("ml")
                ? Optional.of(new ToolInstallation("ml", "6.11", mlExe, HostKind.WIN32_CONSOLE, Map.of(), null, "test"))
                : Optional.empty();

        Project project = sampleProject("microsoft-masm", ">=6.11");
        assertThrows(DomainException.class, () -> provider.resolve(project, registry));
    }

    @Test
    void rejectsIncompatibleHostKinds(@TempDir Path tempDir) throws IOException {
        Path mlExe = Files.createFile(tempDir.resolve("ML.EXE"));
        Path linkExe = Files.createFile(tempDir.resolve("LINK.EXE"));
        Path dbxExe = Files.createFile(tempDir.resolve("DOSBOX.EXE"));

        // ML must not be DOS_REAL (it is Win32)
        ToolRegistry registry = id -> switch (id) {
            case "ml" -> Optional.of(new ToolInstallation("ml", "6.11", mlExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "link" -> Optional.of(new ToolInstallation("link", "5.31", linkExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "dosbox" -> Optional.of(new ToolInstallation("dosbox", "0.74-3", dbxExe, HostKind.WIN64, Map.of(), null, "test"));
            default -> Optional.empty();
        };

        Project project = sampleProject("microsoft-masm", ">=6.11");
        assertThrows(DomainException.class, () -> provider.resolve(project, registry));
    }

    @Test
    void honoursTheProjectsDialectPreference(@TempDir Path tempDir) throws IOException {
        Path mlExe = Files.createFile(tempDir.resolve("ML.EXE"));
        Files.createFile(tempDir.resolve("DOSXNT.EXE"));
        Path linkExe = Files.createFile(tempDir.resolve("LINK.EXE"));
        Path classicExe = Files.createFile(tempDir.resolve("dosbox.exe"));
        Path autoExe = Files.createFile(tempDir.resolve("dosbox-x.exe"));

        ToolRegistry registry = id -> switch (id) {
            case "ml" -> Optional.of(new ToolInstallation("ml", "6.11", mlExe, HostKind.WIN32_CONSOLE, Map.of(), null, "test"));
            case "link" -> Optional.of(new ToolInstallation("link", "5.31", linkExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "dosbox-0.74" -> Optional.of(new ToolInstallation("dosbox-0.74", "0.74", classicExe, HostKind.WIN64, Map.of(), null, "test"));
            case "dosbox" -> Optional.of(new ToolInstallation("dosbox-x", "auto", autoExe, HostKind.WIN64, Map.of(), null, "test"));
            default -> Optional.empty();
        };

        ResolvedToolchain resolved = provider.resolve(sampleProject("microsoft-masm", ">=6.11", "dosbox-0.74"), registry);

        assertEquals("dosbox-0.74", resolved.environment().toolId(), "The build must use the selected dialect");
        assertEquals(classicExe, resolved.environment().executable());
    }

    @Test
    void fallsBackToGenericDosboxWhenPreferredDialectMissing(@TempDir Path tempDir) throws IOException {
        Path mlExe = Files.createFile(tempDir.resolve("ML.EXE"));
        Files.createFile(tempDir.resolve("DOSXNT.EXE"));
        Path linkExe = Files.createFile(tempDir.resolve("LINK.EXE"));
        Path autoExe = Files.createFile(tempDir.resolve("dosbox-x.exe"));

        // The preferred "dosbox-0.74" is not installed; only the generic auto-pick is.
        ToolRegistry registry = id -> switch (id) {
            case "ml" -> Optional.of(new ToolInstallation("ml", "6.11", mlExe, HostKind.WIN32_CONSOLE, Map.of(), null, "test"));
            case "link" -> Optional.of(new ToolInstallation("link", "5.31", linkExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "dosbox" -> Optional.of(new ToolInstallation("dosbox-x", "auto", autoExe, HostKind.WIN64, Map.of(), null, "test"));
            default -> Optional.empty();
        };

        ResolvedToolchain resolved = provider.resolve(sampleProject("microsoft-masm", ">=6.11", "dosbox-0.74"), registry);

        assertEquals(autoExe, resolved.environment().executable(), "A missing preferred dialect falls back to the auto-pick");
    }

    @Test
    void stillValidatesTheChosenDosboxExecutableExists(@TempDir Path tempDir) throws IOException {
        Path mlExe = Files.createFile(tempDir.resolve("ML.EXE"));
        Files.createFile(tempDir.resolve("DOSXNT.EXE"));
        Path linkExe = Files.createFile(tempDir.resolve("LINK.EXE"));
        Path ghost = tempDir.resolve("gone").resolve("dosbox.exe"); // never created

        ToolRegistry registry = id -> switch (id) {
            case "ml" -> Optional.of(new ToolInstallation("ml", "6.11", mlExe, HostKind.WIN32_CONSOLE, Map.of(), null, "test"));
            case "link" -> Optional.of(new ToolInstallation("link", "5.31", linkExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "dosbox-0.74" -> Optional.of(new ToolInstallation("dosbox-0.74", "0.74", ghost, HostKind.WIN64, Map.of(), null, "test"));
            default -> Optional.empty();
        };

        DomainException ex = assertThrows(DomainException.class,
                () -> provider.resolve(sampleProject("microsoft-masm", ">=6.11", "dosbox-0.74"), registry));
        assertEquals("toolchain.invalid-path", ex.code());
    }

    /** Where Win32 programs cannot run (Linux), ML runs inside DOSBox with the DOS extender beside it. */
    @Test
    void mlRunsInsideDosBoxWithItsExtender(@TempDir Path tempDir) throws IOException {
        Path mlExe = Files.createFile(tempDir.resolve("ML.EXE"));
        Path extender = Files.createFile(tempDir.resolve("DOSXNT.EXE"));
        Path linkExe = Files.createFile(tempDir.resolve("LINK.EXE"));
        Path dbxExe = Files.createFile(tempDir.resolve("DOSBOX.EXE"));
        ToolRegistry registry = masm(mlExe, linkExe, dbxExe);

        ResolvedToolchain resolved = insideDosBox(() -> provider.resolve(sampleProject("microsoft-masm", ">=6.11"), registry));

        assertEquals(HostKind.DOS_DPMI, resolved.tools().get("ml").hostKind());
        assertEquals(extender, resolved.tools().get("ml").companions().get("DOSXNT.EXE"));
    }

    @Test
    void mlInsideDosBoxNeedsItsExtender(@TempDir Path tempDir) throws IOException {
        Path mlExe = Files.createFile(tempDir.resolve("ML.EXE"));
        Path linkExe = Files.createFile(tempDir.resolve("LINK.EXE"));
        Path dbxExe = Files.createFile(tempDir.resolve("DOSBOX.EXE"));
        ToolRegistry registry = masm(mlExe, linkExe, dbxExe);

        DomainException ex = assertThrows(DomainException.class, () -> insideDosBox(
                () -> provider.resolve(sampleProject("microsoft-masm", ">=6.11"), registry)));
        assertEquals("toolchain.missing-companion", ex.code());
        // The message names the program that needs the file: "ML needs DOSXNT.EXE", not TLINK.
        assertEquals(List.of("DOSXNT.EXE", "ML"), ex.arguments());
    }

    private static ToolRegistry masm(Path mlExe, Path linkExe, Path dbxExe) {
        return id -> switch (id) {
            case "ml" -> Optional.of(new ToolInstallation("ml", "6.11", mlExe, HostKind.WIN32_CONSOLE, Map.of(), null, "test"));
            case "link" -> Optional.of(new ToolInstallation("link", "5.31", linkExe, HostKind.DOS_REAL, Map.of(), null, "test"));
            case "dosbox" -> Optional.of(new ToolInstallation("dosbox", "0.74-3", dbxExe, HostKind.WIN64, Map.of(), null, "test"));
            default -> Optional.empty();
        };
    }

    private static <T> T insideDosBox(java.util.function.Supplier<T> work) {
        String previous = System.setProperty(Microsoft16ToolchainProvider.INSIDE_DOSBOX_PROPERTY, "true");
        try {
            return work.get();
        } finally {
            if (previous == null) {
                System.clearProperty(Microsoft16ToolchainProvider.INSIDE_DOSBOX_PROPERTY);
            } else {
                System.setProperty(Microsoft16ToolchainProvider.INSIDE_DOSBOX_PROPERTY, previous);
            }
        }
    }

    private static Project sampleProject(String toolchain, String version) {
        return sampleProject(toolchain, version, "dosbox");
    }

    private static Project sampleProject(String toolchain, String version, String environment) {
        return new Project(
                1,
                new ProjectInfo("APP", "1.0.0"),
                new TargetSelection("dos-exe-16", "8086"),
                new ToolchainSelection(toolchain, version),
                new Sources("src/MAIN.ASM", List.of(), List.of(), List.of()),
                new Resources(List.of()),
                Map.of("release", BuildConfiguration.release()),
                new RunConfiguration(environment, "required", true, "auto", 16, List.of()),
                new DebugConfiguration("external"),
                new DistConfiguration(true, false)
        );
    }
}
