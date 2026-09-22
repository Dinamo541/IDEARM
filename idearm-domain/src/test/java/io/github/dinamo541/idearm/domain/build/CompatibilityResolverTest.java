package io.github.dinamo541.idearm.domain.build;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.*;
import io.github.dinamo541.idearm.domain.port.*;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityResolverTest {

    private final CompatibilityResolver resolver = new CompatibilityResolver();

    private final ToolchainProvider mockProvider = new ToolchainProvider() {
        @Override public String id() { return "borland-tasm"; }
        @Override public Set<TargetSupport> supports() { return Set.of(new TargetSupport(16, "OMF", "MZ", "DOS")); }
        @Override public AssemblerAdapter assembler() { return null; }
        @Override public LinkerAdapter linker() { return null; }
        @Override public ResolvedToolchain resolve(Project project, ToolRegistry registry) { return null; }
    };

    @Test
    void resolvesValidDosExeProfile() {
        TargetSelection selection = new TargetSelection("dos-exe-16", "8086");
        TargetProfile profile = resolver.resolve(selection, mockProvider);
        assertNotNull(profile);
        assertEquals("dos-exe-16", profile.id());
        assertEquals("8086", profile.cpuBaseline());
        assertEquals(16, profile.codeMode());
        assertEquals("DOS", profile.platform());
        assertEquals("MZ", profile.executableFormat());
    }

    @Test
    void acceptsOtherCompatibleCpusFor16Bit() {
        TargetSelection selection = new TargetSelection("dos-exe-16", "80386");
        TargetProfile profile = resolver.resolve(selection, mockProvider);
        assertEquals("80386", profile.cpuBaseline());
    }

    @Test
    void rejectsUnsupportedToolchainTarget() {
        ToolchainProvider unsupportedProvider = new ToolchainProvider() {
            @Override public String id() { return "linux-nasm"; }
            @Override public Set<TargetSupport> supports() { return Set.of(new TargetSupport(64, "ELF", "ELF", "Linux")); }
            @Override public AssemblerAdapter assembler() { return null; }
            @Override public LinkerAdapter linker() { return null; }
            @Override public ResolvedToolchain resolve(Project project, ToolRegistry registry) { return null; }
        };

        TargetSelection selection = new TargetSelection("dos-exe-16", "8086");
        assertThrows(DomainException.class, () -> resolver.resolve(selection, unsupportedProvider));
    }

    @Test
    void rejectsInvalidCpuName() {
        TargetSelection selection = new TargetSelection("dos-exe-16", "arm64");
        assertThrows(DomainException.class, () -> resolver.resolve(selection, mockProvider));
    }
}
