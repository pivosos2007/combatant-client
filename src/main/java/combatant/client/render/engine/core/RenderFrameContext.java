/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.core;

import combatant.client.render.engine.core.policy.DepthProvider;
import combatant.client.render.engine.core.policy.FogProvider;
import combatant.client.render.engine.core.policy.LightPolicy;
import combatant.client.render.engine.core.policy.VisibilityProvider;
import combatant.client.render.sodium.SodiumFrameContext;

/**
 * Immutable per-phase context. Old RenderState should eventually become only a shim over this.
 */
public record RenderFrameContext(long frameId, float tickProgress, float frameDeltaTicks, float fixedDeltaTicks,
                                 RenderPhase phase, CameraContext camera, ViewportContext viewport,
                                 FramebufferContext framebuffer, DepthProvider depthProvider, FogProvider fogProvider,
                                 LightPolicy lightPolicy, VisibilityProvider visibilityProvider,
                                 SodiumFrameContext sodium) {
    private static final float TICKS_PER_SECOND = 20.0f;

    /**
     * Legacy alias for render interpolation progress.
     */
    public float tickDelta() {
        return tickProgress;
    }

    /**
     * Fractional progress inside the current game tick, used for world/entity interpolation.
     */
    @Override
    public float tickProgress() {
        return tickProgress;
    }

    /**
     * Time since previous rendered frame, in vanilla tick units.
     */
    @Override
    public float frameDeltaTicks() {
        return frameDeltaTicks;
    }

    public float frameDeltaSeconds() {
        return frameDeltaTicks / TICKS_PER_SECOND;
    }

    /**
     * Vanilla fixed delta in tick units, clamped by RenderTickCounter.Dynamic.
     */
    @Override
    public float fixedDeltaTicks() {
        return fixedDeltaTicks;
    }

    public RenderFrameContext withPhase(RenderPhase newPhase) {
        return new RenderFrameContext(frameId, tickProgress, frameDeltaTicks, fixedDeltaTicks,
                newPhase, camera, viewport, framebuffer,
                depthProvider, fogProvider, lightPolicy, visibilityProvider, sodium);
    }

    public RenderFrameContext withTiming(float newTickProgress,
                                         float newFrameDeltaTicks,
                                         float newFixedDeltaTicks) {
        return new RenderFrameContext(frameId, newTickProgress, newFrameDeltaTicks, newFixedDeltaTicks,
                phase, camera, viewport, framebuffer,
                depthProvider, fogProvider, lightPolicy, visibilityProvider, sodium);
    }
}
