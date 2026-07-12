/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.nondraggable.impl.tab;

import combatant.client.render.engine.animation.AnimationUtility;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class TabListAnimator {
    private final Map<UUID, RowState> rows = new HashMap<>();
    private float panelPresence;

    float updatePanel(boolean visible) {
        float dt = AnimationUtility.deltaTime();
        panelPresence = AnimationUtility.approach(panelPresence, visible ? 1f : 0f, dt, 11f);
        panelPresence = AnimationUtility.snap(panelPresence, visible ? 1f : 0f, 0.002f);
        return panelPresence;
    }

    RowState row(UUID id) {
        return rows.computeIfAbsent(id, ignored -> new RowState());
    }

    void updateRows(Set<UUID> visibleIds) {
        float dt = AnimationUtility.deltaTime();
        Set<UUID> live = visibleIds != null ? visibleIds : Set.of();
        for (UUID id : live) {
            RowState state = row(id);
            state.presence = AnimationUtility.approach(state.presence, 1f, dt, 12f);
            state.presence = AnimationUtility.snap(state.presence, 1f, 0.003f);
        }

        Iterator<Map.Entry<UUID, RowState>> it = rows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, RowState> entry = it.next();
            if (live.contains(entry.getKey())) continue;
            RowState state = entry.getValue();
            state.presence = AnimationUtility.approach(state.presence, 0f, dt, 10f);
            state.presence = AnimationUtility.snap(state.presence, 0f, 0.003f);
            if (state.presence <= 0.001f) {
                it.remove();
            }
        }
    }

    boolean hasVisibleRows() {
        for (RowState state : rows.values()) {
            if (state.presence > 0.01f) return true;
        }
        return false;
    }

    static final class RowState {
        float presence;
    }
}
