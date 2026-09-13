/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.framegraph;

import java.util.List;
import java.util.Objects;

/** Immutable resource-dependency result produced before pass execution. */
public record CompiledFrameGraph(List<FrameGraphPassContract> orderedPasses,
                                 List<Dependency> dependencies) {
    public CompiledFrameGraph {
        orderedPasses = orderedPasses == null ? List.of() : List.copyOf(orderedPasses);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    public record Dependency(int producerIndex,
                             int consumerIndex,
                             FrameGraphResourceKey resource,
                             Hazard hazard) {
        public Dependency {
            Objects.requireNonNull(resource, "resource");
            Objects.requireNonNull(hazard, "hazard");
            if (producerIndex < 0 || consumerIndex < 0 || producerIndex >= consumerIndex) {
                throw new IllegalArgumentException("Frame-graph dependency must point from an earlier pass to a later pass");
            }
        }
    }

    public enum Hazard {
        READ_AFTER_WRITE,
        WRITE_AFTER_READ,
        WRITE_AFTER_WRITE
    }
}
