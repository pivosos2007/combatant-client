/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.framegraph;

import combatant.client.render.engine.core.RenderPhase;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class PassScheduler {
    private final EnumMap<RenderPhase, List<RenderPassNode>> nodes = new EnumMap<>(RenderPhase.class);

    public void add(RenderPassNode node) {
        if (node == null) return;
        nodes.computeIfAbsent(node.phase(), ignored -> new ArrayList<>()).add(node);
    }

    public List<RenderPassNode> nodes(RenderPhase phase) {
        List<RenderPassNode> list = nodes.get(phase);
        if (list == null || list.isEmpty()) return List.of();
        return List.copyOf(list);
    }

    public void clear() {
        nodes.clear();
    }
}
