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
        List<RenderPassNode> list = nodes.get(phase == null ? RenderPhase.NONE : phase);
        if (list == null || list.isEmpty()) return List.of();
        return List.copyOf(list);
    }

    public CompiledFrameGraph compile(RenderPhase phase) {
        List<RenderPassNode> phaseNodes = nodes(phase);
        if (phaseNodes.isEmpty()) return new CompiledFrameGraph(List.of(), List.of());
        ArrayList<FrameGraphPassContract> contracts = new ArrayList<>(phaseNodes.size());
        for (RenderPassNode node : phaseNodes) contracts.add(node.contract());
        return FrameGraphContractCompiler.compile(contracts);
    }

    public void clear() {
        nodes.clear();
    }
}
