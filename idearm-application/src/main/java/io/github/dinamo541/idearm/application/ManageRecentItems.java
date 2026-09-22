package io.github.dinamo541.idearm.application;

import io.github.dinamo541.idearm.domain.model.RecentItem;
import java.util.ArrayList;
import java.util.List;

/** Stable MRU ordering; projects and individual files each retain their last twenty entries. */
public final class ManageRecentItems {
    public static final int LIMIT_PER_KIND = 20;

    private ManageRecentItems() {}

    public static List<RecentItem> normalize(List<RecentItem> items) {
        var result = new ArrayList<RecentItem>();
        for (var item : items) {
            if (!result.contains(item)
                    && result.stream().filter(other -> other.kind() == item.kind()).count() < LIMIT_PER_KIND) {
                result.add(item);
            }
        }
        return List.copyOf(result);
    }

    public static List<RecentItem> remember(List<RecentItem> items, RecentItem opened) {
        var result = new ArrayList<RecentItem>();
        result.add(opened);
        result.addAll(items);
        return normalize(result);
    }
}
