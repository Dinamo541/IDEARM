package io.github.dinamo541.idearm.toolchain.nasm;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.BuildPhase;
import io.github.dinamo541.idearm.domain.build.LinkRequest;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.port.DiagnosticParser;
import io.github.dinamo541.idearm.domain.port.LinkerAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Links NASM objects into a program with GNU ld.
 *
 * <p>ld is told everything a hand-written Assembly program needs and a C compiler would normally supply: the
 * output format ({@code -m}), the entry label the project templates use ({@code main}, or {@code _main} for
 * 32-bit Windows, whose symbols carry an underscore), and for Windows a console program that imports
 * {@code kernel32}. 32-bit programs link against the 32-bit system DLLs, so a 64-bit MSYS2 installation builds
 * both widths.
 */
public final class GnuLinkerAdapter implements LinkerAdapter {

    @Override
    public ToolInvocation link(LinkRequest request) {
        if (request.objectFiles().isEmpty()) {
            throw new DomainException("toolchain.linker.no-objects", "At least one object file is required to link.");
        }
        TargetProfile target = request.target();
        boolean windows = target == null || "Windows".equalsIgnoreCase(target.platform());
        boolean wide = target == null || target.codeMode() == 64;

        var args = new ArrayList<String>();
        args.add("-m");
        args.add(windows ? (wide ? "i386pep" : "i386pe") : (wide ? "elf_x86_64" : "elf_i386"));
        args.add("-e");
        args.add(windows && !wide ? "_main" : "main");
        if (windows) {
            args.add("--subsystem");
            args.add("console");
            if (!wide) {
                // Linking straight to a DLL maps _ExitProcess@4 to ExitProcess; saying so silences ld's warning.
                args.add("--enable-stdcall-fixup");
            }
        }
        args.add("-o");
        args.add(request.executable());
        if (request.mapFile() != null) {
            args.add("-Map=" + request.mapFile());
        }
        args.addAll(request.objectFiles());
        if (windows) {
            if (NativeHost.windows()) {
                args.add("-L" + NativeHost.systemLibraries(wide));
            }
            args.add("-lkernel32");
        }

        var outputs = new ArrayList<>(List.of(request.executable()));
        if (request.mapFile() != null) {
            outputs.add(request.mapFile());
        }
        return new ToolInvocation("ld", BuildPhase.LINK, host(), List.copyOf(args), null, null, outputs);
    }

    @Override
    public DiagnosticParser diagnostics() {
        return new GnuLinkerDiagnosticParser();
    }

    @Override
    public HostKind host() {
        return NativeHost.kind();
    }
}
