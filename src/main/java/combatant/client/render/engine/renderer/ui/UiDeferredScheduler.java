/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.mixininterface.IGpuDevice;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderFrameContext;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.profiler.ProfilerPhase;
import combatant.client.render.engine.profiler.TracyGpuProfiler;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.ClipFunction;
import combatant.client.render.engine.renderer.ui.clip.UiMsaaClipLayer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;

import java.util.EnumMap;

/**
 * Owns extraction-time UI scheduling and replay. Renderer2D exposes only the stable facade;
 * frame queues, projection restoration and deferred batcher pooling live here.
 */
public final class UiDeferredScheduler {
    private static final ObjectArrayList<Deferred2DSubmit> RECORDING = new ObjectArrayList<>(256);
    private static final EnumMap<Renderer2D.Deferred2DLayer, ObjectArrayList<Deferred2DSubmit>> READY_BY_LAYER =
            new EnumMap<>(Renderer2D.Deferred2DLayer.class);
    private static final ObjectArrayList<Deferred2DSubmit> DRAINING = new ObjectArrayList<>(256);
    private static final ObjectArrayList<OrderedUiBatcher> BATCHER_POOL = new ObjectArrayList<>(8);
    private static final ObjectArrayList<ItemBatch> ITEM_PREPARATION_BATCHES = new ObjectArrayList<>(64);
    private static final int MAX_BATCHER_POOL = Integer.getInteger("combatant.render.deferredBatcherPool", 16);

    private static ProjectionMatrixBuffer projection;
    private static boolean recording;
    private static boolean draining;
    private static boolean deferredItemsPrepared;
    private static Renderer2D.Deferred2DLayer forcedLayer;

    static {
        for (Renderer2D.Deferred2DLayer layer : Renderer2D.Deferred2DLayer.values()) {
            int capacity = layer == Renderer2D.Deferred2DLayer.AFTER_VANILLA_GUI ? 64 : 32;
            if (layer == Renderer2D.Deferred2DLayer.BEFORE_VANILLA_GUI) capacity = 256;
            READY_BY_LAYER.put(layer, new ObjectArrayList<>(capacity));
        }
    }

    private UiDeferredScheduler() {
    }

    public static void beginExtractFrame() {
        if (draining) return;
        UiBlurResources.beginDeferredFrame();
        releaseSubmits(RECORDING);
        RECORDING.clear();
        deferredItemsPrepared = false;
        recording = true;
    }

    public static void endExtractFrame() {
        if (!recording) return;
        for (ObjectArrayList<Deferred2DSubmit> ready : READY_BY_LAYER.values()) {
            releaseSubmits(ready);
            ready.clear();
        }
        for (int i = 0, size = RECORDING.size(); i < size; i++) {
            Deferred2DSubmit submit = RECORDING.get(i);
            if (submit == null) continue;
            readyList(submit.layer()).add(submit);
            RECORDING.set(i, null);
        }
        RECORDING.clear();
        recording = false;
    }

    /**
     * Mirror vanilla GuiRenderer's lifecycle: item models/atlas slots are prepared before draw
     * replay starts. In particular this keeps GuiItemAtlas's offscreen pass out of HUD marker
     * replay, rounded clips and liquid-glass batches on every backend.
     */
    public static void prepareDeferredUiItems() {
        if (deferredItemsPrepared || recording || draining) return;

        ITEM_PREPARATION_BATCHES.clear();
        for (ObjectArrayList<Deferred2DSubmit> ready : READY_BY_LAYER.values()) {
            for (int i = 0, size = ready.size(); i < size; i++) {
                Deferred2DSubmit submit = ready.get(i);
                if (submit instanceof DeferredOrderedSubmit ordered && ordered.batcher() != null) {
                    ordered.batcher().collectItemBatches(ITEM_PREPARATION_BATCHES);
                }
            }
        }

        try {
            ItemBatchRenderer.prepareUiItems(ITEM_PREPARATION_BATCHES);
            deferredItemsPrepared = true;
        } finally {
            ITEM_PREPARATION_BATCHES.clear();
        }
    }

