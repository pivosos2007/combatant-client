/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess.graph;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.postprocess.PostProcessBackendResourceOwner;
import combatant.client.render.engine.postprocess.PostProcessExecutionContext;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.profiler.RenderCostProfiler;
import combatant.client.render.engine.profiler.TracyGpuProfiler;
import combatant.client.render.engine.rhi.CombatantRhi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Ordered ping-pong executor for registered post-process passes. */
public final class PostProcessGraph implements AutoCloseable {
    private final List<PostProcessPass> passes = new ArrayList<>();
    private final List<PostProcessPass> activePasses = new ArrayList<>();
    private final PostProcessGraphResources resources = new PostProcessGraphResources();

    public void add(PostProcessPass pass) {
        if (pass == null) return;
        for (PostProcessPass existing : passes) {
            if (existing == pass) return;
        }
        passes.add(pass);
        passes.sort(Comparator
                .comparingInt(PostProcessPass::getPriority)
                .thenComparing(passEntry -> passEntry.getClass().getName()));
    }

    public boolean execute(PostProcessPass.Phase phase,
                           float tickDelta,
                           CombatantRhi rhi,
                           GraphCopy copy) {
        return execute(phase, tickDelta, rhi, copy, pass -> true);
    }

    public boolean execute(PostProcessPass.Phase phase,
                           float tickDelta,
                           CombatantRhi rhi,
                           GraphCopy copy,
                           Predicate<? super PostProcessPass> selector) {
        activePasses.clear();
        for (PostProcessPass pass : passes) {
            if (pass.getPhase() == phase && selector.test(pass) && pass.isActive()) activePasses.add(pass);
        }
        if (activePasses.isEmpty()) return false;

        String gpuGraphLabel = phase == PostProcessPass.Phase.PRE_HAND
                ? "3d:post_graph:pre_hand"
                : "3d:post_graph:post_hand";
        try (RenderCostProfiler.Scope ignoredGraph = RenderCostProfiler.postPass("graph:" + phase);
             TracyGpuProfiler.Scope ignoredGpuGraph = TracyGpuProfiler.beginZone(gpuGraphLabel)) {
            boolean preferStorageTargets = false;
            for (PostProcessPass pass : activePasses) {
                if (pass.prefersStorageOutput(rhi)) {
                    preferStorageTargets = true;
                    break;
                }
            }
            if (!resources.prepare(phase, tickDelta, rhi, preferStorageTargets)) return false;

            GpuTextureView mainColor = resources.mainColor();
            GpuTextureView source = resources.currentSource();
            if (mainColor == null || source == null) return false;

            copy.copy(mainColor, source);
            resources.resetPingPong();

            boolean anyApplied = false;
            for (PostProcessPass pass : activePasses) {
                String passId = pass.getClass().getName();
                boolean applied;
                try (RenderCostProfiler.Scope ignoredPass = RenderCostProfiler.postPass(passId);
                     TracyGpuProfiler.Scope ignoredGpu = TracyGpuProfiler.beginZone(passId)) {
                    applied = pass.render(new PostProcessExecutionContext(
                            resources.context(),
                            rhi,
                            resources.currentSource(),
                            resources.currentDestination(),
                            resources.currentSourceStorage(),
                            resources.currentDestinationStorage()
                    ));
                }
                if (applied) {
                    anyApplied = true;
                    resources.advancePingPong();
                }
            }

            if (!anyApplied) return false;
            GpuTextureView finalColor = resources.finalColor();
            if (finalColor != null) copy.copy(finalColor, mainColor);
            return true;
        } finally {
            activePasses.clear();
        }
    }

    public void releaseBackendResources(CombatantRhi owner) {
        resources.releaseBackendResources(owner);
        for (PostProcessPass pass : passes) {
            if (pass instanceof PostProcessBackendResourceOwner resourceOwner) {
                resourceOwner.releaseBackendResources(owner);
            }
        }
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
