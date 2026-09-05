/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.renderer.Renderer2D;

import static combatant.client.render.engine.renderer.ui.UiBlurResources.scaleBits;

public final class FrameBlurCacheEntry {
    public FrameBlurCacheEntry() {
    }
    long frameId = Long.MIN_VALUE;
    RenderPhase phase = RenderPhase.NONE;
    @Nullable GpuTextureView sourceView;
    @Nullable GpuSampler sourceSampler;
    @Nullable GpuTextureView blurredView;
    @Nullable GpuSampler blurredSampler;
    int screenW;
    int screenH;
    int uiScaleBits;
    int blurQualityId;
    int blurOffsetBits;

    public boolean matches(long frameId,
                            RenderPhase phase,
                            @Nullable GpuTextureView sourceView,
                            @Nullable GpuSampler sourceSampler,
                            float screenW,
                            float screenH,
                            float uiScale,
                            Renderer2D.BlurQuality blurQuality,
                            float offsetPx) {
        Renderer2D.BlurQuality quality = blurQuality != null ? blurQuality : Renderer2D.DEFAULT_BLUR_QUALITY;
        return blurredView != null
                && blurredSampler != null
                && this.frameId == frameId
                && this.phase == phase
                && this.sourceView == sourceView
                && this.sourceSampler == sourceSampler
                && this.screenW == Math.max(1, Math.round(screenW))
                && this.screenH == Math.max(1, Math.round(screenH))
                && this.uiScaleBits == scaleBits(uiScale)
                && this.blurQualityId == quality.id
                && this.blurOffsetBits == Float.floatToIntBits(Float.isFinite(offsetPx) ? Math.max(0.0f, offsetPx) : Renderer2D.DEFAULT_KAWASE_OFFSET_PX);
    }

    public void set(long frameId,
                     RenderPhase phase,
                     @Nullable GpuTextureView sourceView,
                     @Nullable GpuSampler sourceSampler,
                     @Nullable GpuTextureView blurredView,
                     @Nullable GpuSampler blurredSampler,
                     float screenW,
                     float screenH,
                     float uiScale,
                     Renderer2D.BlurQuality blurQuality,
                     float offsetPx) {
        Renderer2D.BlurQuality quality = blurQuality != null ? blurQuality : Renderer2D.DEFAULT_BLUR_QUALITY;
        this.frameId = frameId;
        this.phase = phase != null ? phase : RenderPhase.NONE;
        this.sourceView = sourceView;
        this.sourceSampler = sourceSampler;
        this.blurredView = blurredView;
        this.blurredSampler = blurredSampler;
        this.screenW = Math.max(1, Math.round(screenW));
        this.screenH = Math.max(1, Math.round(screenH));
        this.uiScaleBits = scaleBits(uiScale);
        this.blurQualityId = quality.id;
        this.blurOffsetBits = Float.floatToIntBits(Float.isFinite(offsetPx) ? Math.max(0.0f, offsetPx) : Renderer2D.DEFAULT_KAWASE_OFFSET_PX);
    }

    public void clear() {
        frameId = Long.MIN_VALUE;
        phase = RenderPhase.NONE;
        sourceView = null;
        sourceSampler = null;
        blurredView = null;
        blurredSampler = null;
        screenW = 0;
        screenH = 0;
        uiScaleBits = 0;
        blurQualityId = 0;
        blurOffsetBits = 0;
    }
}
