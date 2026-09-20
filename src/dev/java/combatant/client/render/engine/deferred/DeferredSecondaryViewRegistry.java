/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Frame-local registry for cascades/probes without baking their count into DeferredResource. */
public final class DeferredSecondaryViewRegistry {
    private final EnumMap<DeferredViewFamily, ArrayList<DeferredSecondaryView>> byFamily =
            new EnumMap<>(DeferredViewFamily.class);
    private long frameId = Long.MIN_VALUE;

    public void beginFrame(long frameId) {
        if (this.frameId == frameId) return;
        this.frameId = frameId;
        byFamily.clear();
    }

    public void reset() {
        frameId = Long.MIN_VALUE;
        byFamily.clear();
    }

    public void register(DeferredSecondaryView view) {
        if (view == null) return;
        for (ArrayList<DeferredSecondaryView> existingFamily : byFamily.values()) {
            for (DeferredSecondaryView existing : existingFamily) {
                if (existing.id().equals(view.id())) {
                    throw new IllegalArgumentException("Duplicate secondary view id " + view.id());
                }
            }
        }
        ArrayList<DeferredSecondaryView> views = byFamily.computeIfAbsent(view.family(), ignored -> new ArrayList<>());
        for (DeferredSecondaryView existing : views) {
            if (existing.index() == view.index()) {
                throw new IllegalArgumentException("Duplicate " + view.family() + " secondary view index " + view.index());
            }
        }
        views.add(view);
        views.sort(java.util.Comparator.comparingInt(DeferredSecondaryView::index));
    }

    public List<DeferredSecondaryView> views(DeferredViewFamily family) {
        ArrayList<DeferredSecondaryView> views = byFamily.get(family);
        return views == null ? List.of() : List.copyOf(views);
    }

    public int count(DeferredViewFamily family) {
        ArrayList<DeferredSecondaryView> views = byFamily.get(family);
        return views == null ? 0 : views.size();
    }

    public boolean has(DeferredViewFamily family) {
        return count(family) > 0;
    }

    public DeferredSecondaryView view(DeferredViewFamily family, int index) {
        ArrayList<DeferredSecondaryView> views = byFamily.get(family);
        if (views == null) return null;
        for (DeferredSecondaryView view : views) if (view.index() == index) return view;
        return null;
    }

    public DeferredSecondaryView find(String id) {
        if (id == null || id.isBlank()) return null;
        for (ArrayList<DeferredSecondaryView> views : byFamily.values()) {
            for (DeferredSecondaryView view : views) if (view.id().equals(id)) return view;
        }
        return null;
    }

    public long frameId() {
        return frameId;
    }
}
