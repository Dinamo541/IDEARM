package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.model.RecentItem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManageRecentItemsTest {
    @Test void reopeningMovesTheItemToTheFrontWithoutDuplicates() {
        var first = new RecentItem(RecentItem.Kind.FILE, Path.of("one.asm"));
        var second = new RecentItem(RecentItem.Kind.FILE, Path.of("two.asm"));
        assertEquals(List.of(first, second), ManageRecentItems.remember(List.of(second, first), first));
    }

    @Test void filesDoNotEvictProjects() {
        var project = new RecentItem(RecentItem.Kind.PROJECT, Path.of("project"));
        List<RecentItem> items = List.of(project);
        for (int i = 0; i < 45; i++) {
            items = ManageRecentItems.remember(items, new RecentItem(RecentItem.Kind.FILE, Path.of(i + ".asm")));
        }
        assertEquals(21, items.size());
        assertEquals("44.asm", items.getFirst().name());
        assertTrue(items.contains(project));
        assertFalse(items.stream().anyMatch(item -> item.name().equals("24.asm")));
    }

    @Test void normalizationDeduplicatesEquivalentPathsAndPreservesNewestOrder() {
        var first = new RecentItem(RecentItem.Kind.FILE, Path.of("project", "src", "..", "main.asm"));
        var same = new RecentItem(RecentItem.Kind.FILE, Path.of("project", "main.asm"));
        assertEquals(List.of(first), ManageRecentItems.normalize(List.of(first, same)));
    }
}