    public static void drain(Renderer2D.Deferred2DLayer layer) {
        ObjectArrayList<Deferred2DSubmit> ready = readyList(layer);
        if (ready.isEmpty()) return;

        DRAINING.clear();
        DRAINING.addAll(ready);
        ready.clear();

        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        Matrix4f previousMeshProjection = MeshRenderer.projection();
        ViewportContext previousViewport = ViewportContext.current();
        boolean previousRendering3D = RenderState.rendering3D;
        var modelView = RenderSystem.getModelViewStack();
        boolean pushedModelView = false;

        draining = true;
        try {
            modelView.pushMatrix();
            pushedModelView = true;
            modelView.identity();

            String layerLabel = layer == Renderer2D.Deferred2DLayer.AFTER_VANILLA_GUI
                    ? "after_vanilla_gui"
                    : "before_vanilla_gui";
            try (ProfilerPhase.Scope ignoredCpu = ProfilerPhase.scope("ui:deferred_drain:" + layerLabel);
                 TracyGpuProfiler.Scope ignoredGpu = TracyGpuProfiler.beginZone("2d:deferred_" + layerLabel)) {
                for (int i = 0, size = DRAINING.size(); i < size; ) {
                    Deferred2DSubmit command = DRAINING.get(i);
                    if (command == null) {
                        i++;
                        continue;
                    }

                    if (command instanceof DeferredOrderedSubmit firstOrdered) {
                        int end = i + 1;
                        while (end < size) {
                            Deferred2DSubmit next = DRAINING.get(end);
                            if (!(next instanceof DeferredOrderedSubmit nextOrdered)
                                    || !sameReplayState(firstOrdered, nextOrdered)) {
                                break;
                            }
                            end++;
                        }
                        submitOrderedGroup(i, end, firstOrdered);
                        i = end;
                        continue;
                    }

                    applyViewport(command.viewport());
                    int[] scissor = UiMsaaClipLayer.mapFramebufferScissor(command.framebufferScissor());
                    boolean scissored = false;
                    try {
                        if (scissor != null && scissor.length == 4) {
                            ((IGpuDevice) RenderSystem.getDevice())
                                    .combatant$pushScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
                            scissored = true;
                        }
                        command.submit();
                    } finally {
                        if (scissored) {
                            ((IGpuDevice) RenderSystem.getDevice()).combatant$popScissor();
                        }
                        command.release();
                        DRAINING.set(i, null);
                    }
                    i++;
                }
            }
        } finally {
            draining = false;
            if (pushedModelView) modelView.popMatrix();
            MeshRenderer.setProjection(previousMeshProjection);
            if (previousViewport != null) ViewportContext.applyCaptured(previousViewport);
            RenderState.rendering3D = previousRendering3D;
            if (previousProjection != null && previousProjectionType != null) {
                RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            }
            releaseSubmits(DRAINING);
            DRAINING.clear();
        }
    }

    private static void submitOrderedGroup(int start,
                                           int end,
                                           DeferredOrderedSubmit first) {
        applyViewport(first.viewport());
        int[] scissor = UiMsaaClipLayer.mapFramebufferScissor(first.framebufferScissor());
        boolean scissored = false;
        try {
            if (scissor != null && scissor.length == 4) {
                ((IGpuDevice) RenderSystem.getDevice())
                        .combatant$pushScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
                scissored = true;
            }

            // Enqueue every compatible facade submission before compiling. UiPassCompiler can
            // then join their direct RHI streams and the backend can continue one render pass
            // across UI-runtime/HUD tree boundaries without changing draw order.
            for (int i = start; i < end; i++) {
                DeferredOrderedSubmit ordered = (DeferredOrderedSubmit) DRAINING.get(i);
                if (ordered != null && ordered.batcher() != null) {
                    ordered.batcher().flush(true);
                }
            }
            Renderer2D.flushUiLayer();
        } finally {
            if (scissored) {
                ((IGpuDevice) RenderSystem.getDevice()).combatant$popScissor();
            }
            for (int i = start; i < end; i++) {
                Deferred2DSubmit ordered = DRAINING.get(i);
                if (ordered != null) ordered.release();
                DRAINING.set(i, null);
            }
        }
    }

    private static boolean sameReplayState(DeferredOrderedSubmit first,
                                           DeferredOrderedSubmit next) {
        if (first == null || next == null || first.layer() != next.layer()) return false;
        if (!first.scissorSnapshot().equals(next.scissorSnapshot())) return false;
        if (!first.clipSnapshot().equals(next.clipSnapshot())) return false;
        return sameViewport(first.viewport(), next.viewport());
    }

