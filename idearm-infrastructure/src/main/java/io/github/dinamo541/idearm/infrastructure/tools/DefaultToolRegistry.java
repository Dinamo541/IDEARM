package io.github.dinamo541.idearm.infrastructure.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import io.github.dinamo541.idearm.domain.DomainException;
import io.github.dinamo541.idearm.domain.execution.DosBoxDialects;
import io.github.dinamo541.idearm.domain.model.HostKind;
import io.github.dinamo541.idearm.domain.model.ToolInstallation;
import io.github.dinamo541.idearm.domain.port.ToolRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The tools this machine offers: the ones the user registered, kept in {@code %APPDATA%\IDEARM\tools.toml}, and
 * the ones found by {@link ToolDetector}. A registered tool always wins over a detected one for the same role.
 *
 * <p>Only registered tools are saved: a detected tool is found again on every start, so writing it down would
 * only freeze a path that may later move.
 */
public final class DefaultToolRegistry implements ToolRegistry {

    static final String SOURCE_DETECTED = "detected";
    static final String SOURCE_REGISTERED = "registered";

    private final Map<String, ToolInstallation> tools = new ConcurrentHashMap<>();
    private final boolean autoDetect;
    private final Path configFile;
    private volatile boolean detected = false;

    public DefaultToolRegistry(boolean autoDetect) {
        this(autoDetect, globalConfigPath());
    }

    public DefaultToolRegistry(boolean autoDetect, Path configFile) {
        this.autoDetect = autoDetect;
        this.configFile = configFile;
    }

    public static DefaultToolRegistry system() {
        var registry = new DefaultToolRegistry(true);
        registry.loadFromGlobalConfig();
        return registry;
    }

    public static DefaultToolRegistry empty() {
        return new DefaultToolRegistry(false, null);
    }

    public void register(ToolInstallation tool) {
        tools.put(tool.toolId().toLowerCase(Locale.ROOT), tool);
    }

    public void register(String role, ToolInstallation tool) {
        tools.put(role.toLowerCase(Locale.ROOT), tool);
    }

    @Override
    public Optional<ToolInstallation> find(String toolId) {
        String key = toolId.toLowerCase(Locale.ROOT);
        if (DosBoxDialects.AUTO.equals(key)) {
            // "Any DOSBox" follows the IDE's preference (0.74-3 first), not the order the folders were searched in,
            // so every dialect installed on this machine must be known before choosing.
            ensureDetected();
            return automaticDosBox();
        }
        ToolInstallation direct = tools.get(key);
        if (direct != null) {
            return Optional.of(direct);
        }
        if (ensureDetected()) {
            return find(toolId);
        }
        return Optional.empty();
    }

    @Override
    public Map<String, ToolInstallation> all() {
        ensureDetected();
        var all = new java.util.HashMap<>(tools);
        automaticDosBox().ifPresent(dosbox -> all.put(DosBoxDialects.AUTO, dosbox));
        return Map.copyOf(all);
    }

    private Optional<ToolInstallation> automaticDosBox() {
        for (String dialect : DosBoxDialects.PREFERENCE) {
            ToolInstallation tool = tools.get(dialect);
            if (tool != null) {
                return Optional.of(tool);
            }
        }
        // Older registries used these role names.
        for (String alias : List.of("dosboxx", "staging", DosBoxDialects.AUTO)) {
            ToolInstallation tool = tools.get(alias);
            if (tool != null) {
                return Optional.of(tool);
            }
        }
        return Optional.empty();
    }

