package io.github.dinamo541.idearm.app.viewmodel;

import io.github.dinamo541.idearm.app.i18n.Message;
import io.github.dinamo541.idearm.application.ManageRecentItems;
import io.github.dinamo541.idearm.domain.model.RecentItem;
import io.github.dinamo541.idearm.domain.port.RecentItemsStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Serial background persistence keeps navigation responsive and writes in navigation order. */
public final class RecentItemsViewModel implements AutoCloseable {
    private final ObservableList<RecentItem> items = FXCollections.observableArrayList();
    private final ObservableList<RecentItem> readOnlyItems = FXCollections.unmodifiableObservableList(items);
    private final ReadOnlyObjectWrapper<Message> error = new ReadOnlyObjectWrapper<>();
    private final RecentItemsStore store;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private boolean writable = true;

    public RecentItemsViewModel(RecentItemsStore store) {
        this.store = store;
        // One small local file is read before the first navigation to preserve MRU order.
        if (store != null) {
            try { items.setAll(ManageRecentItems.normalize(store.load())); }
            catch (IOException failure) {
                writable = false; // Preserve a corrupt or newer history until the user explicitly clears it.
                error.set(Message.of("recent.readError"));
            }
        }
    }

    public ObservableList<RecentItem> getItems() { return readOnlyItems; }
    public ReadOnlyObjectProperty<Message> errorProperty() { return error.getReadOnlyProperty(); }

    public void remember(RecentItem.Kind kind, Path path) {
        items.setAll(ManageRecentItems.remember(items, new RecentItem(kind, path)));
        persist();
    }

    public void remove(RecentItem item) { items.remove(item); persist(); }
    public void clear() { items.clear(); writable = true; persist(); }

    private void persist() {
        if (store == null || !writable) return;
        var snapshot = List.copyOf(items);
        writer.execute(() -> {
            try {
                store.save(snapshot);
                FxDispatch.run(() -> error.set(null));
            } catch (IOException failure) {
                FxDispatch.run(() -> error.set(Message.of("recent.writeError")));
            }
        });
    }

    @Override public void close() { writer.close(); }
}
