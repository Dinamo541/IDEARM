package io.github.dinamo541.idearm.toolchain.nasm;

import io.github.dinamo541.idearm.domain.build.AssembleRequest;
import io.github.dinamo541.idearm.domain.build.BuildPhase;
import io.github.dinamo541.idearm.domain.build.LinkRequest;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.TargetProfileCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NasmToolchainTest {

    @Test
    void generatesNasmWin64AssembleInvocation() {
        NasmAssemblerAdapter adapter = new NasmAssemblerAdapter();
        AssembleRequest request = new AssembleRequest("src/main.asm", "obj/main.obj", "lst/main.lst", true, "x86-64",
                List.of("include"), TargetProfileCatalog.WIN_PE64_CONSOLE);

        ToolInvocation invocation = adapter.assemble(request);
        assertEquals("nasm", invocation.toolId());
        assertEquals(BuildPhase.ASSEMBLE, invocation.phase());
        assertEquals(System.getProperty("os.name").toLowerCase().contains("windows") ? HostKind.WIN64 : HostKind.LINUX_ELF,
                invocation.hostKind());

        List<String> args = invocation.arguments();
        assertTrue(args.contains("-f"));
        // A debug build for Windows is assembled as ELF/DWARF, the only debug format GDB reads.
        assertTrue(args.contains("elf64"));
        assertTrue(args.contains("-g"));
        assertTrue(args.contains("-F"));
        assertTrue(args.contains("dwarf"));
        assertTrue(args.contains("-Iinclude/"));
        assertTrue(args.contains("-l"));
        assertTrue(args.contains("lst/main.lst"));
        assertTrue(args.contains("-o"));
        assertTrue(args.contains("obj/main.obj"));
        assertTrue(args.contains("src/main.asm"));
    }

    @Test
    void generatesNasmElf64AssembleInvocation() {
        NasmAssemblerAdapter adapter = new NasmAssemblerAdapter();
        AssembleRequest request = new AssembleRequest("src/main.asm", "obj/main.o", null, true, "x86-64",
                List.of(), TargetProfileCatalog.LINUX_ELF64);

        ToolInvocation invocation = adapter.assemble(request);
        List<String> args = invocation.arguments();
        assertTrue(args.contains("-f"));
        assertTrue(args.contains("elf64"));
        assertTrue(args.contains("dwarf"));
    }

    @Test
    void generatesGnuLinkerInvocation() {
        GnuLinkerAdapter adapter = new GnuLinkerAdapter();
        LinkRequest request = new LinkRequest(
                List.of("obj/main.obj", "obj/utils.obj"),
                "bin/main.exe",
                "map/main.map",
                true,
                TargetProfileCatalog.WIN_PE64_CONSOLE
        );

        ToolInvocation invocation = adapter.link(request);
        assertEquals("ld", invocation.toolId());
        assertEquals(BuildPhase.LINK, invocation.phase());

        List<String> args = invocation.arguments();
        assertTrue(args.contains("-o"));
        assertTrue(args.contains("bin/main.exe"));
        assertTrue(args.contains("-Map=map/main.map"));
        assertTrue(args.contains("obj/main.obj"));
        assertTrue(args.contains("obj/utils.obj"));
        assertTrue(args.contains("-lkernel32"));
        assertEquals(List.of("-m", "i386pep", "-e", "main", "--subsystem", "console"), args.subList(0, 6));
    }

    @Test
    void theObjectFormatFollowsTheTargetNotTheCpu() {
        var adapter = new NasmAssemblerAdapter();

        // A Linux program must never be assembled as a Windows object, whatever its CPU baseline.
        assertEquals("elf64", format(adapter, TargetProfileCatalog.LINUX_ELF64));
        assertEquals("win64", format(adapter, TargetProfileCatalog.WIN_PE64_CONSOLE));
        assertEquals("win32", format(adapter, TargetProfileCatalog.WIN_PE32_CONSOLE));
    }

    @Test
    void thirtyTwoBitWindowsProgramsLinkAsPe32WithTheUnderscoredEntry() {
        var request = new LinkRequest(List.of("obj/main.obj"), "bin/main.exe", null, false,
                TargetProfileCatalog.WIN_PE32_CONSOLE);

        List<String> args = new GnuLinkerAdapter().link(request).arguments();

        assertEquals(List.of("-m", "i386pe", "-e", "_main"), args.subList(0, 4));
        assertTrue(args.contains("-lkernel32"));
    }

    @Test
    void linuxProgramsLinkAsElfWithoutWindowsLibraries() {
        var request = new LinkRequest(List.of("obj/main.o"), "bin/main", null, false, TargetProfileCatalog.LINUX_ELF64);

        List<String> args = new GnuLinkerAdapter().link(request).arguments();

        assertEquals(List.of("-m", "elf_x86_64", "-e", "main", "-o", "bin/main", "obj/main.o"), args);
    }

    private static String format(NasmAssemblerAdapter adapter, io.github.dinamo541.idearm.domain.model.TargetProfile target) {
        var args = adapter.assemble(new AssembleRequest("src/main.asm", "obj/main.obj", null, false,
                target.cpuBaseline(), List.of(), target)).arguments();
        assertFalse(args.contains("-g"), "A release build carries no debug information");
        return args.get(args.indexOf("-f") + 1);
    }

    @Test
    void toolchainProviderDeclaresSupportedProfiles() {
        NasmToolchainProvider provider = new NasmToolchainProvider();
        assertEquals("nasm", provider.id());
        assertTrue(provider.supports().contains(TargetProfileCatalog.WIN_PE64_CONSOLE.support()));
        assertTrue(provider.supports().contains(TargetProfileCatalog.WIN_PE32_CONSOLE.support()));
        assertTrue(provider.supports().contains(TargetProfileCatalog.LINUX_ELF64.support()));
    }
}