    /**
     * Registers every tool found in a folder the user picked, and its subfolders, then saves the registry.
     * Tools found there replace the ones used before for the same role.
     */
    @Override
    public List<ToolInstallation> registerFolder(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            throw new DomainException("tools.folder.missing", "Tool folder not found: " + folder, folder);
        }
        ensureDetected();
        var added = new ArrayList<ToolInstallation>();
        for (var entry : ToolDetector.detectUnder(folder.toAbsolutePath().normalize()).entrySet()) {
            ToolInstallation found = entry.getValue().getFirst();
            ToolInstallation registered = new ToolInstallation(found.toolId(), found.version(), found.executable(),
                    found.hostKind(), found.companions(), found.sha256(), SOURCE_REGISTERED);
            register(entry.getKey(), registered);
            if (added.stream().noneMatch(tool -> tool.executable().equals(registered.executable()))) {
                added.add(registered);
            }
        }
        if (!added.isEmpty()) {
            try {
                saveToGlobalConfig();
            } catch (IOException failure) {
                throw new DomainException("tools.save.failed",
                        "The tools were found but could not be saved to " + configFile + ": " + failure.getMessage(),
                        failure, configFile, failure.getMessage());
            }
        }
        return List.copyOf(added);
    }

    /** Detects once, lazily; true when this call did the detection. */
    private boolean ensureDetected() {
        if (!autoDetect || detected) {
            return false;
        }
        synchronized (this) {
            if (detected) {
                return false;
            }
            for (var entry : ToolDetector.detectAll().entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    tools.putIfAbsent(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().getFirst());
                }
            }
            detected = true;
            return true;
        }
    }

    public void loadFromGlobalConfig() {
        if (configFile != null && Files.isRegularFile(configFile)) {
            try {
                loadFromFile(configFile);
            } catch (IOException | RuntimeException ignored) {
                // A damaged file must not stop the IDE; detection still finds the usual tools.
            }
        }
    }

    public void saveToGlobalConfig() throws IOException {
        if (configFile != null) {
            Files.createDirectories(configFile.toAbsolutePath().getParent());
            saveToFile(configFile);
        }
    }

    public void loadFromFile(Path path) throws IOException {
        TomlMapper mapper = new TomlMapper();
        JsonNode root = mapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
        if (root == null || !root.isObject()) return;

        JsonNode toolsNode = root.get("tools");
        if (toolsNode == null || !toolsNode.isArray()) return;
        for (JsonNode item : toolsNode) {
            String id = item.path("id").asText("");
            String role = item.path("role").asText(id);
            String version = item.path("version").asText(ToolDetector.UNKNOWN_VERSION);
            String executablePath = item.path("executable").asText("");
            if (id.isBlank() || executablePath.isBlank()) {
                continue;
            }
            HostKind host;
            try {
                host = HostKind.valueOf(item.path("host").asText("DOS_REAL").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknownHost) {
                continue;
            }
            Map<String, Path> companions = new LinkedHashMap<>();
            JsonNode companionsNode = item.get("companions");
            if (companionsNode != null && companionsNode.isObject()) {
                companionsNode.fieldNames().forEachRemaining(name ->
                        companions.put(name, Path.of(companionsNode.get(name).asText())));
            }
            String sha256 = item.hasNonNull("sha256") ? item.get("sha256").asText() : null;
            register(role.isBlank() ? id : role,
                    new ToolInstallation(id, version, Path.of(executablePath), host, companions, sha256, SOURCE_REGISTERED));
        }
    }

    public void saveToFile(Path path) throws IOException {
        TomlMapper mapper = new TomlMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode toolsArray = root.putArray("tools");

        var roles = new TreeMap<>(tools);
        for (var entry : roles.entrySet()) {
            ToolInstallation tool = entry.getValue();
            if (SOURCE_DETECTED.equals(tool.source())) {
                continue;
            }
            ObjectNode item = toolsArray.addObject();
            item.put("role", entry.getKey());
            item.put("id", tool.toolId());
            item.put("version", tool.version());
            item.put("executable", tool.executable().toString());
            item.put("host", tool.hostKind().name());
            if (tool.sha256() != null) {
                item.put("sha256", tool.sha256());
            }
            if (!tool.companions().isEmpty()) {
                ObjectNode compNode = item.putObject("companions");
                tool.companions().forEach((k, v) -> compNode.put(k, v.toString()));
            }
        }

        // Written beside the file and moved over it, so a crash never leaves half a registry.
        Path target = path.toAbsolutePath();
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, mapper.writeValueAsString(root), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path globalConfigPath() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, "IDEARM", "tools.toml");
        }
        return Path.of(System.getProperty("user.home"), ".config", "idearm", "tools.toml");
    }
}
