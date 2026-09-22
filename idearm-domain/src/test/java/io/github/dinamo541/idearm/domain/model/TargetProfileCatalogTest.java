package io.github.dinamo541.idearm.domain.model;

import io.github.dinamo541.idearm.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TargetProfileCatalogTest {

    @Test
    void catalogContainsDosExe16() {
        TargetProfile profile = TargetProfileCatalog.require("dos-exe-16");
        assertNotNull(profile);
        assertEquals("x86", profile.architecture());
        assertEquals("8086", profile.cpuBaseline());
        assertEquals(16, profile.codeMode());
        assertEquals("real", profile.processorMode());
        assertEquals("DOS", profile.platform());
        assertEquals("MZ", profile.executableFormat());
        assertEquals("small", profile.memoryModel());
        assertEquals("OMF", profile.objectFormat());
        assertEquals(new TargetSupport(16, "OMF", "MZ", "DOS"), profile.support());
    }

    @Test
    void catalogContains32And64BitProfiles() {
        TargetProfile pe64 = TargetProfileCatalog.require("win-pe64-console");
        assertEquals(64, pe64.codeMode());
        assertEquals("Windows", pe64.platform());
        assertEquals("PE32+", pe64.executableFormat());
        assertEquals("COFF", pe64.objectFormat());

        TargetProfile pe32 = TargetProfileCatalog.require("win-pe32-console");
        assertEquals(32, pe32.codeMode());
        assertEquals("Windows", pe32.platform());
        assertEquals("PE32", pe32.executableFormat());

        TargetProfile elf64 = TargetProfileCatalog.require("linux-elf64");
        assertEquals(64, elf64.codeMode());
        assertEquals("Linux", elf64.platform());
        assertEquals("ELF64", elf64.executableFormat());
        assertEquals("ELF", elf64.objectFormat());
    }

    @Test
    void rejectsUnsupportedProfile() {
        assertThrows(DomainException.class, () -> TargetProfileCatalog.require("linux-elf-64-unknown"));
    }
}
