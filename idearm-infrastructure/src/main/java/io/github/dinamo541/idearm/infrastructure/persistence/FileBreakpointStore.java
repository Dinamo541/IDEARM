package io.github.dinamo541.idearm.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.dinamo541.idearm.domain.debug.Breakpoint;
import io.github.dinamo541.idearm.domain.port.BreakpointStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * File-based persistence of breakpoints into .idearm/breakpoints.json.
 */
public final class FileBreakpointStore implements BreakpointStore {

    private static final String IDEARM_DIR = ".idearm";
    private static final String BREAKPOINTS_FILE = "breakpoints.json";

    private final ObjectMapper mapper;

    public FileBreakpointStore() {
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public List<Breakpoint> loadBreakpoints(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        Path file = projectRoot.resolve(IDEARM_DIR).resolve(BREAKPOINTS_FILE);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            List<BreakpointDto> dtos = mapper.readValue(file.toFile(), new TypeReference<>() {});
            List<Breakpoint> breakpoints = new ArrayList<>();
            for (BreakpointDto dto : dtos) {
                if (dto.path() != null && dto.line() > 0) {
                    breakpoints.add(new Breakpoint(dto.path(), dto.line(), dto.enabled()));
                }
            }
            return List.copyOf(breakpoints);
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public void saveBreakpoints(Path projectRoot, List<Breakpoint> breakpoints) {
        Objects.requireNonNull(projectRoot, "projectRoot cannot be null");
        Objects.requireNonNull(breakpoints, "breakpoints cannot be null");

        Path idearmDir = projectRoot.resolve(IDEARM_DIR);
        Path file = idearmDir.resolve(BREAKPOINTS_FILE);
        try {
            Files.createDirectories(idearmDir);
            List<BreakpointDto> dtos = breakpoints.stream()
                    .map(bp -> new BreakpointDto(bp.path(), bp.line(), bp.enabled()))
                    .toList();
            mapper.writeValue(file.toFile(), dtos);
        } catch (IOException e) {
            // Ignored or logged; saving breakpoints should not crash workbench operations
        }
    }

    /**
     * DTO for JSON serialization of breakpoints.
     */
    public record BreakpointDto(String path, int line, boolean enabled) {}
}
