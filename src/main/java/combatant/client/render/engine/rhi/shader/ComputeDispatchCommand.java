/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

import java.util.List;

public record ComputeDispatchCommand(String label,
                                     RhiComputePipeline pipeline,
                                     int groupsX,
                                     int groupsY,
                                     int groupsZ,
                                     List<StorageBinding> storageBindings) {
    public ComputeDispatchCommand {
        label = label == null || label.isBlank() ? "combatant-compute" : label;
        if (pipeline == null) throw new IllegalArgumentException("pipeline");
        groupsX = Math.max(1, groupsX);
        groupsY = Math.max(1, groupsY);
        groupsZ = Math.max(1, groupsZ);
        storageBindings = storageBindings == null || storageBindings.isEmpty()
                ? List.of()
                : List.copyOf(storageBindings);
    }
}
