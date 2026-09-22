package io.github.dinamo541.idearm.toolchain.dos.borland;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.Project;
import io.github.dinamo541.idearm.domain.model.ResolvedToolchain;
import io.github.dinamo541.idearm.domain.model.TargetSupport;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.domain.port.AssemblerAdapter;
import io.github.dinamo541.idearm.domain.port.LinkerAdapter;
import io.github.dinamo541.idearm.domain.port.ToolRegistry;
import io.github.dinamo541.idearm.domain.port.ToolchainProvider;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Supplies the coherent TASM/TLINK family; machine paths belong to ToolRegistry. */
public final class BorlandToolchainProvider implements ToolchainProvider {
    @Override public String id() { return "borland-tasm"; }
    @Override public Set<TargetSupport> supports() { return Set.of(new TargetSupport(16, "OMF", "MZ", "DOS")); }
    @Override public AssemblerAdapter assembler() { return new TasmAssemblerAdapter(); }
    @Override public LinkerAdapter linker() { return new TlinkLinkerAdapter(); }

    @Override
    public ResolvedToolchain resolve(Project project, ToolRegistry registry) {
        if (!project.toolchain().id().equals(id())) {
            throw new DomainException("toolchain.mismatch", "The project does not select " + id() + ".", id());
        }
        ToolInstallation tasm = required(registry, "tasm");
        ToolInstallation tlink = required(registry, "tlink");
        ToolInstallation dosbox = io.github.dinamo541.idearm.toolchain.dos.DosBoxResolution.forBuild(registry, project);
        checkVersion(tasm.version(), project.toolchain().version());
        if (tlink.hostKind() == io.github.dinamo541.idearm.domain.model.HostKind.DOS_DPMI) {
            for (String required : Set.of("RTM.EXE", "DPMI16BI.OVL")) {
                if (tlink.companions().entrySet().stream().noneMatch(entry -> entry.getKey().equalsIgnoreCase(required) && Files.isRegularFile(entry.getValue()))) {
                    throw new DomainException("toolchain.missing-companion", "TLINK requires " + required + " next to its registered executable.", required, "TLINK");
                }
            }
        }
        return new ResolvedToolchain(id(), Map.of("tasm", tasm, "tlink", tlink), dosbox);
    }

    private static ToolInstallation required(ToolRegistry registry, String id) {
        ToolInstallation installation = registry.find(id).orElseThrow(() ->
            new DomainException("toolchain.not-found", "Register your own " + id + " installation before building.", id));
        return validated(installation, id);
    }



    private static ToolInstallation validated(ToolInstallation installation, String id) {
        if (!Files.isRegularFile(installation.executable())) {
            throw new DomainException("toolchain.invalid-path", "The registered " + id + " executable does not exist: " + installation.executable(), id, installation.executable());
        }
        return installation;
    }

    private static void checkVersion(String installed, String requested) {
        if (requested.isBlank() || requested.equals("*")) return;
        var constraint = Pattern.compile("(>=|=)?\\s*(\\d+(?:\\.\\d+)*)").matcher(requested);
        if (!constraint.matches()) throw new DomainException("toolchain.version-constraint", "Supported version constraints are exact versions, >= versions, or *: " + requested, requested);
        var actual = Pattern.compile("\\d+(?:\\.\\d+)*").matcher(installed);
        if (!actual.find()) throw new DomainException("toolchain.version-unknown", "Cannot verify the registered TASM version: " + installed, installed);
        String[] left = actual.group().split("\\.");
        String[] right = constraint.group(2).split("\\.");
        int comparison = 0;
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            comparison = new java.math.BigInteger(i < left.length ? left[i] : "0")
                .compareTo(new java.math.BigInteger(i < right.length ? right[i] : "0"));
            if (comparison != 0) break;
        }
        if ((">=".equals(constraint.group(1)) && comparison < 0) || (!">=".equals(constraint.group(1)) && comparison != 0)) {
            throw new DomainException("toolchain.version-mismatch", "Registered TASM " + installed + " does not satisfy " + requested + ".", installed, requested);
        }
    }
}
