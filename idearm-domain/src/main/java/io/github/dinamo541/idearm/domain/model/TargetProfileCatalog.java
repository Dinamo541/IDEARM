package io.github.dinamo541.idearm.domain.model;

import io.github.dinamo541.idearm.domain.DomainException;
import java.util.Collection;
import java.util.Map;

/** The catalog grows when a new target has a tested toolchain and execution path. */
public final class TargetProfileCatalog {
    public static final TargetProfile DOS_EXE_16 = new TargetProfile(
            "dos-exe-16", "x86", "8086", 16, "real", "DOS", "MZ", "small", "OMF");
    public static final TargetProfile WIN_PE64_CONSOLE = new TargetProfile(
            "win-pe64-console", "x86", "x86-64", 64, "long", "Windows", "PE32+", "flat", "COFF");
    public static final TargetProfile WIN_PE32_CONSOLE = new TargetProfile(
            "win-pe32-console", "x86", "80386", 32, "protected", "Windows", "PE32", "flat", "COFF");
    public static final TargetProfile LINUX_ELF64 = new TargetProfile(
            "linux-elf64", "x86", "x86-64", 64, "long", "Linux", "ELF64", "flat", "ELF");

    private static final Map<String, TargetProfile> PROFILES = Map.of(
            DOS_EXE_16.id(), DOS_EXE_16,
            WIN_PE64_CONSOLE.id(), WIN_PE64_CONSOLE,
            WIN_PE32_CONSOLE.id(), WIN_PE32_CONSOLE,
            LINUX_ELF64.id(), LINUX_ELF64);
    private TargetProfileCatalog() {}
    public static Collection<TargetProfile> profiles() { return PROFILES.values(); }
    public static TargetProfile require(String id) {
        TargetProfile profile = PROFILES.get(id);
        if (profile == null) throw new DomainException("target.profile.unsupported", "Unsupported target profile: " + id, id);
        return profile;
    }
}