    private static boolean sameViewport(ViewportContext first, ViewportContext next) {
        if (first == next) return true;
        if (first == null || next == null) return false;
        return first.framebufferWidth() == next.framebufferWidth()
                && first.framebufferHeight() == next.framebufferHeight()
                && Float.floatToIntBits(first.scaleFactor()) == Float.floatToIntBits(next.scaleFactor())
                && Float.floatToIntBits(first.width()) == Float.floatToIntBits(next.width())
                && Float.floatToIntBits(first.height()) == Float.floatToIntBits(next.height())
                && Float.floatToIntBits(first.uiScale()) == Float.floatToIntBits(next.uiScale())
                && first.projectionMode() == next.projectionMode()
                && first.projectionMatrix().equals(next.projectionMatrix());
    }

    public static boolean shouldDefer() {
        return recording && !draining;
    }

    static boolean isDraining() {
        return draining;
    }

    public static void withLayer(Renderer2D.Deferred2DLayer layer, Runnable action) {
        if (action == null) return;
        Renderer2D.Deferred2DLayer previous = forcedLayer;
        forcedLayer = layer;
        try {
            action.run();
        } finally {
            forcedLayer = previous;
        }
    }

    public static void deferAction(Runnable action) {
        if (action == null) return;
        if (!shouldDefer()) {
            action.run();
            return;
        }
        enqueue(new DeferredActionSubmit(
                layerForCurrentPhase(false),
                snapshotViewport(),
                ScissorFunction.currentSnapshot(),
                ClipFunction.currentSnapshot(),
                action
        ));
    }

    public static void enqueue(Deferred2DSubmit submit) {
        if (submit != null) RECORDING.add(submit);
    }

    public static Renderer2D.Deferred2DLayer layerForCurrentPhase(boolean pureItemOverlaySubmit) {
        if (forcedLayer != null) return forcedLayer;
        RenderFrameContext frame = CombatantRenderSystem.currentContext();
        RenderPhase phase = frame != null ? frame.phase() : RenderPhase.NONE;
        return phase == RenderPhase.SCREEN_TOP || phase == RenderPhase.SCREEN
                ? Renderer2D.Deferred2DLayer.AFTER_VANILLA_GUI
                : Renderer2D.Deferred2DLayer.BEFORE_VANILLA_GUI;
    }

    public static OrderedUiBatcher obtainBatcher() {
        int last = BATCHER_POOL.size() - 1;
        return last >= 0 ? BATCHER_POOL.remove(last) : new OrderedUiBatcher();
    }

    public static void releaseBatcher(OrderedUiBatcher batcher) {
        if (batcher == null) return;
        batcher.releaseAfterDeferred();
        if (BATCHER_POOL.size() < MAX_BATCHER_POOL) {
            BATCHER_POOL.add(batcher);
        } else {
            batcher.closeRetainedBuffers();
        }
    }

    public static ViewportContext snapshotViewport() {
        ViewportContext viewport = ViewportContext.current();
        if (viewport == null) viewport = ViewportContext.capture();
        return new ViewportContext(
                viewport.framebufferWidth(),
                viewport.framebufferHeight(),
                viewport.scaleFactor(),
                viewport.width(),
                viewport.height(),
                viewport.uiScale(),
                viewport.projectionMode(),
                new Matrix4f(viewport.projectionMatrix())
        );
    }

    private static ObjectArrayList<Deferred2DSubmit> readyList(Renderer2D.Deferred2DLayer layer) {
        Renderer2D.Deferred2DLayer normalized = layer != null
                ? layer
                : Renderer2D.Deferred2DLayer.BEFORE_VANILLA_GUI;
        return READY_BY_LAYER.computeIfAbsent(normalized, ignored -> new ObjectArrayList<>(32));
    }

    private static void releaseSubmits(ObjectArrayList<Deferred2DSubmit> submits) {
        for (int i = 0, size = submits.size(); i < size; i++) {
            Deferred2DSubmit submit = submits.get(i);
            if (submit != null) {
                submit.release();
                submits.set(i, null);
            }
        }
    }

    private static ProjectionMatrixBuffer projection() {
        ProjectionMatrixBuffer current = projection;
        if (current == null) {
            current = new ProjectionMatrixBuffer("combatant-deferred-2d-projection");
            projection = current;
        }
        return current;
    }

    private static void applyViewport(ViewportContext viewport) {
        if (viewport == null) return;
        ViewportContext targetViewport = UiMsaaClipLayer.viewportFor(viewport);
        Matrix4f matrix = new Matrix4f(targetViewport.projectionMatrix());
        RenderSystem.setProjectionMatrix(projection().getBuffer(matrix), ProjectionType.ORTHOGRAPHIC);
        MeshRenderer.setProjection(matrix);
        ViewportContext.applyCaptured(targetViewport);
        RenderState.rendering3D = false;
    }
}
