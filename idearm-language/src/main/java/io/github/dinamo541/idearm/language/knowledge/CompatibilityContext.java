package io.github.dinamo541.idearm.language.knowledge;

import java.util.Collections;
import java.util.Set;

/**
 * Context derived from TargetProfile, ToolchainSelection, and DebugConfiguration.
 */
public record CompatibilityContext(
        String architecture,
        CpuGeneration generation,
        ProcessorMode mode,
        Set<Feature> features,
        Privilege privilege,
        Dialect dialect,
        String dialectVersion,
        String platform,
        String abi,
        String toolchainId,
        String host,
        String backend
) {
    public CompatibilityContext {
        generation = generation != null ? generation : CpuGeneration.UNKNOWN;
        mode = mode != null ? mode : ProcessorMode.REAL;
        features = features != null ? Collections.unmodifiableSet(features) : null;
        privilege = privilege != null ? privilege : Privilege.USER;
        dialect = dialect != null ? dialect : Dialect.COMMON;
    }

    public static CompatibilityContext from(
            io.github.dinamo541.idearm.domain.model.TargetProfile profile,
            String targetCpu,
            io.github.dinamo541.idearm.domain.model.ToolchainSelection toolchain,
            io.github.dinamo541.idearm.domain.model.DebugConfiguration debug,
            Set<Feature> features
    ) {
        String arch = profile != null ? profile.architecture() : "x86";
        CpuGeneration gen;
        if (targetCpu != null && !targetCpu.isBlank()) {
            gen = CpuGeneration.parse(targetCpu);
        } else if (profile != null && profile.cpuBaseline() != null) {
            gen = CpuGeneration.parse(profile.cpuBaseline());
        } else {
            gen = CpuGeneration.UNKNOWN;
        }

        ProcessorMode mode;
        if (profile != null) {
            if (profile.codeMode() == 64 || "long".equalsIgnoreCase(profile.processorMode())) {
                mode = ProcessorMode.LONG;
            } else if (profile.codeMode() == 32 || "protected".equalsIgnoreCase(profile.processorMode())) {
                mode = ProcessorMode.PROTECTED_32;
            } else if (profile.codeMode() == 16 && "protected".equalsIgnoreCase(profile.processorMode())) {
                mode = ProcessorMode.PROTECTED_16;
            } else {
                mode = ProcessorMode.REAL;
            }
        } else {
            mode = ProcessorMode.REAL;
        }

        Set<Feature> feat = features;
        if (feat == null && mode == ProcessorMode.LONG) {
            feat = Set.of(Feature.SSE, Feature.SSE2);
        }

        Dialect dialect = Dialect.COMMON;
        String dialectVersion = null;
        String toolchainId = null;
        if (toolchain != null) {
            toolchainId = toolchain.id();
            dialectVersion = toolchain.version();
            if (toolchainId != null) {
                String tid = toolchainId.toLowerCase(java.util.Locale.ROOT);
                if (tid.contains("tasm")) dialect = Dialect.TASM;
                else if (tid.contains("masm")) dialect = Dialect.MASM;
                else if (tid.contains("nasm")) dialect = Dialect.NASM;
            }
        }

        String platform = profile != null ? profile.platform() : null;
        String abi = "DOS16";
        if (profile != null && "Windows".equalsIgnoreCase(profile.platform())) {
            abi = profile.codeMode() == 64 ? "WIN64" : "STDCALL";
        } else if (profile != null && "Linux".equalsIgnoreCase(profile.platform())) {
            abi = profile.codeMode() == 64 ? "SYSV_AMD64" : "CDECL";
        }

        String backend = debug != null ? debug.backend() : null;

        return new CompatibilityContext(
                arch,
                gen,
                mode,
                feat,
                Privilege.USER,
                dialect,
                dialectVersion,
                platform,
                abi,
                toolchainId,
                null,
                backend
        );
    }

    public static CompatibilityContext dosRealMode(CpuGeneration generation, Dialect dialect, String backend) {
        return new CompatibilityContext(
                "x86",
                generation,
                ProcessorMode.REAL,
                null,
                Privilege.USER,
                dialect,
                null,
                "DOS",
                "DOS16",
                null,
                null,
                backend
        );
    }

    public static CompatibilityContext win64(Dialect dialect, String backend) {
        return new CompatibilityContext(
                "x86_64",
                CpuGeneration.X86_64,
                ProcessorMode.LONG,
                Set.of(Feature.SSE, Feature.SSE2),
                Privilege.USER,
                dialect,
                null,
                "WINDOWS",
                "WIN64",
                null,
                null,
                backend
        );
    }

    public static CompatibilityContext linux64(Dialect dialect, String backend) {
        return new CompatibilityContext(
                "x86_64",
                CpuGeneration.X86_64,
                ProcessorMode.LONG,
                Set.of(Feature.SSE, Feature.SSE2),
                Privilege.USER,
                dialect,
                null,
                "LINUX",
                "SYSV_AMD64",
                null,
                null,
                backend
        );
    }

    public static CompatibilityContext of(CpuGeneration gen, ProcessorMode mode, Set<Feature> features, Dialect dialect, String backend) {
        return new CompatibilityContext(
                "x86",
                gen,
                mode,
                features,
                Privilege.USER,
                dialect,
                null,
                null,
                null,
                null,
                null,
                backend
        );
    }

    public static CompatibilityContext forCpu(CpuGeneration generation) {
        return dosRealMode(generation != null ? generation : CpuGeneration.UNKNOWN, Dialect.COMMON, null);
    }
}
