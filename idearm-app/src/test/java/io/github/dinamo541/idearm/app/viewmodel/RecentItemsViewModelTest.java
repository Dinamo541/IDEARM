package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.domain.model.RecentItem;
import io.github.dinamo541.idearm.domain.port.RecentItemsStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecentItemsViewModelTest {
    @Test void rapidNavigationPersistsInOrderAndCloseFlushesTheLastChange() {
        var store = new MemoryStore();
        var vm = new RecentItemsViewModel(store);
        for (int i = 0; i < 30; i++) vm.remember(RecentItem.Kind.FILE, Path.of(i + ".asm"));
        vm.remove(vm.getItems().getFirst());
        vm.close();
        assertEquals(List.copyOf(vm.getItems()), store.items);
        assertEquals("28.asm", store.items.getFirst().name());
        assertThrows(UnsupportedOperationException.class, () -> vm.getItems().clear());
    }

    @Test void damagedHistoryIsProtectedUntilExplicitlyCleared() {
        var store = new MemoryStore(); store.damaged = true;
        var vm = new RecentItemsViewModel(store);
        assertEquals("recent.readError", vm.errorProperty().get().key());
        vm.remember(RecentItem.Kind.FILE, Path.of("new.asm"));
        vm.close();
        assertEquals(0, store.writes);
        var reset = new RecentItemsViewModel(store);
        reset.clear(); reset.close();
        assertEquals(1, store.writes);
        assertTrue(store.items.isEmpty());
    }

    private static class MemoryStore implements RecentItemsStore {
        List<RecentItem> items = List.of();
        int writes;
        boolean damaged;
        @Override public List<RecentItem> load() throws IOException {
            if (damaged) throw new IOException("damaged");
            return items;
        }
        @Override public void save(List<RecentItem> items) { this.items = List.copyOf(items); writes++; }
    }
}
