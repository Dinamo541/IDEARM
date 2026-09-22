package io.github.dinamo541.idearm.domain.build;

import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.files.FileNames;
import io.github.dinamo541.idearm.domain.model.*;
import io.github.dinamo541.idearm.domain.port.ToolchainProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Multi-module build planning for x86 Assembly targets without inspecting the file system. */
public final class BuildPlanner {
    private final CompatibilityResolver compatibility = new CompatibilityResolver();

    public BuildPlan plan(Project project, String configuration, ToolchainProvider provider) {
        if (project.schema() != 1) fail("project.schema.unsupported", "Unsupported project schema: " + project.schema(), project.schema());
        if (project.info().name().isBlank() || project.info().version().isBlank()) {
            fail("project.info.invalid", "Project name and version must not be blank.");
        }
        if (!List.of("debug", "release").contains(configuration)) {
            fail("build.configuration.unsupported", "Supported build configurations are debug and release: " + configuration, configuration);
        }
        BuildConfiguration flags = project.build().get(configuration);
        if (flags == null) fail("build.configuration.missing", "Missing build configuration: " + configuration, configuration);
        if (!project.toolchain().id().equals(provider.id())) {
            fail("toolchain.provider.mismatch", "Selected toolchain does not match the supplied provider.");
        }
        TargetProfile target = compatibility.resolve(project.target(), provider);
        Sources sources = project.sources();
        if (!flags.defines().isEmpty()) fail("build.defines.unsupported", "Build defines are not supported in this version.");
        if (!flags.extraArgs().isEmpty()) fail("build.extra-args.unsupported", "Advanced tool arguments are not supported in this version.");

        // DOS tools only see 8.3 names; native 32/64-bit tools accept any name.
        boolean dosNames = target.isDos();
        List<String> allSources = new ArrayList<>();
        allSources.add(sources.entry());
        allSources.addAll(sources.modules());
        FileNameRules.validateDistinctPaths(allSources, dosNames);

        for (String src : allSources) {
            if (!FileNames.extension(FileNames.lastSegment(src)).equals("asm")) {
                fail("source.extension.unsupported", "Source file must have an .asm extension: " + src, src);
            }
        }

        if (!sources.include().isEmpty()) {
            FileNameRules.validateDistinctPaths(sources.include(), dosNames);
        }

        // Generated names keep the source's spelling, with lower-case folders and extensions (FileNames).
        // DOS upper-cases them inside the emulator; runners publish them exactly as spelled here.
        String entryStem = FileNames.stem(FileNames.lastSegment(sources.entry()));

        var steps = new ArrayList<ToolInvocation>();
        var objectFiles = new ArrayList<String>();
        var outputs = new ArrayList<String>();
        var listings = new LinkedHashMap<String, String>();

        // 1. Assemble entry and each module
        for (String src : allSources) {
            String stem = FileNames.stem(FileNames.lastSegment(src));
            String object = "obj/" + stem + "." + target.objectExtension();
            String listing = flags.listing() ? "lst/" + stem + ".lst" : null;

            steps.add(provider.assembler().assemble(
                    new AssembleRequest(src, object, listing, flags.debugInfo(), target.cpuBaseline(), sources.include(),
                            target)));
            objectFiles.add(object);
            outputs.add(object);
            if (listing != null) {
                outputs.add(listing);
                listings.put(src, listing);
            }
        }

        // 2. Link all object files into the final executable
        String extension = target.executableExtension();
        String executable = "bin/" + entryStem + (extension.isEmpty() ? "" : "." + extension);
        String map = flags.map() ? "map/" + entryStem + ".map" : null;

        steps.add(provider.linker().link(
                new LinkRequest(objectFiles, executable, map, flags.debugInfo(), target)));
        outputs.add(executable);
        if (map != null) {
            outputs.add(map);
        }

        FileNameRules.validateDistinctPaths(outputs, dosNames);
        return new BuildPlan(project, configuration, target, List.copyOf(steps), executable, List.copyOf(outputs),
                listings);
    }

    private static void fail(String code, String message, Object... arguments) {
        throw new DomainException(code, message, arguments);
    }
}
