package io.github.dinamo541.idearm.infrastructure.persistence;

import io.github.dinamo541.idearm.domain.model.RecentItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FileRecentItemsStoreTest {
    @TempDir Path temp;

    @Test void roundTripsUnicodePathsOrderAndKindsAcrossInstances() throws IOException {
        var file = temp.resolve("settings/recent.json");
        var items = List.of(new RecentItem(RecentItem.Kind.PROJECT, temp.resolve("Mi proyecto á")),
                new RecentItem(RecentItem.Kind.FILE, temp.resolve("Mi proyecto á/main.asm")));
        assertTrue(new FileRecentItemsStore(file).load().isEmpty());
        new FileRecentItemsStore(file).save(items);
        assertEquals(items, new FileRecentItemsStore(file).load());
        try (var files = Files.list(file.getParent())) { assertEquals(1, files.count()); }
    }

    @Test void clearingHistoryDoesNotDeleteAnyReferencedFile() throws IOException {
        var source = Files.writeString(temp.resolve("source.asm"), "mov ax, 1");
        var store = new FileRecentItemsStore(temp.resolve("recent.json"));
        store.save(List.of(new RecentItem(RecentItem.Kind.FILE, source)));
        store.save(List.of());
        assertTrue(store.load().isEmpty());
        assertEquals("mov ax, 1", Files.readString(source));
    }

    @Test void corruptAndFutureDataAreReportedAndNeverRewrittenByLoading() throws IOException {
        var file = temp.resolve("recent.json");
        for (var content : List.of("broken json", "{\"version\":2,\"items\":[]}",
                "{\"version\":1,\"items\":[{\"kind\":\"FILE\",\"path\":\"relative.asm\"}]}")) {
            Files.writeString(file, content);
            assertThrows(IOException.class, () -> new FileRecentItemsStore(file).load());
            assertEquals(content, Files.readString(file));
        }
    }
}
