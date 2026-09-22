package io.github.dinamo541.idearm.domain.port;

import io.github.dinamo541.idearm.domain.model.RecentItem;
import java.io.IOException;
import java.util.List;

/** Persistence of local navigation history, independent of any project. */
public interface RecentItemsStore {
    List<RecentItem> load() throws IOException;
    void save(List<RecentItem> items) throws IOException;
}
