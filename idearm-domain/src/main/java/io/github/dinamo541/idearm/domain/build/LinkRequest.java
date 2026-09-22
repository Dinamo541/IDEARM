package io.github.dinamo541.idearm.domain.build;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import java.util.List;
/** mapFile is null when disabled. */
public record LinkRequest(List<String> objectFiles, String executable, String mapFile,
                          boolean debugInfo, TargetProfile target) {
    public LinkRequest { objectFiles = List.copyOf(objectFiles); }
}
