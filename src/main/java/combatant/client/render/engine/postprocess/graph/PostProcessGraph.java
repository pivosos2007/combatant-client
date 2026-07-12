/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess.graph;

import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.profiler.RenderCostProfiler;
import combatant.client.render.engine.rhi.CombatantRhi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PostProcessGraph implements AutoCloseable {
    private final List<PostProcessGraphPass> passes = new ArrayList<>();
    private final PostProcessGraphResources resources = new PostProcessGraphResources();
    private final HistoryBufferManager history = new HistoryBufferManager();

    public void add(PostProcessGraphPass pass) {
        if (pass == null) return;
        for (PostProcessGraphPass existing : passes) {
            if (existing.getId().equals(pass.getId())) return;
        }
        passes.add(pass);
        passes.sort(Comparator
                .comparingInt(PostProcessGraphPass::priority)
                .thenComparing(PostProcessGraphPass::id));
    }

    public void addLegacy(PostProcessPass pass) {
        if (pass != null) add(new LegacyPostProcessGraphPass(pass));
    }

    public boolean hasActivePass(PostProcessPass.Phase phase, RenderFrameContext context) {
        for (PostProcessGraphPass pass : passes) {
            if (pass.phase() == phase && pass.enabled(context)) return true;
        }
        return false;
    }

    /**
     * Executes one postprocess phase as a ping-pong graph.
     * <p>
     * Flow:
     * main color -> graph source -> active passes -> final graph source -> main color
     */
    public boolean execute(PostProcessPass.Phase phase,
                           float tickDelta,
                           RenderFrameContext context,
                           CombatantRhi rhi,
                           GraphCopy copy) {
        if (!hasActivePass(phase, context)) return false;
        try (RenderCostProfiler.Scope ignoredGraph = RenderCostProfiler.postPass("graph:" + phase)) {
            if (!resources.prepare(phase, tickDelta)) return false;

            GpuTextureView mainColor = resources.mainColor();
            GpuTextureView source = resources.currentSource();
            if (mainColor == null || source == null) return false;

            copy.copy(mainColor, source);
            resources.resetPingPong();

            boolean anyApplied = false;
            for (PostProcessGraphPass pass : passes) {
                if (pass.phase() != phase || !pass.enabled(context)) continue;
                boolean applied;
                try (RenderCostProfiler.Scope ignoredPass = RenderCostProfiler.postPass(pass.getId())) {
                    applied = pass.execute(context, rhi, resources);
                }
                if (applied) {
                    anyApplied = true;
                    resources.advancePingPong();
                }
            }

            if (!anyApplied) return false;
            GpuTextureView finalColor = resources.finalColor();
            if (finalColor != null && mainColor != null) {
                copy.copy(finalColor, mainColor);
            }
            return true;
        }
    }

    public HistoryBufferManager history() {
        return history;
    }

    public List<PostProcessGraphPass> passes() {
        return List.copyOf(passes);
    }

    public PostProcessGraphResources resources() {
        return resources;
    }

    @Override
    public void close() {
        resources.close();
    }

    @FunctionalInterface
    public interface GraphCopy {
        void copy(GpuTextureView src, GpuTextureView dst);
    }
}
