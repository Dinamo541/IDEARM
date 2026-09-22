package io.github.dinamo541.idearm.domain.build;

import io.github.dinamo541.idearm.domain.model.TargetProfile;
import java.util.List;

/**
 * Paths are relative slash-separated paths; listingFile is null when disabled.
 *
 * <p>{@code includeDirs} are project-relative directories that the assembler must search for included files;
 * runners stage them next to the sources so DOS tools can resolve them. {@code target} tells assemblers that
 * emit several object formats (NASM) which one the rest of the build expects; it is null only for callers that
 * predate it.
 */
public record AssembleRequest(String source, String objectFile, String listingFile, boolean debugInfo, String cpu,
                              List<String> includeDirs, TargetProfile target) {
    public AssembleRequest { includeDirs = List.copyOf(includeDirs); }

    public AssembleRequest(String source, String objectFile, String listingFile, boolean debugInfo, String cpu,
                           List<String> includeDirs) {
        this(source, objectFile, listingFile, debugInfo, cpu, includeDirs, null);
    }

    public AssembleRequest(String source, String objectFile, String listingFile, boolean debugInfo, String cpu) {
        this(source, objectFile, listingFile, debugInfo, cpu, List.of(), null);
    }
}
