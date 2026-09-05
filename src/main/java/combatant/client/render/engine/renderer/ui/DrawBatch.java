/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.clip.UiClipSnapshot;
import combatant.client.render.engine.renderer.ui.clip.UiScissorSnapshot;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;

import java.util.Objects;

public final class DrawBatch {
    public final UiBatchType type;
    public final MeshBuilder mesh;
    public GpuTextureView view;
    public GpuSampler sampler;
    public float msdfPxRange;
    public int msdfAtlasWidth;
    public int msdfAtlasHeight;
    public Renderer2D.BlurQuality blurQuality = Renderer2D.DEFAULT_BLUR_QUALITY;
    public float blurOffsetPx = Renderer2D.DEFAULT_KAWASE_OFFSET_PX;
    public UiBackdropRequest backdropRequest = UiBackdropRequest.NONE;
    public UiClipSnapshot clipSnapshot = UiClipSnapshot.NONE;
    public UiScissorSnapshot scissorSnapshot = UiScissorSnapshot.NONE;

    public DrawBatch(UiBatchType type) {
        this.type = type;
        this.mesh = new MeshBuilder(type.pipeline);
    }

    public void begin(GpuTextureView view, GpuSampler sampler,
                      UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        this.view = view;
        this.sampler = sampler;
        this.msdfPxRange = 0.0f;
        this.msdfAtlasWidth = 0;
        this.msdfAtlasHeight = 0;
        this.blurQuality = Renderer2D.DEFAULT_BLUR_QUALITY;
        this.blurOffsetPx = Renderer2D.DEFAULT_KAWASE_OFFSET_PX;
        this.backdropRequest = UiBackdropRequest.NONE;
        this.scissorSnapshot = scissorSnapshot != null ? scissorSnapshot : UiScissorSnapshot.NONE;
        this.clipSnapshot = clipSnapshot != null ? clipSnapshot : UiClipSnapshot.NONE;
        if (mesh.isBuilding()) {
            mesh.end();
        }
        mesh.begin();
    }

    public void beginMsdf(GpuTextureView view, GpuSampler sampler, float pxRange, int atlasWidth, int atlasHeight,
                          UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        begin(view, sampler, scissorSnapshot, clipSnapshot);
        this.msdfPxRange = pxRange;
        this.msdfAtlasWidth = atlasWidth;
        this.msdfAtlasHeight = atlasHeight;
    }

    public void beginBlur(GpuTextureView view, GpuSampler sampler, Renderer2D.BlurQuality quality, float offsetPx,
                          UiBackdropRequest backdropRequest,
                          UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        begin(view, sampler, scissorSnapshot, clipSnapshot);
        this.blurQuality = quality != null ? quality : Renderer2D.DEFAULT_BLUR_QUALITY;
        this.blurOffsetPx = Float.isFinite(offsetPx) ? Math.max(0.0f, offsetPx) : Renderer2D.DEFAULT_KAWASE_OFFSET_PX;
        this.backdropRequest = backdropRequest != null ? backdropRequest : UiBackdropRequest.NONE;
    }

    public boolean canMerge(UiBatchType type, GpuTextureView view, GpuSampler sampler,
                            UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        if (this.type != type || !sameState(this.scissorSnapshot, scissorSnapshot)
                || !sameState(this.clipSnapshot, clipSnapshot)) return false;
        if (!type.usesSampler) return true;
        return this.view == view && this.sampler == sampler;
    }

    public boolean canMergeMsdf(GpuTextureView view, GpuSampler sampler, float pxRange, int atlasWidth, int atlasHeight,
                                UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        return canMerge(UiBatchType.SVG_MSDF, view, sampler, scissorSnapshot, clipSnapshot)
                && Float.compare(this.msdfPxRange, pxRange) == 0
                && this.msdfAtlasWidth == atlasWidth
                && this.msdfAtlasHeight == atlasHeight;
    }

    public boolean canMergeBlur(UiBatchType type, GpuTextureView view, GpuSampler sampler,
                                Renderer2D.BlurQuality quality, float offsetPx,
                                UiBackdropRequest backdropRequest,
                                UiScissorSnapshot scissorSnapshot, UiClipSnapshot clipSnapshot) {
        Renderer2D.BlurQuality normalizedQuality = quality != null ? quality : Renderer2D.DEFAULT_BLUR_QUALITY;
        float normalizedOffset = Float.isFinite(offsetPx) ? Math.max(0.0f, offsetPx) : Renderer2D.DEFAULT_KAWASE_OFFSET_PX;
        return canMerge(type, view, sampler, scissorSnapshot, clipSnapshot)
                && this.blurQuality == normalizedQuality
                && Float.compare(this.blurOffsetPx, normalizedOffset) == 0
                && Objects.equals(this.backdropRequest,
                backdropRequest != null ? backdropRequest : UiBackdropRequest.NONE);
    }

    private static boolean sameState(UiScissorSnapshot a, UiScissorSnapshot b) {
        return a != null && b != null && a.id() == b.id();
    }

    private static boolean sameState(UiClipSnapshot a, UiClipSnapshot b) {
        return a != null && b != null && a.id() == b.id();
    }
}
