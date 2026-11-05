/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.renderer.ui.draw.UiRect;
import java.util.HashMap;
import java.util.Map;

/** Bounds for one immutable captured-scene generation and replay viewport. */
final class UiBlurPlan {
    private record Profile(int quality, int offset) {}
    private record Demand(UiRect bounds, double area) {}
    private final Map<Profile, Demand> demands = new HashMap<>();

    void clear() { demands.clear(); }

    void add(int quality, float offset, UiRect bounds) {
        if (bounds == null || bounds.empty()) return;
        Profile profile = new Profile(quality, Float.floatToIntBits(offset));
        Demand previous = demands.get(profile);
        double area = (double) bounds.width() * bounds.height();
        demands.put(profile, previous == null ? new Demand(bounds, area)
                : new Demand(union(previous.bounds, bounds), previous.area + area));
    }

    UiRect bounds(int quality, float offset, UiRect requested) {
        if (requested == null) return null;
        Demand demand = demands.get(new Profile(quality, Float.floatToIntBits(offset)));
        if (demand == null) return requested;
        // Distant HUD islands must not turn small regional blurs into a full-screen pyramid.
        if ((double) demand.bounds.width() * demand.bounds.height() > demand.area * 2.0) return requested;
        return union(requested, demand.bounds);
    }

    private static UiRect union(UiRect a, UiRect b) {
        float x = Math.min(a.x(), b.x());
        float y = Math.min(a.y(), b.y());
        return new UiRect(x, y, Math.max(a.x() + a.width(), b.x() + b.width()) - x,
                Math.max(a.y() + a.height(), b.y() + b.height()) - y);
    }
}
