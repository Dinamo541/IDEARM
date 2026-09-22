package io.github.dinamo541.idearm.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.model.*;
import io.github.dinamo541.idearm.domain.port.ProjectRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Schema-aware TOML persistence. Unsupported fields are rejected instead of silently discarded. */
public final class TomlProjectRepository implements ProjectRepository {
    public static final int CURRENT_SCHEMA = 1;
    private static final long MAX_FILE_BYTES = 1024 * 1024;
    private final TomlMapper mapper = new TomlMapper();

    /** A folder is a project when its description file is there; reading it is a separate question. */
    @Override public boolean exists(Path projectRoot) {
        return Files.isRegularFile(projectRoot.resolve("idearm.toml"), LinkOption.NOFOLLOW_LINKS);
    }

    @Override public Project load(Path projectRoot) {
        Path file = projectRoot.resolve("idearm.toml");
        try {
            verifyFile(file);
            if (Files.size(file) > MAX_FILE_BYTES) throw invalid("Project file exceeds the 1 MiB limit.");
            JsonNode root = mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
            if (root == null || !root.isObject()) throw invalid("Project file must contain a TOML table.");
            int schema = integer(root, "schema", -1);
            if (schema != CURRENT_SCHEMA) throw new DomainException("project.schema.unsupported", "Unsupported project schema: " + schema, schema);
            fields(root, "schema", "project", "target", "toolchain", "sources", "resources", "build", "run", "debug", "dist");
            JsonNode project = table(root, "project", true); fields(project, "name", "version");
            JsonNode target = table(root, "target", true); fields(target, "profile", "cpu");
            JsonNode toolchain = table(root, "toolchain", true); fields(toolchain, "id", "version");
            JsonNode sources = table(root, "sources", true); fields(sources, "entry", "modules", "include", "exclude");
            JsonNode resources = table(root, "resources", false); fields(resources, "files");
            JsonNode build = table(root, "build", false);
            Map<String, BuildConfiguration> configurations = new LinkedHashMap<>();
            if (build.isEmpty()) {
                configurations.put("debug", BuildConfiguration.debug());
                configurations.put("release", BuildConfiguration.release());
            } else {
                for (String name : names(build)) {
                    if (!name.matches("[A-Za-z][A-Za-z0-9_-]*")) throw invalid("Invalid build configuration: " + name, name);
                    JsonNode configuration = table(build, name, true);
                    fields(configuration, "debug-info", "listing", "map", "defines", "extra-args");
                    configurations.put(name, new BuildConfiguration(bool(configuration, "debug-info", false),
                            bool(configuration, "listing", false), bool(configuration, "map", false),
                            strings(table(configuration, "defines", false)), array(configuration, "extra-args")));
                }
            }
            JsonNode run = table(root, "run", false); fields(run, "environment", "isolation", "keep-open", "cycles", "memsize", "args");
            JsonNode debug = table(root, "debug", false); fields(debug, "backend");
            JsonNode dist = table(root, "dist", false); fields(dist, "launcher", "zip");
            return new Project(schema,
                    new ProjectInfo(string(project, "name", null), string(project, "version", "0.1.0")),
                    new TargetSelection(string(target, "profile", null), string(target, "cpu", "8086")),
                    new ToolchainSelection(string(toolchain, "id", null), string(toolchain, "version", ">=3.2")),
                    new Sources(string(sources, "entry", null), array(sources, "modules"), array(sources, "include"), array(sources, "exclude")),
                    new Resources(array(resources, "files")), configurations,
                    new RunConfiguration(string(run, "environment", "dosbox"), string(run, "isolation", "required"),
                            bool(run, "keep-open", true), string(run, "cycles", "auto"), integer(run, "memsize", 16), array(run, "args")),
                    new DebugConfiguration(string(debug, "backend", "external")),
                    new DistConfiguration(bool(dist, "launcher", true), bool(dist, "zip", false)));
        } catch (DomainException failure) { throw failure; }
        catch (IOException | IllegalArgumentException failure) { throw new DomainException("project.toml.invalid", "Cannot read project " + file + ": " + failure.getMessage(), failure, failure.getMessage(), file); }
    }

