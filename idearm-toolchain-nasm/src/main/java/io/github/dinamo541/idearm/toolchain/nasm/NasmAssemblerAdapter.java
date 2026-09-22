package io.github.dinamo541.idearm.toolchain.nasm;

import io.github.dinamo541.idearm.domain.build.AssembleRequest;
import io.github.dinamo541.idearm.domain.build.BuildPhase;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.port.AssemblerAdapter;
import io.github.dinamo541.idearm.domain.port.DiagnosticParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Invokes the Netwide Assembler (NASM), which writes the object format the target needs: {@code win64} or
 * {@code win32} (COFF) for Windows, {@code elf64} or {@code elf32} for Linux.
 *
 * <p>A debug build for Windows is the exception: NASM can only attach CodeView to COFF objects, and GDB does not
 * read CodeView. The object is written as ELF with DWARF instead; GNU ld reads ELF objects and links them into
 * the same PE program, whose DWARF lets GDB stop on source lines (verified with NASM 3.01, ld 2.46, GDB 17.2).
 */
public final class NasmAssemblerAdapter implements AssemblerAdapter {

    @Override
    public ToolInvocation assemble(AssembleRequest request) {
        var args = new ArrayList<String>();
        String format = format(request.target(), request.cpu(), request.debugInfo());
        args.add("-f");
        args.add(format);

        if (request.debugInfo()) {
            args.add("-g");
            args.add("-F");
            args.add(format.startsWith("win") ? "cv8" : "dwarf");
        }

        for (String include : request.includeDirs()) {
            args.add("-I" + (include.endsWith("/") ? include : include + "/"));
        }

        if (request.listingFile() != null) {
            args.add("-l");
            args.add(request.listingFile());
        }

        args.add("-o");
        args.add(request.objectFile());
        args.add(request.source());

        var outputs = new ArrayList<>(List.of(request.objectFile()));
        if (request.listingFile() != null) {
            outputs.add(request.listingFile());
        }

        return new ToolInvocation("nasm", BuildPhase.ASSEMBLE, host(), List.copyOf(args), null, null, outputs);
    }

    /** The NASM output format for a target; callers without a target get the CPU-based guess. */
    static String format(TargetProfile target, String cpu, boolean debugInfo) {
        if (target != null) {
            boolean linux = "Linux".equalsIgnoreCase(target.platform());
            boolean wide = target.codeMode() == 64;
            if (linux || debugInfo) {
                return wide ? "elf64" : "elf32";
            }
            return wide ? "win64" : "win32";
        }
        String baseline = cpu == null ? "" : cpu.toLowerCase(Locale.ROOT);
        return baseline.matches(".*(386|486|586|pentium).*") ? "win32" : "win64";
    }

    @Override
    public DiagnosticParser diagnostics() {
        return new NasmDiagnosticParser();
    }

    @Override
    public HostKind host() {
        return NativeHost.kind();
    }
}
