package io.github.dinamo541.idearm.toolchain.dos.microsoft;

import io.github.dinamo541.idearm.domain.build.AssembleRequest;
import io.github.dinamo541.idearm.domain.build.BuildPhase;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.port.AssemblerAdapter;
import io.github.dinamo541.idearm.domain.port.DiagnosticParser;
import io.github.dinamo541.idearm.toolchain.dos.DosArguments;
import java.util.ArrayList;
import java.util.List;

/**
 * Translates an AssembleRequest into Microsoft ML 6.11 command arguments.
 *
 * <p>ML 6.11 is a Win32 console program that also carries the DOSXNT DOS extender. On Windows it runs natively
 * (20-60 ms per file). Where the host cannot run Win32 programs (Linux), it runs inside DOSBox like LINK, with its
 * paths written as DOS drive paths; spike S7 verified that mode with DOSBox 0.74-3.
 */
public final class MasmAssemblerAdapter implements AssemblerAdapter {

    private final boolean insideDos;

    /** The native Windows mode. */
    public MasmAssemblerAdapter() {
        this(false);
    }

    public MasmAssemblerAdapter(boolean insideDos) {
        this.insideDos = insideDos;
    }

    @Override
    public ToolInvocation assemble(AssembleRequest request) {
        var outputs = new ArrayList<>(List.of(request.objectFile()));
        var args = new ArrayList<String>();
        args.add("/c");
        args.add("/nologo");
        args.add("/W2");
        if (request.debugInfo()) {
            args.add("/Zi");
        }
        for (String include : request.includeDirs()) {
            args.add("/I" + (insideDos ? DosArguments.path('S', include) : include));
        }
        args.add("/Fo" + (insideDos ? DosArguments.path('C', request.objectFile()) : request.objectFile().replace('/', '\\')));
        if (request.listingFile() != null) {
            args.add("/Fl" + (insideDos ? DosArguments.path('C', request.listingFile())
                    : request.listingFile().replace('/', '\\')));
            outputs.add(request.listingFile());
        }
        args.add(insideDos ? DosArguments.path('S', request.source()) : request.source().replace('/', '\\'));
        return new ToolInvocation("ml", BuildPhase.ASSEMBLE, host(), List.copyOf(args), null, null, List.copyOf(outputs));
    }

    @Override
    public DiagnosticParser diagnostics() {
        return new MasmDiagnosticParser();
    }

    @Override
    public HostKind host() {
        return insideDos ? HostKind.DOS_DPMI : HostKind.WIN32_CONSOLE;
    }
}
