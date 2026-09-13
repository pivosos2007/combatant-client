/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.framegraph;

import combatant.client.render.engine.core.RenderPhase;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/** Resource contract for one logical pass. Execution code is kept out of the contract. */
public record FrameGraphPassContract(RenderPhase phase,
                                     String label,
                                     List<FrameGraphResourceUse> resources,
                                     boolean sideEffect) {
    public FrameGraphPassContract {
        phase = phase == null ? RenderPhase.NONE : phase;
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Frame-graph pass label must not be blank");
        label = label.trim();
        resources = resources == null ? List.of() : List.copyOf(resources);
        HashMap<FrameGraphResourceKey, FrameGraphAccess> seen = new HashMap<>();
        for (FrameGraphResourceUse use : resources) {
            Objects.requireNonNull(use, "resources contains null");
            FrameGraphAccess previous = seen.put(use.resource(), use.access());
            if (previous != null) {
                throw new IllegalArgumentException("Pass '" + label + "' declares resource '"
                        + use.resource().name() + "' more than once; use READ_WRITE when both access modes are required");
            }
        }
    }

    public static FrameGraphPassContract sideEffect(RenderPhase phase, String label) {
        return new FrameGraphPassContract(phase, label, List.of(), true);
    }
}
