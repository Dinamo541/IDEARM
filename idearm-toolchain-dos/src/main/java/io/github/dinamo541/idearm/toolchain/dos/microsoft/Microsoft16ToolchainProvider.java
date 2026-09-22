package io.github.dinamo541.idearm.toolchain.dos.microsoft;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.HostKind;
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

/**
 * Supplies the Microsoft MASM 6.11 / LINK 5.31 toolchain family for 16-bit DOS targets.
 * ML 6.11 executes natively on a Windows host, and LINK 5.31 executes inside DOSBox. On a host that cannot run
 * Win32 programs (Linux), ML runs inside DOSBox too, through the DOSXNT extender that ships next to it (S7).
 */
public final class Microsoft16ToolchainProvider implements ToolchainProvider {

    /** Forces ML into DOSBox on Windows as well, which is how the Linux mode is verified on a Windows machine. */
    static final String INSIDE_DOSBOX_PROPERTY = "idearm.masm.insideDosBox";

    private static boolean mlInsideDos() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows");
        return !windows || Boolean.getBoolean(INSIDE_DOSBOX_PROPERTY);
    }

    @Override
    public String id() {
        return "microsoft-masm";
    }

    @Override
    public Set<TargetSupport> supports() {
        return Set.of(new TargetSupport(16, "OMF", "MZ", "DOS"));
    }

    @Override
    public AssemblerAdapter assembler() {
        return new MasmAssemblerAdapter(mlInsideDos());
    }

    @Override
    public LinkerAdapter linker() {
        return new MasmLinkerAdapter();
    }

    @Override
    public ResolvedToolchain resolve(Project project, ToolRegistry registry) {
        if (!project.toolchain().id().equals(id())) {
            throw new DomainException("toolchain.mismatch", "The project does not select " + id() + ".", id());
        }
        ToolInstallation ml = required(registry, "ml");
        ToolInstallation link = required(registry, "link");
        ToolInstallation dosbox = io.github.dinamo541.idearm.toolchain.dos.DosBoxResolution.forBuild(registry, project);

        checkVersion(ml.version(), project.toolchain().version());

        if (ml.hostKind() != HostKind.WIN32_CONSOLE && ml.hostKind() != HostKind.WIN64) {
            throw new DomainException("toolchain.unsupported-host", "ML must be a Win32/Win64 console executable: " + ml.hostKind(), "ML.EXE", ml.hostKind());
        }
        if (mlInsideDos()) {
            ml = forDosBox(ml);
        }
        link = withDebugPacker(link);
        if (link.hostKind() != HostKind.DOS_REAL && link.hostKind() != HostKind.DOS_DPMI) {
            throw new DomainException("toolchain.unsupported-host", "LINK must be a 16-bit DOS executable: " + link.hostKind(), "LINK.EXE", link.hostKind());
        }

        return new ResolvedToolchain(id(), Map.of("ml", ml, "link", link), dosbox);
    }

    /**
     * ML as DOSBox runs it: a DOS-extended program that needs DOSXNT.EXE beside it on drive T:, and ML.ERR for the
     * text of its messages when the installation has it.
     */
    private static ToolInstallation forDosBox(ToolInstallation ml) {
        java.nio.file.Path folder = ml.executable().toAbsolutePath().getParent();
        var companions = new java.util.LinkedHashMap<String, java.nio.file.Path>(ml.companions());
        java.nio.file.Path extender = findIn(folder, "DOSXNT.EXE");
        if (extender == null) {
            throw new DomainException("toolchain.missing-companion",
                    "ML needs DOSXNT.EXE next to its registered executable to run inside DOSBox.", "DOSXNT.EXE", "ML");
        }
        companions.put("DOSXNT.EXE", extender);
        java.nio.file.Path messages = findIn(folder, "ML.ERR");
        if (messages != null) {
            companions.put("ML.ERR", messages);
        }
        return new ToolInstallation(ml.toolId(), ml.version(), ml.executable(), HostKind.DOS_DPMI, companions,
                ml.sha256(), ml.source());
    }

    /**
     * LINK /CO runs CVPACK on the program it wrote to compact its CodeView information, and warns (L4081) when it
     * cannot; CVPACK is DOS-extended like ML, so DOSXNT travels with it.
     */
    private static ToolInstallation withDebugPacker(ToolInstallation link) {
        java.nio.file.Path folder = link.executable().toAbsolutePath().getParent();
        java.nio.file.Path packer = findIn(folder, "CVPACK.EXE");
        java.nio.file.Path extender = findIn(folder, "DOSXNT.EXE");
        if (packer == null || extender == null) {
            return link;
        }
        var companions = new java.util.LinkedHashMap<String, java.nio.file.Path>(link.companions());
        companions.putIfAbsent("CVPACK.EXE", packer);
        companions.putIfAbsent("DOSXNT.EXE", extender);
        return new ToolInstallation(link.toolId(), link.version(), link.executable(), link.hostKind(), companions,
                link.sha256(), link.source());
    }

    /** A file in a folder, matched without regard to case as DOS names are. */
    private static java.nio.file.Path findIn(java.nio.file.Path folder, String name) {
        if (folder == null) {
            return null;
        }
        try (var entries = Files.list(folder)) {
            return entries.filter(entry -> entry.getFileName().toString().equalsIgnoreCase(name))
                    .filter(Files::isRegularFile)
                    .findFirst().orElse(null);
        } catch (java.io.IOException unreadable) {
            return null;
        }
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
        if (!constraint.matches()) {
            throw new DomainException("toolchain.version-constraint", "Supported version constraints are exact versions, >= versions, or *: " + requested, requested);
        }
        var actual = Pattern.compile("\\d+(?:\\.\\d+)*").matcher(installed);
        if (!actual.find()) {
            throw new DomainException("toolchain.version-unknown", "Cannot verify the registered MASM version: " + installed, installed);
        }
        String[] left = actual.group().split("\\.");
        String[] right = constraint.group(2).split("\\.");
        int comparison = 0;
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            comparison = new java.math.BigInteger(i < left.length ? left[i] : "0")
                    .compareTo(new java.math.BigInteger(i < right.length ? right[i] : "0"));
            if (comparison != 0) break;
        }
        if ((">=".equals(constraint.group(1)) && comparison < 0) || (!">=".equals(constraint.group(1)) && comparison != 0)) {
            throw new DomainException("toolchain.version-mismatch", "Registered MASM " + installed + " does not satisfy " + requested + ".", installed, requested);
        }
    }
}
