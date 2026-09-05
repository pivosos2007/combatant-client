/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable native-shader resource layout used for GL validation and Vulkan descriptors. */
public record ShaderResourceLayout(List<ShaderResourceSlot> slots) {
    public static final ShaderResourceLayout EMPTY = new ShaderResourceLayout(List.of());

    public ShaderResourceLayout {
        slots = slots == null || slots.isEmpty() ? List.of() : List.copyOf(slots);
        Set<Integer> bindings = new HashSet<>();
        for (ShaderResourceSlot slot : slots) {
            if (slot == null) throw new IllegalArgumentException("resource layout contains null slot");
            if (!bindings.add(slot.binding())) {
                throw new IllegalArgumentException("duplicate native shader binding " + slot.binding());
            }
        }
    }

    public ShaderResourceSlot slot(int binding) {
        for (ShaderResourceSlot slot : slots) if (slot.binding() == binding) return slot;
        return null;
    }

    public int count(ShaderResourceKind kind) {
        int count = 0;
        for (ShaderResourceSlot slot : slots) if (slot.kind() == kind) count++;
        return count;
    }
}
