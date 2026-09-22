package io.github.dinamo541.idearm.toolchain.dos.borland;

import io.github.dinamo541.idearm.domain.build.AssembleRequest;
import io.github.dinamo541.idearm.domain.build.BuildPhase;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.port.AssemblerAdapter;
import io.github.dinamo541.idearm.domain.port.DiagnosticParser;
import java.util.ArrayList;
import java.util.List;

/** Turbo Assembler 3.x/4.x, executed inside DOSBox. /w2 enables every warning (verified in Phase 0). */
public final class TasmAssemblerAdapter implements AssemblerAdapter {
    @Override
    public ToolInvocation assemble(AssembleRequest request) {
        var outputs = new ArrayList<>(List.of(request.objectFile()));
        var switches = new StringBuilder("/w2");
        if (request.debugInfo()) {
            switches.append(" /zi");
        }
        // TASM searches include directories given as /i<path>; staged sources and includes share drive S.
        for (String include : request.includeDirs()) {
            switches.append(" /i").append(DosArguments.path('S', include));
        }
        String files = DosArguments.path('S', request.source()) + "," + DosArguments.path('C', request.objectFile());
        if (request.listingFile() != null) {
            switches.append(" /l");
            files += "," + DosArguments.path('C', request.listingFile());
            outputs.add(request.listingFile());
        }
        return new ToolInvocation("tasm", BuildPhase.ASSEMBLE, host(), List.of("@C:\\asm.rsp"), "asm.rsp",
                switches + " " + files, outputs);
    }

    @Override public DiagnosticParser diagnostics() { return new TasmDiagnosticParser(); }
    @Override public HostKind host() { return HostKind.DOS_REAL; }
}
