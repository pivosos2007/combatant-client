package combatant.client.render.engine.profiler;

import com.mojang.jtracy.Plot;
import com.mojang.jtracy.TracyClient;
import com.mojang.jtracy.Zone;
import combatant.client.render.engine.rhi.RhiStatsSnapshot;
import combatant.client.render.engine.rhi.RhiPipelineStatsSnapshot;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public enum DevTracyProfiler {
    ;

    private static final Scope NOOP_SCOPE = new Scope(null);
    private static final ThreadLocal<Boolean> THREAD_NAMED =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static volatile boolean loadAttempted;
    private static volatile boolean available;
    private static volatile Plot uiFrameMsPlot;
    private static volatile Plot uiNodeCountPlot;
    private static volatile Plot worldFrameMsPlot;
    private static volatile Plot worldNodeCountPlot;
    private static volatile Plot glWaitMsPlot;
    private static volatile Plot glWaitCallsPlot;
    private static volatile Plot uiBatchDrawsPlot;
    private static volatile Plot uiBatchVerticesPlot;
    private static volatile Plot[] uiPipelinePlots;
    private static volatile Plot[] rhiPipelinePlots;
    private static final Map<String, Plot[]> PIPELINE_DETAIL_PLOTS = new ConcurrentHashMap<>();

    public static boolean isEnabled() {
        return ProfilerSettings.isTracyEnabled() && isAvailable();
    }

    public static boolean isAvailable() {
        ensureLoaded();
        return available;
    }

    public static synchronized boolean setEnabled(boolean enabled) {
        ProfilerSettings.setTracyEnabled(enabled);
        if (!enabled) {
            ProfilerLog.info("Tracy disabled");
            return false;
        }
        boolean active = isAvailable();
        if (active) {
            ProfilerLog.info("Tracy enabled; data is streamed to the external Tracy viewer, not latest.log");
            TracyClient.message("Combatant Tracy enabled");
        }
        return active;
    }

    public static Scope beginZone(String name) {
        if (!isEnabled() || name == null || name.isBlank()) {
            return NOOP_SCOPE;
        }
        ensureThreadNamed();
        return new Scope(TracyClient.beginZone(name, false));
    }

    public static void markFrame() {
        if (!isEnabled()) return;
        ensureThreadNamed();
        TracyClient.markFrame();
    }

    public static boolean shouldTraceCurrentThread() {
        String threadName = Thread.currentThread().getName();
        if (threadName == null || threadName.isBlank()) {
            return false;
        }
        return "Render thread".equals(threadName)
                || threadName.startsWith("Render thread")
                || "main".equals(threadName)
                || "Main thread".equals(threadName);
    }

    public static void plotUiFrame(double ms, int nodes) {
        if (!isEnabled()) return;
        plotUiFrameMs().setValue(ms);
        plotUiNodeCount().setValue(nodes);
    }

    public static void plotWorldFrame(double ms, int nodes) {
        if (!isEnabled()) return;
        plotWorldFrameMs().setValue(ms);
        plotWorldNodeCount().setValue(nodes);
    }

    public static void plotGlWait(double ms, int calls) {
        if (!isEnabled()) return;
        plotGlWaitMs().setValue(ms);
        plotGlWaitCalls().setValue(calls);
    }

    public static void plotUiBatch(int draws, int vertices) {
        if (!isEnabled()) return;
        plotUiBatchDraws().setValue(draws);
        plotUiBatchVertices().setValue(vertices);
    }

    /** Called directly from the render frame lifecycle, never through DevRenderProfiler2D. */
    public static void plotUiPipeline(UiPipelineStatsSnapshot s) {
        if (!isEnabled() || s == null) return;
        Plot[] p = uiPipelinePlots();
        p[0].setValue(s.analyticClipPushes());
        p[1].setValue(s.stencilClipPushes());
        p[2].setValue(s.stencilMaskPasses());
        p[3].setValue(s.stencilMaskPrimitives());
        p[4].setValue(s.stencilRestorePasses());
        p[5].setValue(s.stencilTestTransitions());
        p[6].setValue(s.stencilClears());
        p[7].setValue(s.stencilRenderPasses());
        p[8].setValue(s.stencilMaxDepth());
        p[9].setValue(s.msaaLayerAttempts());
        p[10].setValue(s.msaaLayerBegins());
        p[11].setValue(s.msaaLayerFallbacks());
        p[12].setValue(s.msaaResolves());
        p[13].setValue(s.msaaResolveFailures());
        p[14].setValue(s.msaaComposites());
        p[15].setValue(s.msaaTargetAllocations());
        p[16].setValue(s.msaaTargetResizes());
        p[17].setValue(s.msaaPixelSamples());
        p[18].setValue(s.directPasses());
        p[19].setValue(s.itemPasses());
        p[20].setValue(s.mixedItemPasses());
        p[21].setValue(s.legacyPasses());
        p[22].setValue(s.legacyMixedItems());
        p[23].setValue(s.legacyBlurCapture());
        p[24].setValue(s.legacyPreparedGlass());
        p[25].setValue(s.legacyUnsupported());
    }

    /** Called directly from the render frame lifecycle, never through a UI/world tree profiler. */
    public static void plotRhiPipeline(RhiStatsSnapshot s) {
        if (!isEnabled() || s == null) return;
        Plot[] p = rhiPipelinePlots();
        p[0].setValue(s.drawCalls());
        p[1].setValue(s.renderPasses());
        p[2].setValue(s.renderPassAttachmentSwitches());
        p[3].setValue(s.pipelineBinds());
        p[4].setValue(s.pipelineBindSkips());
        p[5].setValue(s.pipelineSwitches());
        p[6].setValue(s.uniquePipelines());
        p[7].setValue(s.uniformBinds());
        p[8].setValue(s.samplerBinds());
        p[9].setValue(s.meshUploads());
        p[10].setValue(s.uploadedVertexBytes());
        p[11].setValue(s.uploadedIndexBytes());
        p[12].setValue(s.fullscreenPasses());
        p[13].setValue(s.textureFastCopies());
        p[14].setValue(s.textureShaderCopies());
        p[15].setValue(s.estimatedShaderAluOps());
        p[16].setValue(s.estimatedShaderTranscendentalOps());
        p[17].setValue(s.estimatedShaderTextureOps());
        p[18].setValue(s.estimatedShaderBranchOps());
        p[19].setValue(s.estimatedShaderLoopOps());
        p[20].setValue(s.ringWraps());
        p[21].setValue(s.ringStalls());
        p[22].setValue(s.immediateFallbackUploads());
        p[23].setValue(s.temporaryOwnedMeshes());
        p[24].setValue(s.dynamicArenaAllocations());
        p[25].setValue(s.dynamicPersistentArenaAllocations());
        p[26].setValue(s.dynamicSpillArenaAllocations());
        p[27].setValue(s.dynamicArenaReuses());
        p[28].setValue(s.dynamicArenaRetires());
        p[29].setValue(s.dynamicFenceChecks());
        p[30].setValue(s.dynamicFenceCompletions());
        p[31].setValue(s.dynamicArenaBacklogEvents());
        p[32].setValue(s.dynamicPersistentArenaBytes());
        p[33].setValue(s.dynamicSpillArenaBytes());
        p[34].setValue(s.legacyPathUses());
        plotPipelineBreakdown(s.pipelineBreakdown());
    }

    private static void plotPipelineBreakdown(java.util.List<RhiPipelineStatsSnapshot> snapshots) {
        if (snapshots == null) return;
        Map<String, RhiPipelineStatsSnapshot> current = new HashMap<>(snapshots.size());
        for (RhiPipelineStatsSnapshot snapshot : snapshots) {
            if (snapshot != null) current.put(snapshot.pipelineId(), snapshot);
        }
        for (Map.Entry<String, Plot[]> entry : PIPELINE_DETAIL_PLOTS.entrySet()) {
            setPipelinePlotValues(entry.getValue(), current.remove(entry.getKey()));
        }
        for (Map.Entry<String, RhiPipelineStatsSnapshot> entry : current.entrySet()) {
            setPipelinePlotValues(pipelineDetailPlots(entry.getKey()), entry.getValue());
        }
    }

    private static void setPipelinePlotValues(Plot[] plots, RhiPipelineStatsSnapshot snapshot) {
        plots[0].setValue(snapshot != null ? snapshot.draws() : 0);
        plots[1].setValue(snapshot != null ? snapshot.binds() : 0);
        plots[2].setValue(snapshot != null ? snapshot.switchesInto() : 0);
        plots[3].setValue(snapshot != null ? snapshot.estimatedAluOps() : 0);
        plots[4].setValue(snapshot != null ? snapshot.estimatedTranscendentalOps() : 0);
        plots[5].setValue(snapshot != null ? snapshot.estimatedTextureOps() : 0);
        plots[6].setValue(snapshot != null ? snapshot.estimatedBranchOps() : 0);
    }

    private static Plot[] pipelineDetailPlots(String pipelineId) {
        String id = pipelineId == null || pipelineId.isBlank() ? "external" : pipelineId;
        return PIPELINE_DETAIL_PLOTS.computeIfAbsent(id, ignored -> {
            String safe = id.replaceAll("[^A-Za-z0-9_.-]", "_");
            String prefix = "render.pipeline." + safe;
            return new Plot[]{
                    TracyClient.createPlot(prefix + ".draws"),
                    TracyClient.createPlot(prefix + ".binds"),
                    TracyClient.createPlot(prefix + ".switches_into"),
                    TracyClient.createPlot(prefix + ".estimated_alu_sum"),
                    TracyClient.createPlot(prefix + ".estimated_transcendental_sum"),
                    TracyClient.createPlot(prefix + ".estimated_texture_sum"),
                    TracyClient.createPlot(prefix + ".estimated_branch_sum")
            };
        });
    }

    private static void ensureLoaded() {
        if (loadAttempted) {
            return;
        }
        synchronized (DevTracyProfiler.class) {
            if (loadAttempted) {
                return;
            }
            loadAttempted = true;
            try {
                TracyClient.load();
                TracyClient.reportAppInfo("Combatant render profiler");
                available = TracyClient.isAvailable();
                if (available) {
                    ProfilerLog.info("Tracy client loaded");
                }
            } catch (Throwable t) {
                available = false;
                ProfilerLog.warn("Tracy unavailable: %s", t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    private static void ensureThreadNamed() {
        if (Boolean.TRUE.equals(THREAD_NAMED.get())) {
            return;
        }
        TracyClient.setThreadName(Thread.currentThread().getName(), 0);
        THREAD_NAMED.set(Boolean.TRUE);
    }

    private static Plot plotUiFrameMs() {
        Plot plot = uiFrameMsPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (uiFrameMsPlot == null) {
                uiFrameMsPlot = TracyClient.createPlot("render.ui.ms");
            }
            return uiFrameMsPlot;
        }
    }

    private static Plot plotUiNodeCount() {
        Plot plot = uiNodeCountPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (uiNodeCountPlot == null) {
                uiNodeCountPlot = TracyClient.createPlot("render.ui.nodes");
            }
            return uiNodeCountPlot;
        }
    }

    private static Plot plotWorldFrameMs() {
        Plot plot = worldFrameMsPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (worldFrameMsPlot == null) {
                worldFrameMsPlot = TracyClient.createPlot("render.world.ms");
            }
            return worldFrameMsPlot;
        }
    }

    private static Plot plotWorldNodeCount() {
        Plot plot = worldNodeCountPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (worldNodeCountPlot == null) {
                worldNodeCountPlot = TracyClient.createPlot("render.world.nodes");
            }
            return worldNodeCountPlot;
        }
    }

    private static Plot plotGlWaitMs() {
        Plot plot = glWaitMsPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (glWaitMsPlot == null) {
                glWaitMsPlot = TracyClient.createPlot("render.gl_wait.ms");
            }
            return glWaitMsPlot;
        }
    }

    private static Plot plotGlWaitCalls() {
        Plot plot = glWaitCallsPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (glWaitCallsPlot == null) {
                glWaitCallsPlot = TracyClient.createPlot("render.gl_wait.calls");
            }
            return glWaitCallsPlot;
        }
    }

    private static Plot plotUiBatchDraws() {
        Plot plot = uiBatchDrawsPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (uiBatchDrawsPlot == null) {
                uiBatchDrawsPlot = TracyClient.createPlot("render.ui_batch.draws");
            }
            return uiBatchDrawsPlot;
        }
    }

    private static Plot plotUiBatchVertices() {
        Plot plot = uiBatchVerticesPlot;
        if (plot != null) return plot;
        synchronized (DevTracyProfiler.class) {
            if (uiBatchVerticesPlot == null) {
                uiBatchVerticesPlot = TracyClient.createPlot("render.ui_batch.vertices");
            }
            return uiBatchVerticesPlot;
        }
    }

    private static Plot[] uiPipelinePlots() {
        Plot[] plots = uiPipelinePlots;
        if (plots != null) return plots;
        synchronized (DevTracyProfiler.class) {
            if (uiPipelinePlots == null) {
                String[] names = {
                        "render.ui_clip.analytic_pushes",
                        "render.ui_clip.stencil_pushes",
                        "render.ui_clip.stencil_mask_passes",
                        "render.ui_clip.stencil_mask_primitives",
                        "render.ui_clip.stencil_restore_passes",
                        "render.ui_clip.stencil_test_transitions",
                        "render.ui_clip.stencil_clears",
                        "render.ui_clip.stencil_render_passes",
                        "render.ui_clip.stencil_max_depth",
                        "render.ui_clip.msaa_layer_attempts",
                        "render.ui_clip.msaa_layer_begins",
                        "render.ui_clip.msaa_layer_fallbacks",
                        "render.ui_clip.msaa_resolves",
                        "render.ui_clip.msaa_resolve_failures",
                        "render.ui_clip.msaa_composites",
                        "render.ui_clip.msaa_target_allocations",
                        "render.ui_clip.msaa_target_resizes",
                        "render.ui_clip.msaa_pixel_samples",
                        "render.ui_compiler.direct_passes",
                        "render.ui_compiler.item_passes",
                        "render.ui_compiler.mixed_item_passes",
                        "render.ui_compiler.legacy_passes",
                        "render.ui_compiler.legacy_mixed_items",
                        "render.ui_compiler.legacy_blur_capture",
                        "render.ui_compiler.legacy_prepared_glass",
                        "render.ui_compiler.legacy_unsupported"
                };
                Plot[] created = new Plot[names.length];
                for (int i = 0; i < names.length; i++) {
                    created[i] = TracyClient.createPlot(names[i]);
                }
                uiPipelinePlots = created;
            }
            return uiPipelinePlots;
        }
    }

    private static Plot[] rhiPipelinePlots() {
        Plot[] plots = rhiPipelinePlots;
        if (plots != null) return plots;
        synchronized (DevTracyProfiler.class) {
            if (rhiPipelinePlots == null) {
                String[] names = {
                        "render.rhi.draw_calls",
                        "render.rhi.render_passes",
                        "render.rhi.attachment_switches",
                        "render.rhi.pipeline_binds",
                        "render.rhi.pipeline_bind_skips",
                        "render.rhi.pipeline_switches",
                        "render.rhi.unique_pipelines",
                        "render.rhi.uniform_binds",
                        "render.rhi.sampler_binds",
                        "render.rhi.mesh_uploads",
                        "render.rhi.uploaded_vertex_bytes",
                        "render.rhi.uploaded_index_bytes",
                        "render.rhi.fullscreen_passes",
                        "render.rhi.texture_fast_copies",
                        "render.rhi.texture_shader_copies",
                        "render.shader.estimated_alu_per_draw_sum",
                        "render.shader.estimated_transcendental_per_draw_sum",
                        "render.shader.estimated_texture_per_draw_sum",
                        "render.shader.estimated_branch_per_draw_sum",
                        "render.shader.estimated_loop_per_draw_sum",
                        "render.rhi.ring_wraps",
                        "render.rhi.ring_stalls",
                        "render.rhi.immediate_fallback_uploads",
                        "render.rhi.temporary_owned_meshes",
                        "render.rhi.dynamic.arena_creations",
                        "render.rhi.dynamic.persistent_arena_creations",
                        "render.rhi.dynamic.spill_arena_creations",
                        "render.rhi.dynamic.arena_reuses",
                        "render.rhi.dynamic.arena_retires",
                        "render.rhi.dynamic.fence_checks",
                        "render.rhi.dynamic.fence_completions",
                        "render.rhi.dynamic.backlog_events",
                        "render.rhi.dynamic.persistent_arena_bytes",
                        "render.rhi.dynamic.spill_arena_bytes",
                        "render.rhi.legacy_path_uses"
                };
                Plot[] created = new Plot[names.length];
                for (int i = 0; i < names.length; i++) created[i] = TracyClient.createPlot(names[i]);
                rhiPipelinePlots = created;
            }
            return rhiPipelinePlots;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Zone zone;

        private Scope(Zone zone) {
            this.zone = zone;
        }

        @Override
        public void close() {
            if (zone != null) {
                zone.close();
            }
        }
    }
}
