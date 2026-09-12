/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess.graph;


import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.postprocess.PostProcessContext;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.features.module.Module;

import java.util.Set;

public final class LegacyPostProcessGraphPass implements PostProcessGraphPass {
    private final PostProcessPass delegate;
    private final String id;

    public LegacyPostProcessGraphPass(PostProcessPass delegate) {
        this.delegate = delegate;
        this.id = delegate.getClass().getName();
    }

    public PostProcessPass delegate() {
        return delegate;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int priority() {
        return delegate.getPriority();
    }

    @Override
    public PostProcessPass.Phase phase() {
        return delegate.getPhase();
    }

    @Override
    public Set<PostProcessResource> reads() {
        return Set.of(PostProcessResource.GRAPH_SOURCE_COLOR);
    }

    @Override
    public Set<PostProcessResource> writes() {
        return Set.of(PostProcessResource.GRAPH_DEST_COLOR);
    }

    @Override
    public PostProcessResolution resolution() {
        return PostProcessResolution.FULL;
    }

    @Override
    public boolean enabled(RenderFrameContext context) {
        return delegate.isActive();
    }

    @Override
    public boolean execute(RenderFrameContext context, CombatantRhi rhi, PostProcessGraphResources resources) {
        GpuTextureView src = resources.currentSource();
        GpuTextureView dst = resources.currentDestination();
        PostProcessContext legacyContext = resources.legacyContext();
        if (src == null || dst == null || legacyContext == null) return false;
        if (delegate instanceof Module module && !module.isEnabled()) return false;
        // Shared graph resources are not locally recoverable. Do not quarantine/cleanup a
        // module while the graph may contain partially written GPU resources.
        return delegate.render(legacyContext, src, dst);
    }
}
