package io.github.dinamo541.idearm.toolchain.nasm;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Toolchain provider for NASM and GNU ld: 32-bit and 64-bit Windows PE programs, and Linux ELF programs when the
 * IDE itself runs on Linux.
 */
public final class NasmToolchainProvider implements ToolchainProvider {

    public static final String ID = "nasm";

    private static final Set<TargetSupport> SUPPORTS = Set.of(
            new TargetSupport(64, "COFF", "PE32+", "Windows"),
            new TargetSupport(32, "COFF", "PE32", "Windows"),
            new TargetSupport(64, "ELF", "ELF64", "Linux")
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<TargetSupport> supports() {
        return SUPPORTS;
    }

    @Override
    public AssemblerAdapter assembler() {
        return new NasmAssemblerAdapter();
    }

    @Override
    public LinkerAdapter linker() {
        return new GnuLinkerAdapter();
    }

    @Override
    public ResolvedToolchain resolve(Project project, ToolRegistry registry) {
        if (!project.toolchain().id().equals(id())) {
            throw new DomainException("toolchain.mismatch", "The project does not select " + id() + ".", id());
        }

        if (NativeHost.windows()) {
            var target = io.github.dinamo541.idearm.domain.model.TargetProfileCatalog.require(project.target().profile());
            if ("Linux".equalsIgnoreCase(target.platform())) {
                // MSYS2/MinGW ld only writes PE files; an ELF program needs a Linux linker (Linux or WSL).
                throw new DomainException("toolchain.target.host",
                        "Linux ELF programs are built on Linux (or in WSL); this Windows linker only produces PE files.");
            }
        }
        ToolInstallation nasm = required(registry, "nasm");
        // GNU ld always ships with GCC (binutils); the adapter passes ld options, which gcc would not accept.
        ToolInstallation linker = required(registry, "ld");

        Map<String, ToolInstallation> tools = new HashMap<>();
        tools.put("nasm", nasm);
        tools.put("ld", linker);
        return new ResolvedToolchain(id(), tools);
    }

    private static ToolInstallation required(ToolRegistry registry, String id) {
        ToolInstallation installation = registry.find(id).orElseThrow(() ->
                new DomainException("toolchain.not-found", "Register your own " + id + " installation before building.", id));
        if (!Files.isRegularFile(installation.executable())) {
            throw new DomainException("toolchain.invalid-path",
                    "The registered " + id + " executable does not exist: " + installation.executable(), id, installation.executable());
        }
        return installation;
    }
}
