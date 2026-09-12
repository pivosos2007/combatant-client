/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects;

import combatant.client.render.engine.renderer.Renderer3D;

import java.util.HashMap;
import java.util.Map;

/** Current Renderer3D adapter layer for the CPU transient lifecycle backend. */
public final class CurrentTransientEffectBackend extends CpuTransientWorldEffectSystem {
    private final Map<String, EffectRenderer> renderers = new HashMap<>();

    public CurrentTransientEffectBackend() {
        super();
    }

    public CurrentTransientEffectBackend(EffectBudget budget) {
        super(budget);
    }

    public synchronized void registerRenderer(String type, EffectRenderer renderer) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Transient renderer type must not be blank");
        }
        if (renderer == null) {
            renderers.remove(type);
        } else {
            renderers.put(type, renderer);
        }
    }

    public void render(Renderer3D renderer, float tickDelta, long nowMs) {
        if (renderer == null) return;
        update(nowMs);
        for (TransientEffectDescriptor descriptor : snapshot()) {
            EffectRenderer adapter;
            synchronized (this) {
                adapter = renderers.get(descriptor.type());
            }
            if (adapter != null) {
                adapter.render(renderer, descriptor, tickDelta, nowMs);
            }
        }
    }

    @FunctionalInterface
    public interface EffectRenderer {
        void render(Renderer3D renderer,
                    TransientEffectDescriptor descriptor,
                    float tickDelta,
                    long nowMs);
    }
}
