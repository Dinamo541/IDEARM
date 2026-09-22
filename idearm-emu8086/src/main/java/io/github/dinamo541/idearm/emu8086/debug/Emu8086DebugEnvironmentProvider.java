package io.github.dinamo541.idearm.emu8086.debug;

import io.github.dinamo541.idearm.domain.debug.DebugEvent;
import io.github.dinamo541.idearm.domain.debug.DebugLaunchSpec;
import io.github.dinamo541.idearm.domain.model.TargetProfile;
import io.github.dinamo541.idearm.domain.port.DebugEnvironmentProvider;
import io.github.dinamo541.idearm.domain.port.DebugSession;
import io.github.dinamo541.idearm.emu8086.cpu.Cpu8086;
import io.github.dinamo541.idearm.emu8086.cpu.CpuRegisters;
import io.github.dinamo541.idearm.emu8086.cpu.RealModeMemory;
import io.github.dinamo541.idearm.emu8086.dos.DosInterruptHandler;
import io.github.dinamo541.idearm.emu8086.loader.LoadedProgram;
import io.github.dinamo541.idearm.emu8086.loader.ProgramLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Execution environment provider for in-process 8086 real-mode CPU emulation and debugging.
 */
public final class Emu8086DebugEnvironmentProvider implements DebugEnvironmentProvider {

    public static final String ID = "emu8086";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supports(TargetProfile profile) {
        if (profile == null) return false;
        // Supports 16-bit DOS targets
        return profile.codeMode() == 16;
    }

    @Override
    public DebugSession launchDebug(DebugLaunchSpec spec) {
        return launchDebug(spec, e -> {});
    }

    @Override
    public DebugSession launchDebug(DebugLaunchSpec spec, Consumer<DebugEvent> events) {
        Objects.requireNonNull(spec, "spec cannot be null");
        Consumer<DebugEvent> eventSink = (events != null) ? events : e -> {};

        byte[] binaryData;
        try {
            binaryData = Files.readAllBytes(spec.executable());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read debug executable: " + spec.executable(), e);
        }

        // Search for listing (.LST) file next to the executable or in build/staging
        SourceMap sourceMap = locateAndParseSourceMap(spec);

        RealModeMemory memory = new RealModeMemory();
        CpuRegisters registers = new CpuRegisters();
        LoadedProgram program = ProgramLoader.load(binaryData, memory, registers, spec.args());

        Cpu8086 cpu = new Cpu8086(registers, memory);
        DosInterruptHandler dosHandler = new DosInterruptHandler();
        cpu.setInterruptHandler(dosHandler);

        return new Emu8086DebugSession(
                cpu,
                memory,
                program,
                sourceMap,
                spec.breakpoints(),
                dosHandler,
                eventSink
        );
    }

    /**
     * The source map of the entry module.
     *
     * <p>The build reports which listing belongs to which source, entry module first; only that module's
     * offsets line up with the start of the code segment. Callers that do not know the listings get a search
     * next to the executable and in the build folder.
     */
    private SourceMap locateAndParseSourceMap(DebugLaunchSpec spec) {
        for (Map.Entry<String, Path> listing : spec.listings().entrySet()) {
            if (Files.isRegularFile(listing.getValue())) {
                List<String> source = readLines(spec.projectRoot().resolve(listing.getKey()));
                return SourceMap.parseListing(listing.getKey(), readListing(listing.getValue()), source);
            }
            break;
        }

        Path exePath = spec.executable();
        String exeName = exePath.getFileName().toString();
        int dot = exeName.lastIndexOf('.');
        String baseName = dot > 0 ? exeName.substring(0, dot) : exeName;
        Path buildDirectory = exePath.getParent() != null ? exePath.getParent().getParent() : null;

        var candidates = new ArrayList<Path>();
        candidates.add(exePath.resolveSibling(baseName + ".lst"));
        if (buildDirectory != null) {
            candidates.add(buildDirectory.resolve("lst").resolve(baseName + ".lst"));
        }
        candidates.add(spec.projectRoot().resolve("build").resolve("debug").resolve("lst").resolve(baseName + ".lst"));
        candidates.add(spec.stagingDirectory().resolve(baseName + ".lst"));

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return SourceMap.parseListing(baseName + ".asm", readListing(candidate));
            }
        }
        return SourceMap.empty();
    }

    /** Listings are CP437 text whose offsets and line numbers are ASCII; Latin-1 reads any byte. */
    private static String readListing(Path listing) {
        try {
            return Files.readString(listing, StandardCharsets.ISO_8859_1);
        } catch (IOException unreadable) {
            return "";
        }
    }

    private static List<String> readLines(Path source) {
        try {
            return io.github.dinamo541.idearm.domain.files.TextDecoding.decode(Files.readAllBytes(source)).text()
                    .lines().toList();
        } catch (IOException unreadable) {
            return List.of();
        }
    }
}
