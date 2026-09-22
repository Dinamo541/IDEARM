package io.github.dinamo541.idearm.toolchain.dos.microsoft;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.build.BuildPhase;
import io.github.dinamo541.idearm.domain.build.LinkRequest;
import io.github.dinamo541.idearm.domain.build.ToolInvocation;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.port.DiagnosticParser;
import io.github.dinamo541.idearm.domain.port.LinkerAdapter;
import io.github.dinamo541.idearm.toolchain.dos.DosArguments;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Translates a LinkRequest into Microsoft LINK 5.31 response file for 16-bit DOS MZ executables.
 * LINK 5.31 is a 16-bit DOS binary executed inside DOSBox.
 * The response file MUST end with a trailing semicolon (;) to prevent interactive prompts from hanging.
 */
public final class MasmLinkerAdapter implements LinkerAdapter {

    @Override
    public ToolInvocation link(LinkRequest request) {
        if (!request.target().executableFormat().equalsIgnoreCase("MZ") || request.objectFiles().isEmpty()) {
            throw new DomainException("toolchain.unsupported-target",
                    "Microsoft LINK adapter supports DOS MZ with at least one object file.");
        }
        var outputs = new ArrayList<>(List.of(request.executable()));
        String switches = "/NOLOGO /ONERROR:NOEXE /BATCH" + (request.debugInfo() ? " /CO" : "");

        String objects = request.objectFiles().stream()
                .map(path -> DosArguments.path('C', path))
                .collect(Collectors.joining("+"));

        String files = objects + "," + DosArguments.path('C', request.executable());
        if (request.mapFile() != null) {
            files += "," + DosArguments.path('C', request.mapFile());
            outputs.add(request.mapFile());
        }

        // The trailing semicolon is mandatory in Microsoft LINK to accept defaults and terminate prompts
        String responseContent = switches + " " + files + ";";

        return new ToolInvocation("link", BuildPhase.LINK, host(), List.of("@C:\\link.rsp"), "link.rsp", responseContent, outputs);
    }

    @Override
    public DiagnosticParser diagnostics() {
        return new MasmLinkerDiagnosticParser();
    }

    @Override
    public HostKind host() {
        return HostKind.DOS_REAL;
    }
}