    @Override public void save(Path projectRoot, Project project) {
        if (project.schema() != CURRENT_SCHEMA) throw new DomainException("project.schema.unsupported", "Unsupported project schema: " + project.schema(), project.schema());
        Path file = projectRoot.resolve("idearm.toml");
        Path temporary = null;
        try {
            // Do not overwrite unknown future settings or malformed hand-authored data.
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) load(projectRoot);
            if (!Files.isDirectory(projectRoot, LinkOption.NOFOLLOW_LINKS)
                    || !linkFree(projectRoot)) throw new IOException("project.root.unsafe");
            String content = mapper.writeValueAsString(serialize(project));
            temporary = Files.createTempFile(projectRoot, ".idearm-", ".toml.tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException failure) { throw new DomainException("project.save.failed", "Cannot save project: " + file, failure, file); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
    }

    private ObjectNode serialize(Project model) {
        ObjectNode root = mapper.createObjectNode(); root.put("schema", model.schema());
        root.putObject("project").put("name", model.info().name()).put("version", model.info().version());
        root.putObject("target").put("profile", model.target().profile()).put("cpu", model.target().cpu());
        root.putObject("toolchain").put("id", model.toolchain().id()).put("version", model.toolchain().version());
        ObjectNode sources = root.putObject("sources"); sources.put("entry", model.sources().entry());
        put(sources, "modules", model.sources().modules()); put(sources, "include", model.sources().include()); put(sources, "exclude", model.sources().exclude());
        put(root.putObject("resources"), "files", model.resources().files());
        ObjectNode build = root.putObject("build");
        model.build().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ObjectNode config = build.putObject(entry.getKey()); BuildConfiguration value = entry.getValue();
            config.put("debug-info", value.debugInfo()).put("listing", value.listing()).put("map", value.map());
            put(config, "extra-args", value.extraArgs());
            ObjectNode defines = config.putObject("defines");
            value.defines().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(define -> defines.put(define.getKey(), define.getValue()));
        });
        RunConfiguration run = model.run();
        ObjectNode runNode = root.putObject("run");
        runNode.put("environment", run.environment()).put("isolation", run.isolation()).put("keep-open", run.keepOpen()).put("cycles", run.cycles()).put("memsize", run.memsize());
        put(runNode, "args", run.args());
        root.putObject("debug").put("backend", model.debug().backend());
        root.putObject("dist").put("launcher", model.dist().launcher()).put("zip", model.dist().zip());
        return root;
    }

    private static void verifyFile(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || !linkFree(file)) throw new IOException("project.file.unsafeOrMissing");
    }

    /** No link or junction on the way to the path; Windows 8.3 short names (C:\USERS\JUANPE~1) are not links. */
    private static boolean linkFree(Path path) throws IOException {
        return path.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(path.toRealPath());
    }
    private ObjectNode table(JsonNode parent, String name, boolean required) {
        JsonNode node = parent.get(name);
        if (node == null && !required) return mapper.createObjectNode();
        if (node == null || !node.isObject()) throw invalid("Expected table: " + name, name);
        return (ObjectNode) node;
    }
    private static void fields(JsonNode node, String... allowed) {
        Set<String> known = Set.of(allowed);
        for (String field : names(node)) if (!known.contains(field)) throw invalid("Unknown project field: " + field, field);
    }
    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>(); node.fieldNames().forEachRemaining(names::add); return names;
    }
    private static String string(JsonNode node, String name, String fallback) {
        JsonNode value = node.get(name);
        if (value == null && fallback != null) return fallback;
        if (value == null || !value.isTextual()) throw invalid("Expected string: " + name, name);
        return value.textValue();
    }
    private static boolean bool(JsonNode node, String name, boolean fallback) {
        JsonNode value = node.get(name); if (value == null) return fallback;
        if (!value.isBoolean()) throw invalid("Expected boolean: " + name, name); return value.booleanValue();
    }
    private static int integer(JsonNode node, String name, int fallback) {
        JsonNode value = node.get(name); if (value == null) return fallback;
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw invalid("Expected integer: " + name, name); return value.intValue();
    }
    private static List<String> array(JsonNode node, String name) {
        JsonNode value = node.get(name); if (value == null) return List.of();
        if (!value.isArray()) throw invalid("Expected string array: " + name, name);
        List<String> result = new ArrayList<>();
        for (JsonNode element : value) {
            if (!element.isTextual()) throw invalid("Expected string array: " + name, name);
            result.add(element.textValue());
        }
        return List.copyOf(result);
    }
    private static Map<String, String> strings(JsonNode object) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : names(object)) result.put(name, string(object, name, null));
        return result;
    }
    private static void put(ObjectNode parent, String name, List<String> values) {
        ArrayNode array = parent.putArray(name); values.forEach(array::add);
    }
    private static DomainException invalid(String message, Object... arguments) {
        return new DomainException("project.toml.invalid", message, arguments);
    }
}
