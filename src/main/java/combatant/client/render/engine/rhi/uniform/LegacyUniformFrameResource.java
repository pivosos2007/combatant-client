/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.uniform;

/**
 * Adapter for old DynamicUniformStorage owners while they are migrated into CombatantUniformAllocator.
 */
public final class LegacyUniformFrameResource implements FrameResource {
    private final String name;
    private final Runnable flip;
    private boolean used = true;
    private boolean lazyTrackingEnabled;

    public LegacyUniformFrameResource(String name, Runnable flip) {
        this.name = name;
        this.flip = flip;
    }

    public void markUsed() {
        used = true;
        lazyTrackingEnabled = true;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean usedThisFrame() {
        return !lazyTrackingEnabled || used;
    }

    @Override
    public void onFramePresented() {
        if (lazyTrackingEnabled && !used) return;
        flip.run();
        used = false;
    }
}
