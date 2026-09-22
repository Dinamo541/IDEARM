package io.github.dinamo541.idearm.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dinamo541.idearm.domain.model.RecentItem;
import io.github.dinamo541.idearm.domain.port.RecentItemsStore;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Versioned UTF-8 history. A failed write never replaces the previous complete file. */
public final class FileRecentItemsStore implements RecentItemsStore {
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();

    public FileRecentItemsStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    @Override public List<RecentItem> load() throws IOException {
        if (Files.notExists(file)) return List.of();
        if (Files.size(file) > 1_048_576) throw new IOException("Recent history exceeds 1 MiB.");
        var root = mapper.readTree(file.toFile());
        if (root == null || root.path("version").asInt() != 1 || !root.path("items").isArray()) {
            throw new IOException("Unsupported or damaged recent history.");
        }
        var items = new ArrayList<RecentItem>();
        try {
            for (var node : root.path("items")) {
                var path = Path.of(node.required("path").asText());
                if (!path.isAbsolute()) throw new IllegalArgumentException("History paths must be absolute.");
                items.add(new RecentItem(RecentItem.Kind.valueOf(node.required("kind").asText()), path));
            }
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid recent history entry.", failure);
        }
        return List.copyOf(items);
    }

    @Override public void save(List<RecentItem> items) throws IOException {
        var root = mapper.createObjectNode().put("version", 1);
        var entries = root.putArray("items");
        for (var item : items) {
            entries.addObject().put("kind", item.kind().name()).put("path", item.path().toString());
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "recent-", ".tmp");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), root);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
