/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.HudDeferredGuiElement;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Supplier;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Shadow
    @Final
    private List<?> draws;

    @Shadow
    private int firstDrawIndexAfterBlur;

    @Shadow
    @Final
    private Projection guiProjection;

    @Shadow
    @Final
    private ProjectionMatrixBuffer guiProjectionMatrixBuffer;

    @Unique
    private final ObjectArrayList<QueuedHudMarker> combatant$hudMarkers = new ObjectArrayList<>(16);

    @Unique
    private GuiRenderState.TraverseRange combatant$currentTraverseRange;

    @Shadow
    private void executeDrawRange(Supplier<String> supplier,
                                  RenderTarget renderTarget,
                                  GpuBufferSlice gpuBufferSlice,
                                  int start,
                                  int end) {
        throw new AssertionError();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void combatant$clearHudMarkersAtFrameStart(CallbackInfo ci) {
        combatant$hudMarkers.clear();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void combatant$clearHudMarkersAtFrameEnd(CallbackInfo ci) {
        combatant$hudMarkers.clear();
    }

    @Inject(method = "addElementsToMeshes", at = @At("HEAD"))
    private void combatant$enterTraverseRange(GuiRenderState.TraverseRange range, CallbackInfo ci) {
        combatant$currentTraverseRange = range;
    }

    @Inject(method = "addElementsToMeshes", at = @At("RETURN"))
    private void combatant$exitTraverseRange(GuiRenderState.TraverseRange range, CallbackInfo ci) {
        combatant$currentTraverseRange = null;
    }

    @Inject(method = "addElementToMesh", at = @At("HEAD"), cancellable = true)
    private void combatant$captureHudDeferredMarker(GuiElementRenderState element, CallbackInfo ci) {
        if (!(element instanceof HudDeferredGuiElement marker)) return;
        int drawIndex = draws != null ? draws.size() : 0;
        GuiRenderState.TraverseRange range = combatant$currentTraverseRange != null
                ? combatant$currentTraverseRange
                : GuiRenderState.TraverseRange.BEFORE_BLUR;
        combatant$hudMarkers.add(new QueuedHudMarker(drawIndex, range, marker.layer()));
        ci.cancel();
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void combatant$drawWithDeferredHudMarkers(CallbackInfo ci) {
        if (combatant$hudMarkers.isEmpty()) return;

        if (draws == null || draws.isEmpty()) {
            combatant$drainHudMarkersUpTo(Integer.MAX_VALUE);
            ci.cancel();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null) {
            combatant$drainHudMarkersUpTo(Integer.MAX_VALUE);
            ci.cancel();
            return;
        }

        WindowRenderState window = mc.gameRenderer.gameRenderState().windowRenderState;
        if (window == null || window.guiScale == 0) {
            combatant$drainHudMarkersUpTo(Integer.MAX_VALUE);
            ci.cancel();
            return;
        }

        guiProjection.setupOrtho(
                1000.0F,
                11000.0F,
                (float) window.width / (float) window.guiScale,
                (float) window.height / (float) window.guiScale,
                true
        );
        RenderSystem.setProjectionMatrix(guiProjectionMatrixBuffer.getBuffer(guiProjection), ProjectionType.ORTHOGRAPHIC);

        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        if (target == null) {
            combatant$drainHudMarkersUpTo(Integer.MAX_VALUE);
            ci.cancel();
            return;
        }

        GpuBufferSlice transform = RenderSystem.getDynamicUniforms()
                .writeTransform(new Matrix4f().setTranslation(0.0F, 0.0F, -11000.0F));

        int beforeBlurEnd = Math.min(firstDrawIndexAfterBlur, draws.size());
        if (beforeBlurEnd > 0) {
            combatant$executeDrawRangeWithHudMarkers(
                    () -> "gui before blur",
                    target,
                    transform,
                    0,
                    beforeBlurEnd,
                    GuiRenderState.TraverseRange.BEFORE_BLUR
            );
        } else {
            combatant$drainHudMarkersInRange(GuiRenderState.TraverseRange.BEFORE_BLUR, Integer.MAX_VALUE);
        }

        boolean hasAfterBlurMarkers = combatant$hasPendingHudMarkersInRange(GuiRenderState.TraverseRange.AFTER_BLUR);
        if (draws.size() <= firstDrawIndexAfterBlur && !hasAfterBlurMarkers) {
            combatant$drainHudMarkersUpTo(Integer.MAX_VALUE);
            ci.cancel();
            return;
        }

        RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(target.getDepthTexture(), 0.0D);
        mc.gameRenderer.processBlurEffect();
        combatant$executeDrawRangeWithHudMarkers(
                () -> "gui after blur",
                target,
                transform,
                firstDrawIndexAfterBlur,
                draws.size(),
                GuiRenderState.TraverseRange.AFTER_BLUR
        );
        combatant$drainHudMarkersUpTo(Integer.MAX_VALUE);
        ci.cancel();
    }

    @Unique
    private void combatant$executeDrawRangeWithHudMarkers(Supplier<String> label,
                                                          RenderTarget target,
                                                          GpuBufferSlice transform,
                                                          int start,
                                                          int end,
                                                          GuiRenderState.TraverseRange range) {
        int cursor = start;
        for (int i = 0, size = combatant$hudMarkers.size(); i < size; i++) {
            QueuedHudMarker marker = combatant$hudMarkers.get(i);
            if (marker == null || marker.drained || marker.range != range) continue;

            int markerIndex = marker.drawIndex;
            if (markerIndex < start) {
                combatant$drainHudMarker(marker);
                continue;
            }
            if (markerIndex > end) continue;

            if (cursor < markerIndex) {
                executeDrawRange(label, target, transform, cursor, markerIndex);
            }
            combatant$drainHudMarker(marker);
            cursor = Math.max(cursor, markerIndex);
        }

        if (cursor < end) {
            executeDrawRange(label, target, transform, cursor, end);
        }
    }

    @Unique
    private boolean combatant$hasPendingHudMarkersInRange(GuiRenderState.TraverseRange range) {
        for (int i = 0, size = combatant$hudMarkers.size(); i < size; i++) {
            QueuedHudMarker marker = combatant$hudMarkers.get(i);
            if (marker != null && !marker.drained && marker.range == range) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private void combatant$drainHudMarkersInRange(GuiRenderState.TraverseRange range, int drawIndexInclusive) {
        for (int i = 0, size = combatant$hudMarkers.size(); i < size; i++) {
            QueuedHudMarker marker = combatant$hudMarkers.get(i);
            if (marker == null || marker.drained || marker.range != range) continue;
            if (marker.drawIndex <= drawIndexInclusive) {
                combatant$drainHudMarker(marker);
            }
        }
    }

    @Unique
    private void combatant$drainHudMarkersUpTo(int drawIndexInclusive) {
        for (int i = 0, size = combatant$hudMarkers.size(); i < size; i++) {
            QueuedHudMarker marker = combatant$hudMarkers.get(i);
            if (marker == null || marker.drained) continue;
            if (marker.drawIndex <= drawIndexInclusive) {
                combatant$drainHudMarker(marker);
            }
        }
    }

    @Unique
    private void combatant$drainHudMarker(QueuedHudMarker marker) {
        if (marker == null || marker.drained) return;
        marker.drained = true;
        Renderer2D.drainDeferred2D(marker.layer);
    }

    @Unique
    private static final class QueuedHudMarker {
        private final int drawIndex;
        private final GuiRenderState.TraverseRange range;
        private final Renderer2D.Deferred2DLayer layer;
        private boolean drained;

        private QueuedHudMarker(int drawIndex,
                                GuiRenderState.TraverseRange range,
                                Renderer2D.Deferred2DLayer layer) {
            this.drawIndex = drawIndex;
            this.range = range;
            this.layer = layer;
        }
    }
}
