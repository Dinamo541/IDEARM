package io.github.dinamo541.idearm.domain.build;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.*;
import io.github.dinamo541.idearm.domain.port.ToolchainProvider;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CompatibilityResolver {
    private static final Map<String, Set<Integer>> CPU_MODES = Map.of(
            "8086", Set.of(16), "80186", Set.of(16), "80286", Set.of(16),
            "80386", Set.of(16, 32), "80486", Set.of(16, 32),
            "pentium", Set.of(16, 32), "x86-64", Set.of(16, 32, 64));

    public TargetProfile resolve(TargetSelection selection, ToolchainProvider provider) {
        TargetProfile base = TargetProfileCatalog.require(selection.profile());
        Set<Integer> modes = CPU_MODES.get(selection.cpu().toLowerCase(Locale.ROOT));
        if (modes == null || !modes.contains(base.codeMode())) {
            throw new DomainException("target.cpu.incompatible", "CPU baseline is not compatible with this code mode: " + selection.cpu(), selection.cpu());
        }
        if (!provider.supports().contains(base.support())) {
            throw new DomainException("toolchain.target.incompatible", "Toolchain " + provider.id() + " does not support " + base.id(), provider.id(), base.id());
        }
        return new TargetProfile(base.id(), base.architecture(), selection.cpu(), base.codeMode(),
                base.processorMode(), base.platform(), base.executableFormat(), base.memoryModel(), base.objectFormat());
    }
}
