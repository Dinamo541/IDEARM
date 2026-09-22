package io.github.dinamo541.idearm.toolchain.dos.borland;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.BuildPhase;
import io.github.dinamo541.idearm.domain.build.LinkRequest;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.port.DiagnosticParser;
import io.github.dinamo541.idearm.domain.port.LinkerAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class TlinkLinkerAdapter implements LinkerAdapter {
    @Override
    public ToolInvocation link(LinkRequest request) {
        if (!request.target().executableFormat().equalsIgnoreCase("MZ") || request.objectFiles().isEmpty()) {
            throw new DomainException("toolchain.unsupported-target", "The first Borland linker adapter supports DOS MZ with at least one object.");
        }
        var outputs = new ArrayList<>(List.of(request.executable()));
        String switches = request.debugInfo() ? "/v " : "";
        String files = request.objectFiles().stream().map(path -> DosArguments.path('C', path)).collect(Collectors.joining(" "))
            + "," + DosArguments.path('C', request.executable());
        if (request.mapFile() != null) {
            switches += "/m ";
            files += "," + DosArguments.path('C', request.mapFile());
            outputs.add(request.mapFile());
        }
        return new ToolInvocation("tlink", BuildPhase.LINK, host(), List.of("@C:\\link.rsp"), "link.rsp", switches + files, outputs);
    }

    @Override public DiagnosticParser diagnostics() { return new TlinkDiagnosticParser(); }
    @Override public HostKind host() { return HostKind.DOS_REAL; }
}
